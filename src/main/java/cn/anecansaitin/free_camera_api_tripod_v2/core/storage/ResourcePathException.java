package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 资源路径越过存储安全边界时的拒绝。
///
/// [ResourcePathResolver] 只抛出本异常，不把 {@link java.nio.file.InvalidPathException} 或
/// {@link java.io.IOException} 直接暴露给调用方。实现相关的 IO 失败仍保留在 [cause] 中，
/// 但 [reason] 始终是不含具体文件系统路径的稳定英文文本，供诊断或测试匹配。
///
/// @see ResourcePathResolver
@NullMarked
public final class ResourcePathException extends RuntimeException {

    private final ResourceId resource;
    private final String reason;

    /// 以资源身份与稳定原因文本创建拒绝。
    ///
    /// @param resource 被拒绝的资源身份
    /// @param reason   稳定原因文本
    public ResourcePathException(ResourceId resource, String reason) {
        this(resource, reason, null);
    }

    /// 以资源身份、稳定原因文本和底层原因创建拒绝。
    ///
    /// @param resource 被拒绝的资源身份
    /// @param reason   稳定原因文本
    /// @param cause    底层路径或文件系统异常；无底层原因时为空
    public ResourcePathException(ResourceId resource, String reason, @Nullable Throwable cause) {
        super(message(resource, reason), cause);
        this.resource = Objects.requireNonNull(resource, "resource");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /// 被拒绝的资源身份。
    public ResourceId resource() {
        return resource;
    }

    /// 稳定的拒绝原因；不包含具体文件系统路径。
    public String reason() {
        return reason;
    }

    private static String message(ResourceId resource, String reason) {
        return "Unsafe resource path for '" + Objects.requireNonNull(resource, "resource").value()
                + "': " + Objects.requireNonNull(reason, "reason");
    }
}
