package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/// 把 [ResourceId] 限制在一个资源根内的纯路径解析器。
///
/// 本类只做词法检查、路径组合和符号链接边界校验，不读取或写入目标文件内容，不创建目录，
/// 也不要求资源根或目标存在。解析结果仍使用资源根下的词法路径；文件服务应在真正打开文件时
/// 继续使用宿主提供的安全 IO 方式，因为本类无法消除“校验后路径被替换”的 TOCTOU 窗口。
///
/// ## 拒绝规则
///
/// - 空白资源身份、绝对路径、UNC、Windows 盘符、NUL 与反斜杠；
/// - 空路径段、`.` 段与 `..` 段（即使归一化后仍在根内也拒绝）；
/// - 组合并 `normalize()` 后不以资源根开头的路径；
/// - 已存在目标的真实路径不在资源根真实路径内，或写目标本身是符号链接；
/// - 写路径最近存在的祖先无法解析为根内真实路径。
///
/// 所有拒绝都抛出 [ResourcePathException]，不泄漏 [InvalidPathException] 或 [IOException]。
/// 资源根只做绝对化与规范化，不要求存在；符号链接校验按当前文件系统状态进行。
///
/// @see ResourcePathException
@NullMarked
public final class ResourcePathResolver {

    private static final String BLANK_RESOURCE_ID = "resource id is blank";
    private static final String NUL_RESOURCE_ID = "resource id contains a NUL character";
    private static final String BACKSLASH_RESOURCE_ID =
            "resource id contains a backslash; use '/' separators";
    private static final String UNC_RESOURCE_PATH = "UNC resource paths are not allowed";
    private static final String ABSOLUTE_RESOURCE_PATH = "absolute resource paths are not allowed";
    private static final String EMPTY_SEGMENT = "resource id contains an empty path segment";
    private static final String DOT_SEGMENT = "resource id contains a '.' path segment";
    private static final String DOT_DOT_SEGMENT = "resource id contains a '..' path segment";
    private static final String WINDOWS_DRIVE =
            "Windows drive-qualified resource paths are not allowed";
    private static final String INVALID_PLATFORM_PATH =
            "resource id is not a valid path for the current file system";
    private static final String OUTSIDE_ROOT = "resolved resource path escapes the resource root";
    private static final String WRITE_TARGET_SYMLINK =
            "write target must not be a symbolic link";
    private static final String UNRESOLVABLE_SYMLINK =
            "symbolic link in the resource path cannot be resolved";
    private static final String UNVERIFIABLE_PATH =
            "resource path could not be verified against the resource root";

    private final Path root;

    /// 以资源根创建解析器。
    ///
    /// 传入路径会被转为绝对路径并规范化；不检查目录是否存在，也不访问文件内容。
    ///
    /// @param root 资源根；必须非空
    public ResourcePathResolver(Path root) {
        Path checked = Objects.requireNonNull(root, "root");
        if (checked.toString().isEmpty()) {
            throw new IllegalArgumentException("root must not be empty");
        }
        this.root = checked.toAbsolutePath().normalize();
    }

    /// 规范化为绝对路径的资源根。
    public Path root() {
        return root;
    }

    /// 解析一个用于读取的资源路径；不要求目标文件存在。
    ///
    /// 若目标或其最近存在的祖先落在资源根之外（包括经由符号链接），则拒绝。
    ///
    /// @param id 相对资源身份
    /// @return 资源根下的词法路径
    /// @throws ResourcePathException 资源身份不合法或路径越过资源根
    public Path resolveForRead(ResourceId id) {
        return resolve(Objects.requireNonNull(id, "id"), false);
    }

    /// 解析一个用于写入的资源路径；不创建目标或父目录。
    ///
    /// 除读取路径的边界外，写目标本身不得是符号链接，且最近存在的祖先目录真实路径必须在
    /// 资源根真实路径内。
    ///
    /// @param id 相对资源身份
    /// @return 资源根下的词法路径
    /// @throws ResourcePathException 资源身份不合法、路径越过资源根或写目标是符号链接
    public Path resolveForWrite(ResourceId id) {
        return resolve(Objects.requireNonNull(id, "id"), true);
    }

