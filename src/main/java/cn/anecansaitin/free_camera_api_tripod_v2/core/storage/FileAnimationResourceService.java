package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.StructureFieldPath;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceEntry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.DiagnosticReport;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceListing;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/// 第一版本地 JSON 文件资源服务（只实现本地资源根，不含 NBT / 网络 / 恢复槽）。
///
/// ## 读写边界
///
/// - 资源身份先交给 [ResourcePathResolver] 解析；越界、绝对路径、`..`、反斜杠等由解析器
///   拒绝，本类不重新实现路径安全。
/// - 载入只做 [CameraAnimation#codec(DocumentRegistries)] 的**结构解码**，不调用统一文档
///   校验。返回的 [LoadResult.Loaded] 仍表示“尚未发布”，调用方必须继续执行完整校验。
/// - 保存先编码，再写入目标同目录的临时文件，最后以 `ATOMIC_MOVE` 替换正式文件；失败时
///   清理临时文件，不留下部分正式文件。
///
/// ## 诊断
///
/// 所有载入 / 保存失败都返回 [LoadResult.Rejected] / [SaveResult.Rejected]，不把 IO、JSON
/// 或 Codec 异常抛给调用方。报告只含同一资源的 [DiagnosticScope.File] 诊断。
///
/// ## 元数据
///
/// 内容哈希为 SHA-256 的小写十六进制文本；[AnimationResourceEntry#contentHash()] 保证非空白。
/// [entries()] 只列出资源根下扩展名为 `.json` 的普通文件，跳过符号链接、目录和无法读取的项，
/// 并按 [ResourceId#value()] 排序。
///
/// ## 线程
///
/// 本类无可变状态，可并发使用。[saveAsync] 使用 [CompletableFuture] 的公共线程池执行保存，
/// 不依赖任何客户端线程类；完成回调只调用一次。
///
/// ## 已知边界
///
/// 本实现不消除“路径校验后文件系统被替换”的 TOCTOU 窗口；单文件大小与 JSON 深度由
/// [JsonResourceLimits] 在读取 / 解析前限制，目录资源数量尚未限制；不支持 NBT、网络 Ops、
/// 压缩 Ops、恢复槽或备份。资源根目录下的临时文件使用 `.tmp` 后缀，因而不参与
/// [entries()] 的 `.json` 枚举。
@NullMarked
public final class FileAnimationResourceService implements AnimationResourceService {

    /// 路径被 [ResourcePathResolver] 拒绝。
    static final DiagnosticCode RESOURCE_PATH_REJECTED =
            new DiagnosticCode("free_camera_api_tripod:resource_path_rejected");

    /// 资源无法读取（不存在、不是普通文件或 IO 失败）。
    static final DiagnosticCode RESOURCE_READ_FAILED =
            new DiagnosticCode("free_camera_api_tripod:resource_read_failed");

    /// 资源内容不是合法 JSON。
    static final DiagnosticCode RESOURCE_JSON_INVALID =
            new DiagnosticCode("free_camera_api_tripod:resource_json_invalid");

    /// JSON 合法，但无法按文档结构解码。
    static final DiagnosticCode RESOURCE_STRUCTURE_INVALID =
            new DiagnosticCode("free_camera_api_tripod:resource_structure_invalid");

    /// 文档无法编码。
    static final DiagnosticCode RESOURCE_ENCODE_FAILED =
            new DiagnosticCode("free_camera_api_tripod:resource_encode_failed");

    /// 资源无法写入（目录创建、临时文件、原子替换等）。
    static final DiagnosticCode RESOURCE_WRITE_FAILED =
            new DiagnosticCode("free_camera_api_tripod:resource_write_failed");

    /// 资源文件或编码结果超过配置上限。
    static final DiagnosticCode RESOURCE_TOO_LARGE =
            new DiagnosticCode("free_camera_api_tripod:resource_too_large");

    /// JSON 容器嵌套超过配置上限。
    static final DiagnosticCode RESOURCE_TOO_DEEP =
            new DiagnosticCode("free_camera_api_tripod:resource_too_deep");

    private static final String JSON_SUFFIX = ".json";
    private static final String TEMP_PREFIX = ".resource-";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final int IO_BUFFER_SIZE = 8192;

    private final ResourcePathResolver resolver;
    private final Codec<CameraAnimation> structureCodec;
    private final JsonResourceLimits limits;

