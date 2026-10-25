package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceEntry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.DiagnosticReport;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.RecoveryDecision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.RecoveryService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.RecoverySlot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/// 第一版本地 JSON 文件恢复服务（主设计 §8.4）。
///
/// ## 恢复根与正式资源隔离
///
/// 构造时同时接收**恢复根**与**正式资源根**两个 [ResourcePathResolver]。本类只解析恢复根下的
/// 路径，正式资源根仅用于构造期检查：两个根相同、互为祖先时直接拒绝，避免恢复槽文件被正式
/// 资源枚举当成动画文件，也避免恢复写入误落到正式资源目录。正式资源根不参与任何读写。
///
/// ## 槽文件格式
///
/// 每个恢复槽是恢复根下 `slots/` 目录中的自包含 JSON envelope：
///
/// ```json
/// {
///   "slot_version": 1,
///   "resource": "animations/example.json",
///   "revision": 12,
///   "captured_at_epoch_millis": 1700000000000,
///   "size_bytes": 1024,
///   "sha256": "<64 位小写十六进制>",
///   "document": { "...": "CameraAnimation 的结构 JSON" }
/// }
/// ```
///
/// - `resource` 是**目标正式资源**身份，只作为元数据保存，不用于解析正式路径；
/// - `revision` 是捕获快照的发布序号；
/// - `document` 是 [CameraAnimation#codec(DocumentRegistries)] 的紧凑结构 JSON；
/// - `size_bytes` / `sha256` 覆盖 `document` 的 UTF-8 字节（不含 envelope 其余字段），
///   读取时重新序列化并校验，因此槽内容被截断或改写时不会被当成合法槽；
/// - 槽**不保存**渲染缓存、撤销栈、会话草稿或宿主状态。
///
/// 槽文件名由内容确定：`slot-<19 位捕获毫秒>-<16 位指纹>.json`，指纹为
/// 目标身份、revision、捕获时间、大小与哈希的 SHA-256 前缀。给定 [RecoverySlot] 即可重新
/// 计算文件身份，无需维护内存映射；文件名与 envelope 不一致的槽不列出、不可恢复。
///
/// ## 捕获与身份
///
/// [capture] 在调用线程只读取捕获时间，编码、哈希和写入都在 [CompletableFuture] 的公共
/// 线程池中执行，不阻塞编辑线程。写入先落到恢复根内的临时文件，再以 `ATOMIC_MOVE` 替换槽
/// 文件；不支持原子替换时返回 [SaveResult.Rejected]。完成回调恰好调用一次。
///
/// [SaveResult.Saved] 的 [AnimationResourceEntry#resource()] 是**恢复槽身份**
/// （`slots/...`），不是目标正式资源身份，不会把自动恢复版本误报成用户正式保存。
/// [restore] 返回的 [LoadResult.Loaded] 同样以槽身份作为 entry，并以捕获时间作为
/// `modifiedAtEpochMillis`。
///
/// ## 列表、恢复与错误
///
/// [slots] 只列出文件名规范、envelope 合法、`document` 结构可解码且大小 / 哈希一致的槽，
/// 按捕获时间从新到旧排列；同一时间的槽再按目标身份、revision 与哈希稳定排序。
/// [restore] 只执行结构解码，**不执行统一文档校验、不发布文档**，调用方仍须走正常校验与
/// 整份换档流程。[discard] 删除槽及其决定元数据，不触碰正式资源。
///
/// 所有失败都通过 [LoadResult.Rejected] / [SaveResult.Rejected] 返回，不向调用方抛出 IO、
/// JSON 或 Codec 异常。报告只含单条资源身份一致的 [DiagnosticScope.File] 诊断；恢复操作的
/// 报告资源是恢复槽身份，不冒充正式资源。
///
/// ## 用户决定与持久性
///
/// [decide] 只把决定写入槽旁的 `<槽文件名>.decision` JSON，并持久化到磁盘，服务重启后仍可
/// 读取。它**不执行**决定本身：`KEEP` 保留槽位；`RESTORE` 与 `DISCARD` 同样只记录，调用方
/// 仍须显式调用 [restore] / [discard]。这正是 [RecoveryDecision] 的契约，避免把 `KEEP`
/// 静默解释成恢复或删除。决定元数据写入失败时为尽力而为，不抛异常，也不删除槽。
///
/// ## 数量上限
///
/// 第一版固定最多保留 [#MAX_SLOTS] 个槽。每次成功写入后按文件名中的捕获时间删除最旧的槽
/// （同时删除其 `.decision` 元数据），使目录中规范槽文件不超过上限。非法 / 不可读的规范
/// 槽文件也占用名额，但不会出现在 [slots] 中。第一版**没有**按体积或保留期淘汰；也没有
/// 防抖 / 周期调度、NBT、网络、压缩 Ops 或自动发布。
///
/// ## 线程
///
/// 服务可并发使用。槽目录的写入、裁剪、枚举、恢复、删除与决定记录由内部锁串行化；回调在锁
/// 外调用。捕获时间在调用线程读取，因此同一毫秒内的多次捕获可能得到相同时间，此时顺序由
/// 稳定排序的其余键决定。
@NullMarked
public final class FileRecoveryService implements RecoveryService {

