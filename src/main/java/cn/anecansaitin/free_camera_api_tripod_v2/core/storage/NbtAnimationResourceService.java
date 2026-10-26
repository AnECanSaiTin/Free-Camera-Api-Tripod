package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceEntry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.DiagnosticReport;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.NullMarked;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/// 本地 NBT 文件资源服务（主设计 §8.2、§8.4）。
///
/// 资源身份仍交给 [ResourcePathResolver] 做根内解析；本类只负责：
///
/// - 读取文件字节 → `NbtIo` + [NbtAccounter] → NBT 深度 / 条目守卫 → `CameraAnimation` 结构
///   `Codec`。**只做结构解码**，统一文档校验仍由 [cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document.CoreAnimationLoader]
///   或文档 store 执行。
/// - 编码文档为 NBT 字节，检查字节 / 深度 / 条目上限后写入同目录临时文件，再原子替换正式文件。
/// - 生成 SHA-256 元数据、枚举 `.nbt` 普通文件、异步保存。
///
/// 不注册网络包、不做 SavedData 生命周期、不压缩、不提供恢复槽或跨版本兼容矩阵。与
/// [FileAnimationResourceService] 一样，本类不消除校验后路径被替换的 TOCTOU 窗口。
@NullMarked
public final class NbtAnimationResourceService implements AnimationResourceService {

    private static final String NBT_SUFFIX = ".nbt";
    private static final String TEMP_PREFIX = ".resource-";
    private static final String TEMP_SUFFIX = ".tmp";

    private final ResourcePathResolver resolver;
    private final Codec<CameraAnimation> structureCodec;
    private final NbtResourceLimits limits;

    /// @param resolver        资源根解析器
    /// @param structureCodec  `CameraAnimation` 结构 Codec；不执行统一文档校验
    /// @param limits          NBT 载荷规模上限
    public NbtAnimationResourceService(ResourcePathResolver resolver,
                                       Codec<CameraAnimation> structureCodec,
                                       NbtResourceLimits limits) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.structureCodec = Objects.requireNonNull(structureCodec, "structureCodec");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public LoadResult load(ResourceId resource) {
        Objects.requireNonNull(resource, "resource");
        Path path;

        try {
            path = resolver.resolveForRead(resource);
        } catch (ResourcePathException exception) {
            return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_PATH_REJECTED,
                    "Resource path rejected: " + exception.reason());
        }

        byte[] bytes;

        try {
            long size = Files.size(path);

            if (size > limits.maxBytes()) {
                return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_TOO_LARGE,
                        "Resource '" + resource.value() + "' is " + size
                                + " bytes, exceeds maxBytes " + limits.maxBytes());
            }