    /// 以资源根解析器与冻结的文档注册表创建服务。
    ///
    /// 构造时固定 [CameraAnimation#codec(DocumentRegistries)]；注册表应已由调用方冻结，
    /// 本类不负责注册或冻结。
    ///
    /// @param resolver   资源根解析器
    /// @param registries 文档结构编解码所需的类型注册表
    public FileAnimationResourceService(ResourcePathResolver resolver, DocumentRegistries registries) {
        this(resolver, registries, JsonResourceLimits.DEFAULT);
    }

    /// 以显式规模上限创建服务。
    ///
    /// @param resolver   资源根解析器
    /// @param registries 文档结构编解码所需的类型注册表
    /// @param limits     文件大小与 JSON 深度上限
    public FileAnimationResourceService(ResourcePathResolver resolver,
                                        DocumentRegistries registries,
                                        JsonResourceLimits limits) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.structureCodec = CameraAnimation.codec(Objects.requireNonNull(registries, "registries"));
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public LoadResult load(ResourceId resource) {
        Objects.requireNonNull(resource, "resource");

        Path path;
        try {
            path = resolver.resolveForRead(resource);
        } catch (ResourcePathException e) {
            return rejectedLoad(resource, RESOURCE_PATH_REJECTED,
                    "Resource path rejected: " + e.reason());
        }

        byte[] bytes;
        try {
            long size = Files.size(path);

            if (size > limits.maxFileBytes()) {
                return rejectedLoad(resource, RESOURCE_TOO_LARGE,
                        "Resource '" + resource.value() + "' is " + size
                                + " bytes, exceeds maxFileBytes " + limits.maxFileBytes());
            }

            bytes = Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            return rejectedLoad(resource, RESOURCE_READ_FAILED,
                    "Resource '" + resource.value() + "' does not exist");
        } catch (IOException | SecurityException e) {
            return rejectedLoad(resource, RESOURCE_READ_FAILED,
                    "Could not read resource '" + resource.value() + "': " + describe(e));
        }

        if (bytes.length > limits.maxFileBytes()) {
            return rejectedLoad(resource, RESOURCE_TOO_LARGE,
                    "Resource '" + resource.value() + "' is " + bytes.length
                            + " bytes, exceeds maxFileBytes " + limits.maxFileBytes());
        }

        String jsonText = new String(bytes, StandardCharsets.UTF_8);
        if (jsonText.isBlank()) {
            return rejectedLoad(resource, RESOURCE_JSON_INVALID,
                    "Resource '" + resource.value() + "' is empty");
        }

        if (JsonDepthGuard.exceeds(jsonText, limits.maxJsonDepth())) {
            return rejectedLoad(resource, RESOURCE_TOO_DEEP,
                    "Resource '" + resource.value() + "' exceeds maxJsonDepth "
                            + limits.maxJsonDepth());
        }

        JsonElement json;
        try {
            json = JsonParser.parseString(jsonText);
        } catch (RuntimeException e) {
            return rejectedLoad(resource, RESOURCE_JSON_INVALID,
                    "Resource '" + resource.value() + "' is not valid JSON: " + describe(e));
        }

        CameraAnimation document;
        try {
            DataResult<CameraAnimation> parsed = structureCodec.parse(JsonOps.INSTANCE, json);

            if (parsed.error().isPresent()) {
                String message = errorMessage(parsed);
                return rejectedLoad(resource, RESOURCE_STRUCTURE_INVALID,
                        "Resource '" + resource.value() + "' has invalid structure: " + message,
                        StructureFieldPath.locate(message, json));
            }

            document = parsed.result().orElse(null);
        } catch (RuntimeException e) {
            String message = describe(e);
            return rejectedLoad(resource, RESOURCE_STRUCTURE_INVALID,
                    "Resource '" + resource.value() + "' has invalid structure: " + message,
                    StructureFieldPath.locate(message, json));
        }

        if (document == null) {
            return rejectedLoad(resource, RESOURCE_STRUCTURE_INVALID,
                    "Resource '" + resource.value() + "' has invalid structure: "
                            + "document codec returned no value");
        }

        try {
            long modifiedAt = Math.max(0L, Files.getLastModifiedTime(path).toMillis());
            AnimationResourceEntry entry = new AnimationResourceEntry(
                    resource, bytes.length, sha256Hex(bytes), modifiedAt);
            return new LoadResult.Loaded(document, entry);
        } catch (IOException | RuntimeException e) {
            return rejectedLoad(resource, RESOURCE_READ_FAILED,
                    "Could not read resource metadata for '" + resource.value() + "': "
                            + describe(e));
        }
    }