    /// 槽目录相对恢复根的名称。
    static final String SLOT_DIRECTORY = "slots";

    /// 槽格式版本；第一版只接受该值。
    static final int SLOT_FORMAT_VERSION = 1;

    /// 第一版最多保留的恢复槽数量（主设计 §8.4 的“限制数量”）。
    static final int MAX_SLOTS = 16;

    /// 恢复槽路径被 [ResourcePathResolver] 拒绝。
    static final DiagnosticCode RECOVERY_PATH_REJECTED =
            new DiagnosticCode("free_camera_api_tripod:recovery_path_rejected");

    /// 文档无法编码为槽内容。
    static final DiagnosticCode RECOVERY_ENCODE_FAILED =
            new DiagnosticCode("free_camera_api_tripod:recovery_encode_failed");

    /// 槽文件无法写入（目录创建、临时文件、原子替换等）。
    static final DiagnosticCode RECOVERY_WRITE_FAILED =
            new DiagnosticCode("free_camera_api_tripod:recovery_write_failed");

    /// 请求恢复的槽文件不存在。
    static final DiagnosticCode RECOVERY_SLOT_NOT_FOUND =
            new DiagnosticCode("free_camera_api_tripod:recovery_slot_not_found");

    /// 槽文件无法读取（IO / 权限 / 不是普通文件）。
    static final DiagnosticCode RECOVERY_READ_FAILED =
            new DiagnosticCode("free_camera_api_tripod:recovery_read_failed");

    /// 槽文件不是合法 JSON，或 envelope 字段缺失 / 类型错误 / 版本不支持。
    static final DiagnosticCode RECOVERY_JSON_INVALID =
            new DiagnosticCode("free_camera_api_tripod:recovery_json_invalid");

    /// envelope 合法，但 `document` 不能按 [CameraAnimation] 结构解码。
    static final DiagnosticCode RECOVERY_STRUCTURE_INVALID =
            new DiagnosticCode("free_camera_api_tripod:recovery_structure_invalid");

    /// 槽内容与记录的大小 / 哈希或文件名身份不一致。
    static final DiagnosticCode RECOVERY_CONTENT_MISMATCH =
            new DiagnosticCode("free_camera_api_tripod:recovery_content_mismatch");

    private static final String SLOT_FILE_PREFIX = "slot-";
    private static final String SLOT_FILE_SUFFIX = ".json";
    private static final String TEMP_PREFIX = ".recovery-";
    private static final String DECISION_TEMP_PREFIX = ".decision-";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String DECISION_SUFFIX = ".decision";
    private static final Pattern SLOT_FILE_NAME = Pattern.compile("slot-\\d{19}-[0-9a-f]{16}\\.json");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private static final Comparator<RecoverySlot> SLOT_ORDER =
            Comparator.comparingLong(RecoverySlot::capturedAtEpochMillis).reversed()
                    .thenComparing(slot -> slot.resource().value())
                    .thenComparingLong(slot -> slot.snapshot().revision().value())
                    .thenComparing(RecoverySlot::contentHash);

    private final ResourcePathResolver recoveryRoot;
    private final LongSupplier clock;
    private final Codec<CameraAnimation> structureCodec;
    private final Object lock = new Object();