            bytes = Files.readAllBytes(path);
        } catch (NoSuchFileException exception) {
            return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_READ_FAILED,
                    "Resource '" + resource.value() + "' does not exist");
        } catch (IOException | SecurityException exception) {
            return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_READ_FAILED,
                    "Could not read resource '" + resource.value() + "': " + describe(exception));
        }

        if (bytes.length > limits.maxBytes()) {
            return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_TOO_LARGE,
                    "Resource '" + resource.value() + "' is " + bytes.length
                            + " bytes, exceeds maxBytes " + limits.maxBytes());
        }

        CompoundTag tag;

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            tag = NbtIo.read(input, NbtAccounter.create(limits.maxBytes()));
        } catch (IOException | RuntimeException exception) {
            return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_STRUCTURE_INVALID,
                    "Resource '" + resource.value() + "' is not valid NBT: " + describe(exception));
        }

        String violation = NbtTagGuard.check(tag, limits);

        if (violation != null) {
            return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_STRUCTURE_INVALID,
                    "Resource '" + resource.value() + "' exceeds NBT limits: " + violation);
        }

        CameraAnimation document;

        try {
            DataResult<CameraAnimation> parsed = structureCodec.parse(NbtOps.INSTANCE, tag);

            if (parsed.error().isPresent()) {
                return rejectedLoad(resource,
                        FileAnimationResourceService.RESOURCE_STRUCTURE_INVALID,
                        "Resource '" + resource.value() + "' has invalid structure: "
                                + errorMessage(parsed));
            }

            document = parsed.result().orElse(null);
        } catch (RuntimeException exception) {
            return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_STRUCTURE_INVALID,
                    "Resource '" + resource.value() + "' has invalid structure: "
                            + describe(exception));
        }

        if (document == null) {
            return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_STRUCTURE_INVALID,
                    "Resource '" + resource.value() + "' decoded to no document");
        }

        try {
            return new LoadResult.Loaded(document, entryFor(resource, path, bytes));
        } catch (IOException | RuntimeException exception) {
            return rejectedLoad(resource, FileAnimationResourceService.RESOURCE_READ_FAILED,
                    "Could not read resource metadata for '" + resource.value() + "': "
                            + describe(exception));
        }
    }

    @Override
    public SaveResult save(ResourceId resource, CameraAnimation document) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(document, "document");
        Path target;

        try {
            target = resolver.resolveForWrite(resource);
        } catch (ResourcePathException exception) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_PATH_REJECTED,
                    "Resource path rejected: " + exception.reason());
        }

        DataResult<Tag> encoded;

        try {
            encoded = structureCodec.encodeStart(NbtOps.INSTANCE, document);
        } catch (RuntimeException exception) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_ENCODE_FAILED,
                    "Could not encode resource '" + resource.value() + "': " + describe(exception));
        }

        if (encoded.error().isPresent()) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_ENCODE_FAILED,
                    "Could not encode resource '" + resource.value() + "': "
                            + errorMessage(encoded));
        }

        Tag tag = encoded.result().orElseThrow();

        if (!(tag instanceof CompoundTag compound)) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_ENCODE_FAILED,
                    "NBT document root must be a compound tag");
        }

        String violation = NbtTagGuard.check(compound, limits);

        if (violation != null) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_TOO_DEEP,
                    "Encoded resource '" + resource.value() + "' exceeds NBT limits: "
                            + violation);
        }

        byte[] bytes;

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            try (DataOutputStream output = new DataOutputStream(buffer)) {
                NbtIo.write(compound, output);
            }

            bytes = buffer.toByteArray();
        } catch (IOException | RuntimeException exception) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_WRITE_FAILED,
                    "Could not write NBT resource '" + resource.value() + "': "
                            + describe(exception));
        }

        if (bytes.length > limits.maxBytes()) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_TOO_LARGE,
                    "Encoded resource '" + resource.value() + "' is " + bytes.length
                            + " bytes, exceeds maxBytes " + limits.maxBytes());
        }

        Path parent = target.getParent();

        if (parent == null) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_WRITE_FAILED,
                    "Resource path has no parent directory: '" + resource.value() + "'");
        }

        Path temp = null;

        try {
            Files.createDirectories(parent);
            temp = Files.createTempFile(parent, TEMP_PREFIX, TEMP_SUFFIX);
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            temp = null;
            return new SaveResult.Saved(entryFor(resource, target, bytes));
        } catch (AtomicMoveNotSupportedException exception) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_WRITE_FAILED,
                    "Atomic replacement is not supported for resource '" + resource.value() + "'");
        } catch (IOException | RuntimeException exception) {
            return rejectedSave(resource, FileAnimationResourceService.RESOURCE_WRITE_FAILED,
                    "Could not write resource '" + resource.value() + "': " + describe(exception));
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public void saveAsync(ResourceId resource,
                          CameraAnimation document,
                          Consumer<SaveResult> completion) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(completion, "completion");
        CompletableFuture.runAsync(() -> {
            SaveResult result;

            try {
                result = save(resource, document);
            } catch (RuntimeException exception) {
                result = rejectedSave(resource, FileAnimationResourceService.RESOURCE_WRITE_FAILED,
                        "Could not write resource '" + resource.value() + "': "
                                + describe(exception));
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
        } catch (ResourcePathException exception) {
            return Optional.empty();
        }

        try {
            if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(NBT_SUFFIX)) {
                return Optional.empty();
            }

            byte[] bytes = Files.readAllBytes(path);
            return Optional.of(entryFor(resource, path, bytes));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<AnimationResourceEntry> entries() {
        Path root = resolver.root();

        if (!Files.isDirectory(root)) {
            return List.of();
        }

        List<AnimationResourceEntry> entries = new ArrayList<>();

        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(NBT_SUFFIX))
                    .forEach(path -> {
                        ResourceId id = resourceId(root, path);

                        if (id == null) {
                            return;
                        }

                        try {
                            resolver.resolveForRead(id);
                            entries.add(entryFor(id, path, Files.readAllBytes(path)));
                        } catch (IOException | RuntimeException ignored) {
                            // 不可读 / 越界项不暴露给调用方。
                        }
                    });
        } catch (IOException | RuntimeException exception) {
            return List.of();
        }

        entries.sort(Comparator.comparing(entry -> entry.resource().value()));
        return List.copyOf(entries);
    }

    private AnimationResourceEntry entryFor(ResourceId resource, Path path, byte[] bytes)
            throws IOException {
        return new AnimationResourceEntry(
                resource, bytes.length, sha256(bytes), Files.getLastModifiedTime(path).toMillis());
    }

    private static ResourceId resourceId(Path root, Path file) {
        try {
            String relative = root.relativize(file).toString().replace('\\', '/');
            return new ResourceId(relative);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static LoadResult rejectedLoad(ResourceId resource,
                                            DiagnosticCode code,
                                            String message) {
        return new LoadResult.Rejected(new DiagnosticReport(resource, List.of(fileError(
                resource, code, message))));
    }

    private static SaveResult rejectedSave(ResourceId resource,
                                            DiagnosticCode code,
                                            String message) {
        return new SaveResult.Rejected(new DiagnosticReport(resource, List.of(fileError(
                resource, code, message))));
    }

    private static Diagnostic fileError(ResourceId resource,
                                        DiagnosticCode code,
                                        String message) {
        return new Diagnostic(code, Severity.ERROR, LocalizedMessage.literal(message),
                new DiagnosticScope.File(resource), null, null);
    }

    private static String errorMessage(DataResult<?> result) {
        return result.error()
                .map(DataResult.Error::message)
                .orElse("unknown codec error");
    }

    private static String describe(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException ignored) {
            // 临时文件清理失败不覆盖原始保存结果。
        }
    }
}