    @Override
    public SaveResult save(ResourceId resource, CameraAnimation document) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(document, "document");

        Path target;
        try {
            target = resolver.resolveForWrite(resource);
        } catch (ResourcePathException e) {
            return rejectedSave(resource, RESOURCE_PATH_REJECTED,
                    "Resource path rejected: " + e.reason());
        }

        JsonElement encoded;
        try {
            DataResult<JsonElement> result = structureCodec.encodeStart(JsonOps.INSTANCE, document);

            if (result.error().isPresent()) {
                return rejectedSave(resource, RESOURCE_ENCODE_FAILED,
                        "Document could not be encoded: " + errorMessage(result));
            }

            encoded = result.result().orElse(null);
        } catch (RuntimeException e) {
            return rejectedSave(resource, RESOURCE_ENCODE_FAILED,
                    "Document could not be encoded: " + describe(e));
        }

        if (encoded == null) {
            return rejectedSave(resource, RESOURCE_ENCODE_FAILED,
                    "Document could not be encoded: codec returned no value");
        }

        byte[] bytes = encoded.toString().getBytes(StandardCharsets.UTF_8);

        if (bytes.length > limits.maxFileBytes()) {
            return rejectedSave(resource, RESOURCE_TOO_LARGE,
                    "Encoded resource is " + bytes.length
                            + " bytes, exceeds maxFileBytes " + limits.maxFileBytes());
        }

        String hash;
        try {
            hash = sha256Hex(bytes);
        } catch (RuntimeException e) {
            return rejectedSave(resource, RESOURCE_WRITE_FAILED,
                    "Could not hash encoded resource: " + describe(e));
        }

        Path parent = target.getParent();
        if (parent == null) {
            return rejectedSave(resource, RESOURCE_WRITE_FAILED,
                    "Resource path has no parent directory: '" + resource.value() + "'");
        }

        Path temp = null;
        try {
            Files.createDirectories(parent);
            temp = Files.createTempFile(parent, TEMP_PREFIX, TEMP_SUFFIX);
            Files.write(temp, bytes);

            long modifiedAt = Math.max(0L, Files.getLastModifiedTime(temp).toMillis());
            AnimationResourceEntry entry = new AnimationResourceEntry(
                    resource, bytes.length, hash, modifiedAt);

            Files.move(temp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temp = null;
            return new SaveResult.Saved(entry);
        } catch (AtomicMoveNotSupportedException e) {
            return rejectedSave(resource, RESOURCE_WRITE_FAILED,
                    "Atomic replacement is not supported for resource '" + resource.value() + "'");
        } catch (IOException | RuntimeException e) {
            return rejectedSave(resource, RESOURCE_WRITE_FAILED,
                    "Could not write resource '" + resource.value() + "': " + describe(e));
        } finally {
            deleteTemporaryFile(temp);
        }
    }

    @Override
    public void saveAsync(ResourceId resource, CameraAnimation document,
                          Consumer<SaveResult> completion) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(completion, "completion");