    /// 以恢复根、正式资源根与冻结的文档注册表创建服务。
    ///
    /// @param recoveryRoot 恢复根；所有槽文件只写入该根
    /// @param resourceRoot 正式资源根；只用于拒绝与恢复根重叠的配置，不参与读写
    /// @param registries   文档结构编解码所需的类型注册表，调用方应已冻结
    /// @throws IllegalArgumentException 两个根相同或互为祖先
    public FileRecoveryService(ResourcePathResolver recoveryRoot,
                               ResourcePathResolver resourceRoot,
                               DocumentRegistries registries) {
        this(recoveryRoot, resourceRoot, registries, System::currentTimeMillis);
    }

    /// 可注入时钟的构造，供同包测试固定捕获时间；正式代码使用系统毫秒时钟。
    FileRecoveryService(ResourcePathResolver recoveryRoot,
                        ResourcePathResolver resourceRoot,
                        DocumentRegistries registries,
                        LongSupplier clock) {
        this.recoveryRoot = Objects.requireNonNull(recoveryRoot, "recoveryRoot");
        Objects.requireNonNull(resourceRoot, "resourceRoot");
        this.structureCodec = CameraAnimation.codec(Objects.requireNonNull(registries, "registries"));
        this.clock = Objects.requireNonNull(clock, "clock");

        Path recovery = recoveryRoot.root();
        Path formal = resourceRoot.root();
        if (recovery.equals(formal) || recovery.startsWith(formal) || formal.startsWith(recovery)) {
            throw new IllegalArgumentException(
                    "Recovery root must not overlap the formal resource root: "
                            + recovery + " / " + formal);
        }
    }

    @Override
    public List<RecoverySlot> slots() {
        synchronized (lock) {
            Path directory = slotDirectory();
            if (!Files.isDirectory(directory)) {
                return List.of();
            }

            List<RecoverySlot> found = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path file : stream) {
                    if (!isSlotFileName(file) || !Files.isRegularFile(file)) {
                        continue;
                    }

                    try {
                        ResourceId fileId = new ResourceId(
                                relativeId(recoveryRoot.root(), file));
                        found.add(readSlot(file, fileId));
                    } catch (SlotReadException | RuntimeException e) {
                        // 非法槽不列出；它仍占用数量上限并在裁剪时按文件名淘汰。
                    }
                }
            } catch (IOException | RuntimeException e) {
                return List.of();
            }