    private Path resolve(ResourceId id, boolean forWrite) {
        validateSyntax(id);

        Path candidate;
        try {
            candidate = root.resolve(id.value()).normalize();
        } catch (InvalidPathException e) {
            throw new ResourcePathException(id, INVALID_PLATFORM_PATH, e);
        }

        if (!candidate.startsWith(root)) {
            throw new ResourcePathException(id, OUTSIDE_ROOT);
        }

        try {
            if (forWrite && Files.isSymbolicLink(candidate)) {
                throw new ResourcePathException(id, WRITE_TARGET_SYMLINK);
            }

            Path canonicalRoot = canonicalize(id, root);
            Path canonicalCandidate = canonicalize(id, candidate);
            if (!canonicalCandidate.startsWith(canonicalRoot)) {
                throw new ResourcePathException(id, OUTSIDE_ROOT);
            }
        } catch (IOException | SecurityException e) {
            throw new ResourcePathException(id, UNVERIFIABLE_PATH, e);
        }

        return candidate;
    }

    /// 校验资源身份的词法边界；反斜杠不会被当作分隔符或兼容输入接受。
    private static void validateSyntax(ResourceId id) {
        String value = id.value();

        if (value.isBlank()) {
            throw new ResourcePathException(id, BLANK_RESOURCE_ID);
        }
        if (value.indexOf('\0') >= 0) {
            throw new ResourcePathException(id, NUL_RESOURCE_ID);
        }
        if (value.indexOf('\\') >= 0) {
            throw new ResourcePathException(id, BACKSLASH_RESOURCE_ID);
        }
        if (value.startsWith("//")) {
            throw new ResourcePathException(id, UNC_RESOURCE_PATH);
        }
        if (value.startsWith("/")) {
            throw new ResourcePathException(id, ABSOLUTE_RESOURCE_PATH);
        }

        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty()) {
                throw new ResourcePathException(id, EMPTY_SEGMENT);
            }
            if (".".equals(segment)) {
                throw new ResourcePathException(id, DOT_SEGMENT);
            }
            if ("..".equals(segment)) {
                throw new ResourcePathException(id, DOT_DOT_SEGMENT);
            }
            if (hasWindowsDrivePrefix(segment)) {
                throw new ResourcePathException(id, WINDOWS_DRIVE);
            }
        }
    }

    private static boolean hasWindowsDrivePrefix(String segment) {
        if (segment.length() < 2 || segment.charAt(1) != ':') {
            return false;
        }

        char first = segment.charAt(0);
        return (first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z');
    }

    /// 解析路径中已存在前缀的真实路径，并拼接尚不存在的尾部。
    ///
    /// 对不存在或悬空的符号链接直接拒绝；对有效符号链接则跟随并通过真实路径比较边界。
    /// 对写路径而言，这等价于检查最近存在的祖先目录真实路径，同时不跟随目标符号链接。
    private static Path canonicalize(ResourceId id, Path path) throws IOException {
        Deque<Path> missing = new ArrayDeque<>();
        Path existing = path;

        while (existing != null && !Files.exists(existing)) {
            if (Files.isSymbolicLink(existing)) {
                throw new ResourcePathException(id, UNRESOLVABLE_SYMLINK);
            }

            Path name = existing.getFileName();
            if (name == null) {
                throw new IOException("no existing ancestor for resource path");
            }
            missing.addFirst(name);
            existing = existing.getParent();
        }

        if (existing == null) {
            throw new IOException("no existing ancestor for resource path");
        }

        Path canonical = existing.toRealPath();
        for (Path name : missing) {
            canonical = canonical.resolve(name);
        }
        return canonical.normalize();
    }
}