        CompletableFuture.runAsync(() -> {
            SaveResult result;

            try {
                result = save(resource, document);
            } catch (RuntimeException e) {
                // save 已把可预期失败转成 Rejected；这里再兜底一次，保证回调仍有结果。
                result = rejectedSave(resource, RESOURCE_WRITE_FAILED,
                        "Could not write resource '" + resource.value() + "': " + describe(e));
            }

            completion.accept(result);
        });
    }

    @Override
    public Optional<AnimationResourceEntry> entry(ResourceId resource) {
        Objects.requireNonNull(resource, "resource");

        Path path;
        try {
            path = resolver.resolveForRead(resource);
        } catch (ResourcePathException e) {
            return Optional.empty();
        }

        return readEntry(resource, path);
    }

    @Override
    public List<AnimationResourceEntry> entries() {
        return collectEntries(Integer.MAX_VALUE).entries();
    }

    @Override
    public ResourceListing list(int maxEntries) {
        if (maxEntries < 0) {
            throw new IllegalArgumentException("maxEntries must be non-negative: " + maxEntries);
        }

        return collectEntries(maxEntries);
    }

    private ResourceListing collectEntries(int maxEntries) {
        Path root = resolver.root();

        try {
            if (!Files.isDirectory(root)) {
                return new ResourceListing(List.of(), false);
            }

            // 使用真实根遍历，避免资源根本身是符号链接时 walkFileTree 不进入目录。
            Path realRoot = root.toRealPath();
            List<AnimationResourceEntry> found = new ArrayList<>();
            PriorityQueue<AnimationResourceEntry> bounded = maxEntries == Integer.MAX_VALUE
                    ? null
                    : new PriorityQueue<>(Math.max(1, maxEntries),
                            Comparator.comparing((AnimationResourceEntry entry) ->
                                    entry.resource().value()).reversed());
            int[] seen = {0};

            Files.walkFileTree(realRoot, new SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(
                        Path file, BasicFileAttributes attributes) {
                    if (!attributes.isRegularFile()
                            || !file.getFileName().toString().endsWith(JSON_SUFFIX)) {
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    ResourceId id;
                    try {
                        id = new ResourceId(relativeId(realRoot, file));
                    } catch (RuntimeException e) {
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    // 枚举时再走一次解析器：不把越界项或不可验证项暴露给调用方。
                    try {
                        resolver.resolveForRead(id);
                    } catch (ResourcePathException e) {
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    Optional<AnimationResourceEntry> entry = readEntry(id, file);

                    if (entry.isPresent()) {
                        seen[0]++;

                        if (bounded == null) {
                            found.add(entry.get());
                        } else {
                            bounded.add(entry.get());

                            if (bounded.size() > maxEntries) {
                                bounded.poll();
                            }
                        }
                    }

                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException error) {
                    // 单个无法解析的目录项不应让整个枚举失败。
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });

            List<AnimationResourceEntry> result = bounded == null
                    ? found
                    : new ArrayList<>(bounded);
            result.sort(Comparator.comparing(entry -> entry.resource().value()));
            return new ResourceListing(result, seen[0] > maxEntries);
        } catch (IOException | RuntimeException e) {
            return new ResourceListing(List.of(), false);
        }
    }

    /// 读取单个资源的元数据；不存在、不是普通文件或读取失败时返回空。
    private static Optional<AnimationResourceEntry> readEntry(ResourceId resource, Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }

            long size = Files.size(path);
            long modifiedAt = Math.max(0L, Files.getLastModifiedTime(path).toMillis());
            return Optional.of(new AnimationResourceEntry(
                    resource, size, sha256Hex(path), modifiedAt));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /// 把真实根下的文件路径转换为以 `/` 分隔的资源身份。
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

    /// 读取文件内容并计算 SHA-256 小写十六进制哈希。
    private static String sha256Hex(Path path) throws IOException {
        MessageDigest digest = sha256();

        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[IO_BUFFER_SIZE];
            int read;

            while ((read = input.read(buffer)) != -1) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /// 计算字节内容的 SHA-256 小写十六进制哈希。
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

    /// 删除临时文件；失败时保留原因不重要，不能覆盖原始保存诊断。
    private static void deleteTemporaryFile(Path temp) {
        if (temp == null) {
            return;
        }

        try {
            Files.deleteIfExists(temp);
        } catch (IOException | SecurityException ignored) {
            // 清理是尽力而为；原始失败原因已经由 SaveResult.Rejected 报告。
        }
    }

    private static LoadResult rejectedLoad(ResourceId resource, DiagnosticCode code, String message) {
        return rejectedLoad(resource, code, message, null);
    }

    private static LoadResult rejectedLoad(ResourceId resource,
                                           DiagnosticCode code,
                                           String message,
                                           @Nullable String fieldPath) {
        return new LoadResult.Rejected(report(resource, code, message, fieldPath));
    }

    private static SaveResult rejectedSave(ResourceId resource, DiagnosticCode code, String message) {
        return new SaveResult.Rejected(report(resource, code, message, null));
    }

    /// 构造只含单条 File 作用域诊断的单资源报告。
    private static DiagnosticReport report(ResourceId resource,
                                           DiagnosticCode code,
                                           String message,
                                           @Nullable String fieldPath) {
        Diagnostic diagnostic = new Diagnostic(
                code,
                Severity.ERROR,
                LocalizedMessage.literal(message),
                new DiagnosticScope.File(resource),
                fieldPath == null ? null : new Diagnostic.Location(null, fieldPath),
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
}