            found.sort(SLOT_ORDER);
            return List.copyOf(found);
        }
    }

    @Override
    public void capture(AnimationSnapshot snapshot, ResourceId resource,
                        Consumer<SaveResult> completion) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(completion, "completion");

        long capturedAt = Math.max(0L, clock.getAsLong());

        CompletableFuture.runAsync(() -> {
            SaveResult result;

            try {
                result = writeCapture(snapshot, resource, capturedAt);
            } catch (RuntimeException e) {
                // writeCapture 已把可预期失败转成 Rejected；这里兜底保证回调仍有结果。
                result = rejectedSave(uncommittedSlotId(capturedAt), RECOVERY_WRITE_FAILED,
                        "Could not capture recovery slot: " + describe(e));
            }

            completion.accept(result);
        });
    }

    @Override
    public LoadResult restore(RecoverySlot slot) {
        Objects.requireNonNull(slot, "slot");

        ResourceId slotId = slotResourceId(slot.resource(), slot.snapshot().revision(),
                slot.capturedAtEpochMillis(), slot.sizeBytes(), slot.contentHash());

        Path path;
        try {
            path = recoveryRoot.resolveForRead(slotId);
        } catch (ResourcePathException e) {
            return rejectedLoad(slotId, RECOVERY_PATH_REJECTED,
                    "Recovery slot path rejected: " + e.reason());
        }

        synchronized (lock) {
            try {
                if (!Files.isRegularFile(path)) {
                    return rejectedLoad(slotId, RECOVERY_SLOT_NOT_FOUND,
                            "Recovery slot '" + slotId.value() + "' does not exist");
                }

                RecoverySlot stored = readSlot(path, slotId);
                if (!stored.equals(slot)) {
                    return rejectedLoad(slotId, RECOVERY_CONTENT_MISMATCH,
                            "Recovery slot '" + slotId.value()
                                    + "' does not match the requested slot metadata");
                }

                AnimationResourceEntry entry = new AnimationResourceEntry(
                        slotId, stored.sizeBytes(), stored.contentHash(),
                        stored.capturedAtEpochMillis());
                return new LoadResult.Loaded(stored.snapshot().document(), entry);
            } catch (SlotReadException e) {
                return rejectedLoad(slotId, e.code, e.getMessage());
            } catch (RuntimeException e) {
                return rejectedLoad(slotId, RECOVERY_READ_FAILED,
                        "Could not read recovery slot '" + slotId.value() + "': " + describe(e));
            }
        }
    }

    @Override
    public void discard(RecoverySlot slot) {
        Objects.requireNonNull(slot, "slot");

        ResourceId slotId = slotResourceId(slot.resource(), slot.snapshot().revision(),
                slot.capturedAtEpochMillis(), slot.sizeBytes(), slot.contentHash());

        Path path;
        try {
            path = recoveryRoot.resolveForWrite(slotId);
        } catch (ResourcePathException e) {
            return;
        }

        synchronized (lock) {
            deleteSlotFiles(path);
        }
    }

    @Override
    public void decide(RecoverySlot slot, RecoveryDecision decision) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(decision, "decision");

        ResourceId slotId = slotResourceId(slot.resource(), slot.snapshot().revision(),
                slot.capturedAtEpochMillis(), slot.sizeBytes(), slot.contentHash());

        Path path;
        try {
            path = recoveryRoot.resolveForRead(slotId);
        } catch (ResourcePathException e) {
            return;
        }

        synchronized (lock) {
            try {
                if (!Files.isRegularFile(path)) {
                    return;
                }

                writeDecision(path, decision);
            } catch (IOException | RuntimeException e) {
                // void 契约没有诊断通道：记录决定是尽力而为，失败不删除槽、不抛异常。
            }
        }
    }

    /// 读取已持久化的用户决定；仅用于同包测试与宿主诊断，不属于公开契约。
    ///
    /// 没有决定文件、文件不可读或内容非法时返回空。
    ///
    /// @param slot 恢复槽
    /// @return 已持久化的决定
    Optional<RecoveryDecision> recordedDecision(RecoverySlot slot) {
        Objects.requireNonNull(slot, "slot");

        ResourceId slotId = slotResourceId(slot.resource(), slot.snapshot().revision(),
                slot.capturedAtEpochMillis(), slot.sizeBytes(), slot.contentHash());

        Path slotPath;
        try {
            slotPath = recoveryRoot.resolveForRead(slotId);
        } catch (ResourcePathException e) {
            return Optional.empty();
        }

        Path decisionPath = decisionPath(slotPath);
        byte[] bytes;

        try {
            if (!Files.isRegularFile(decisionPath)) {
                return Optional.empty();
            }
            bytes = Files.readAllBytes(decisionPath);
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }

        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }

            JsonElement value = parsed.getAsJsonObject().get("decision");
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }

            return Optional.of(RecoveryDecision.valueOf(value.getAsString()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /// 计算槽身份：目标身份、revision、捕获时间、大小与内容哈希的确定性函数。
    ///
    /// 文件名使用 19 位零填充的捕获毫秒，保证按文件名排序等价于按捕获时间排序。
    ///
    /// @param target                目标正式资源身份
    /// @param revision              捕获快照的发布序号
    /// @param capturedAtEpochMillis 捕获时间，Unix epoch 毫秒，非负
    /// @param sizeBytes             `document` JSON 的 UTF-8 字节数，非负
    /// @param contentHash           `document` JSON 的 SHA-256 小写十六进制
    /// @return 恢复槽资源身份
    static ResourceId slotResourceId(ResourceId target, Revision revision,
                                     long capturedAtEpochMillis, long sizeBytes,
                                     String contentHash) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(contentHash, "contentHash");

        String identity = SLOT_FORMAT_VERSION + "\n"
                + target.value() + "\n"
                + revision.value() + "\n"
                + capturedAtEpochMillis + "\n"
                + sizeBytes + "\n"
                + contentHash;
        String fingerprint = sha256Hex(identity.getBytes(StandardCharsets.UTF_8))
                .substring(0, 16);
        String name = SLOT_FILE_PREFIX
                + String.format(Locale.ROOT, "%019d", capturedAtEpochMillis)
                + "-" + fingerprint + SLOT_FILE_SUFFIX;

        return new ResourceId(SLOT_DIRECTORY + "/" + name);
    }

    /// 编码并原子写入一个槽，然后裁剪数量。失败全部转成 [SaveResult.Rejected]。
    private SaveResult writeCapture(AnimationSnapshot snapshot, ResourceId target,
                                    long capturedAt) {
        ResourceId uncommitted = uncommittedSlotId(capturedAt);
        JsonElement encoded;

        try {
            DataResult<JsonElement> result = structureCodec.encodeStart(
                    JsonOps.INSTANCE, snapshot.document());

            if (result.error().isPresent()) {
                return rejectedSave(uncommitted, RECOVERY_ENCODE_FAILED,
                        "Document could not be encoded for recovery: " + errorMessage(result));
            }

            encoded = result.result().orElse(null);
        } catch (RuntimeException e) {
            return rejectedSave(uncommitted, RECOVERY_ENCODE_FAILED,
                    "Document could not be encoded for recovery: " + describe(e));
        }

        if (encoded == null) {
            return rejectedSave(uncommitted, RECOVERY_ENCODE_FAILED,
                    "Document could not be encoded for recovery: codec returned no value");
        }

        byte[] documentBytes = encoded.toString().getBytes(StandardCharsets.UTF_8);
        String documentHash;
        try {
            documentHash = sha256Hex(documentBytes);
        } catch (RuntimeException e) {
            return rejectedSave(uncommitted, RECOVERY_WRITE_FAILED,
                    "Could not hash recovery content: " + describe(e));
        }

        ResourceId slotId = slotResourceId(target, snapshot.revision(), capturedAt,
                documentBytes.length, documentHash);

        Path path;
        try {
            path = recoveryRoot.resolveForWrite(slotId);
        } catch (ResourcePathException e) {
            return rejectedSave(slotId, RECOVERY_PATH_REJECTED,
                    "Recovery slot path rejected: " + e.reason());
        }

        JsonObject envelope = new JsonObject();
        envelope.addProperty("slot_version", SLOT_FORMAT_VERSION);
        envelope.addProperty("resource", target.value());
        envelope.addProperty("revision", snapshot.revision().value());
        envelope.addProperty("captured_at_epoch_millis", capturedAt);
        envelope.addProperty("size_bytes", documentBytes.length);
        envelope.addProperty("sha256", documentHash);
        envelope.add("document", encoded);
        byte[] envelopeBytes = envelope.toString().getBytes(StandardCharsets.UTF_8);

        Path parent = path.getParent();
        if (parent == null) {
            return rejectedSave(slotId, RECOVERY_WRITE_FAILED,
                    "Recovery slot path has no parent directory: '" + slotId.value() + "'");
        }

        synchronized (lock) {
            Path temp = null;

            try {
                Files.createDirectories(parent);
                temp = Files.createTempFile(parent, TEMP_PREFIX, TEMP_SUFFIX);
                Files.write(temp, envelopeBytes);
                Files.move(temp, path,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                temp = null;

                trimSlots();

                return new SaveResult.Saved(new AnimationResourceEntry(
                        slotId, documentBytes.length, documentHash, capturedAt));
            } catch (AtomicMoveNotSupportedException e) {
                return rejectedSave(slotId, RECOVERY_WRITE_FAILED,
                        "Atomic replacement is not supported for recovery slot '"
                                + slotId.value() + "'");
            } catch (IOException | RuntimeException e) {
                return rejectedSave(slotId, RECOVERY_WRITE_FAILED,
                        "Could not write recovery slot '" + slotId.value() + "': " + describe(e));
            } finally {
                deleteTemporaryFile(temp);
            }
        }
    }

    /// 读取并校验一个槽；文件名必须与 envelope 内容计算出的身份一致。
    ///
    /// @param file       槽文件
    /// @param expectedId 期望的槽身份
    /// @return 合法槽
    /// @throws SlotReadException 读取、JSON、结构或内容校验失败
    private RecoverySlot readSlot(Path file, ResourceId expectedId) throws SlotReadException {
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            throw new SlotReadException(RECOVERY_SLOT_NOT_FOUND,
                    "Recovery slot '" + expectedId.value() + "' does not exist");
        } catch (IOException | SecurityException e) {
            throw new SlotReadException(RECOVERY_READ_FAILED,
                    "Could not read recovery slot '" + expectedId.value() + "': " + describe(e));
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(new String(fileBytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + expectedId.value() + "' is not valid JSON: "
                            + describe(e));
        }

        if (!parsed.isJsonObject()) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + expectedId.value() + "' must be a JSON object");
        }

        JsonObject envelope = parsed.getAsJsonObject();
        long version = readLong(envelope, "slot_version", expectedId);
        if (version != SLOT_FORMAT_VERSION) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + expectedId.value() + "' has unsupported slot_version: "
                            + version);
        }

        ResourceId target;
        try {
            target = new ResourceId(readString(envelope, "resource", expectedId));
        } catch (IllegalArgumentException e) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + expectedId.value() + "' has invalid resource: "
                            + describe(e));
        }

        long revisionValue = readLong(envelope, "revision", expectedId);
        long capturedAt = readLong(envelope, "captured_at_epoch_millis", expectedId);
        long sizeBytes = readLong(envelope, "size_bytes", expectedId);
        String contentHash = readString(envelope, "sha256", expectedId);

        if (revisionValue < 0 || capturedAt < 0 || sizeBytes < 0) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + expectedId.value()
                            + "' has negative revision, captured time or size");
        }

        if (!SHA256_HEX.matcher(contentHash).matches()) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + expectedId.value() + "' has an invalid sha256 field");
        }

        JsonElement documentJson = envelope.get("document");
        if (documentJson == null) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + expectedId.value() + "' is missing the document field");
        }

        if (!documentJson.isJsonObject()) {
            throw new SlotReadException(RECOVERY_STRUCTURE_INVALID,
                    "Recovery slot '" + expectedId.value() + "' document must be a JSON object");
        }

        byte[] documentBytes;
        try {
            documentBytes = documentJson.toString().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new SlotReadException(RECOVERY_STRUCTURE_INVALID,
                    "Recovery slot '" + expectedId.value()
                            + "' document could not be serialized: " + describe(e));
        }

        if (documentBytes.length != sizeBytes
                || !contentHash.equals(sha256Hex(documentBytes))) {
            throw new SlotReadException(RECOVERY_CONTENT_MISMATCH,
                    "Recovery slot '" + expectedId.value()
                            + "' content size or hash does not match its metadata");
        }

        CameraAnimation document;
        try {
            DataResult<CameraAnimation> decoded = structureCodec.parse(
                    JsonOps.INSTANCE, documentJson);

            if (decoded.error().isPresent()) {
                throw new SlotReadException(RECOVERY_STRUCTURE_INVALID,
                        "Recovery slot '" + expectedId.value()
                                + "' has invalid document structure: " + errorMessage(decoded));
            }

            document = decoded.result().orElse(null);
        } catch (SlotReadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SlotReadException(RECOVERY_STRUCTURE_INVALID,
                    "Recovery slot '" + expectedId.value()
                            + "' has invalid document structure: " + describe(e));
        }

        if (document == null) {
            throw new SlotReadException(RECOVERY_STRUCTURE_INVALID,
                    "Recovery slot '" + expectedId.value()
                            + "' has invalid document structure: codec returned no value");
        }

        Revision revision = new Revision(revisionValue);
        ResourceId canonicalId = slotResourceId(target, revision, capturedAt,
                sizeBytes, contentHash);
        if (!canonicalId.equals(expectedId)) {
            throw new SlotReadException(RECOVERY_CONTENT_MISMATCH,
                    "Recovery slot file identity does not match its contents: expected '"
                            + canonicalId.value() + "', got '" + expectedId.value() + "'");
        }

        return new RecoverySlot(target, new AnimationSnapshot(revision, document),
                capturedAt, sizeBytes, contentHash);
    }

    /// 按文件名中的捕获时间淘汰最旧的槽，使规范槽文件不超过 [#MAX_SLOTS]。
    ///
    /// 裁剪失败不回滚已经成功的捕获，也不改变返回结果；它只影响数量上限。
    private void trimSlots() {
        Path directory = slotDirectory();
        if (!Files.isDirectory(directory)) {
            return;
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (isSlotFileName(file) && Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        } catch (IOException | RuntimeException e) {
            return;
        }

        files.sort(Comparator.comparing(file -> file.getFileName().toString()));
        int excess = files.size() - MAX_SLOTS;

        for (int index = 0; index < excess; index++) {
            deleteSlotFiles(files.get(index));
        }
    }

    /// 原子写入槽旁决定元数据。
    private void writeDecision(Path slotPath, RecoveryDecision decision) throws IOException {
        Path path = decisionPath(slotPath);
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }

        JsonObject metadata = new JsonObject();
        metadata.addProperty("decision", decision.name());
        metadata.addProperty("decided_at_epoch_millis",
                Math.max(0L, clock.getAsLong()));
        byte[] bytes = metadata.toString().getBytes(StandardCharsets.UTF_8);

        Path temp = null;
        try {
            Files.createDirectories(parent);
            temp = Files.createTempFile(parent, DECISION_TEMP_PREFIX, TEMP_SUFFIX);
            Files.write(temp, bytes);
            Files.move(temp, path,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temp = null;
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("Atomic replacement is not supported for decision metadata", e);
        } finally {
            deleteTemporaryFile(temp);
        }
    }

    /// 删除槽及其决定元数据；正式资源不在恢复根下，因此不会被触及。
    private static void deleteSlotFiles(Path slotPath) {
        try {
            Files.deleteIfExists(slotPath);
        } catch (IOException | SecurityException ignored) {
            // 删除是尽力而为：void 契约没有诊断通道，下一次捕获仍会按上限继续裁剪。
        }

        try {
            Files.deleteIfExists(decisionPath(slotPath));
        } catch (IOException | SecurityException ignored) {
            // 决定元数据残留不影响槽本身；槽已不存在时 [recordedDecision] 也读不到。
        }
    }

    private static void deleteTemporaryFile(Path temp) {
        if (temp == null) {
            return;
        }

        try {
            Files.deleteIfExists(temp);
        } catch (IOException | SecurityException ignored) {
            // 清理是尽力而为；原始失败原因已由结果报告。
        }
    }

    private Path slotDirectory() {
        return recoveryRoot.root().resolve(SLOT_DIRECTORY);
    }

    private static Path decisionPath(Path slotPath) {
        return slotPath.resolveSibling(slotPath.getFileName() + DECISION_SUFFIX);
    }

    private static boolean isSlotFileName(Path file) {
        return SLOT_FILE_NAME.matcher(file.getFileName().toString()).matches();
    }

    /// 尚未落盘时的稳定报告身份；不是合法槽文件名，扫描不会列出。
    private static ResourceId uncommittedSlotId(long capturedAt) {
        return new ResourceId(SLOT_DIRECTORY + "/slot-"
                + String.format(Locale.ROOT, "%019d", capturedAt) + "-uncommitted");
    }

    /// 把恢复根下的文件路径转换为以 `/` 分隔的资源身份。
    private static String relativeId(Path root, Path file) {
        Path relative = root.relativize(file);
        StringBuilder value = new StringBuilder();

        for (Path segment : relative) {
            if (value.length() > 0) {
                value.append('/');
            }
            value.append(segment);
        }

        return value.toString();
    }

    private static long readLong(JsonObject object, String field, ResourceId slotId)
            throws SlotReadException {
        JsonElement element = object.get(field);

        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + slotId.value() + "' field '" + field
                            + "' must be a number");
        }

        try {
            return Long.parseLong(element.getAsString());
        } catch (NumberFormatException e) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + slotId.value() + "' field '" + field
                            + "' must be a whole number");
        }
    }

    private static String readString(JsonObject object, String field, ResourceId slotId)
            throws SlotReadException {
        JsonElement element = object.get(field);

        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new SlotReadException(RECOVERY_JSON_INVALID,
                    "Recovery slot '" + slotId.value() + "' field '" + field
                            + "' must be a string");
        }

        return element.getAsString();
    }

    private static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static LoadResult rejectedLoad(ResourceId resource, DiagnosticCode code,
                                           String message) {
        return new LoadResult.Rejected(report(resource, code, message));
    }

    private static SaveResult rejectedSave(ResourceId resource, DiagnosticCode code,
                                           String message) {
        return new SaveResult.Rejected(report(resource, code, message));
    }

    /// 构造只含单条 File 作用域诊断的单资源报告。
    private static DiagnosticReport report(ResourceId resource, DiagnosticCode code,
                                           String message) {
        Diagnostic diagnostic = new Diagnostic(
                code,
                Severity.ERROR,
                LocalizedMessage.literal(message),
                new DiagnosticScope.File(resource),
                null,
                null);

        return new DiagnosticReport(resource, List.of(diagnostic));
    }

    private static String errorMessage(DataResult<?> result) {
        return result.error()
                .map(DataResult.Error::message)
                .orElse("document codec returned an error");
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    /// 槽读取失败：携带稳定诊断码与消息，公开入口统一转成 Rejected。
    private static final class SlotReadException extends Exception {

        private final DiagnosticCode code;

        private SlotReadException(DiagnosticCode code, String message) {
            super(message, null, false, false);
            this.code = code;
        }
    }
}
