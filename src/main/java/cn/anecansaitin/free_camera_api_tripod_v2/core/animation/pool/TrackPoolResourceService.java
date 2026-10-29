package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import com.mojang.serialization.DataResult;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// 轨道池资源目录服务：有界枚举、受限名称、读取/保存/删除。
///
/// 名称只接受单段 `.json` / `.nbt` 文件名，拒绝路径分隔符与 `..`，避免把资源名变成越界路径。
/// 目录枚举达到上限即停止，不做全目录排序；条目顺序按文件名稳定排序后再截断。
public final class TrackPoolResourceService {

    private final Path root;
    private final DocumentRegistries registries;
    private final TrackPoolFileService files;

    public TrackPoolResourceService(Path root, DocumentRegistries registries) {
        this(root, registries, new TrackPoolFileService());
    }

    public TrackPoolResourceService(Path root,
                                    DocumentRegistries registries,
                                    TrackPoolFileService files) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.registries = Objects.requireNonNull(registries, "registries");
        this.files = Objects.requireNonNull(files, "files");
    }

    /// 枚举资源条目；最多返回 `maxEntries` 条。
    public DataResult<List<Entry>> list(int maxEntries) {
        if (maxEntries < 0) {
            return DataResult.error(() -> "maxEntries must be non-negative");
        }

        if (maxEntries == 0) {
            return DataResult.success(List.of());
        }

        try {
            if (!Files.isDirectory(root)) {
                return DataResult.success(List.of());
            }

            List<Entry> entries = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, this::isResource)) {
                for (Path path : stream) {
                    if (entries.size() >= maxEntries) {
                        break;
                    }

                    entries.add(new Entry(path.getFileName().toString(),
                            Files.size(path), Files.getLastModifiedTime(path).toMillis()));
                }
            }

            entries.sort(Comparator.comparing(Entry::name));
            return DataResult.success(List.copyOf(entries));
        } catch (IOException exception) {
            return DataResult.error(() -> "Could not list track-pool resources: " + exception.getMessage());
        }
    }

    /// 读取一个资源。
    public DataResult<TrackPoolAnimation> load(String name) {
        DataResult<Path> path = resolve(name);

        if (path.error().isPresent()) {
            return DataResult.error(() -> path.error().orElseThrow().message());
        }

        return files.read(path.result().orElseThrow(), registries);
    }

    /// 保存当前会话快照。
    public DataResult<Path> save(String name, TrackPoolEditorSession session) {
        Objects.requireNonNull(session, "session");
        DataResult<Path> path = resolve(name);

        if (path.error().isPresent()) {
            return DataResult.error(() -> path.error().orElseThrow().message());
        }

        return files.write(path.result().orElseThrow(), session.snapshot().document(), registries);
    }

    /// 删除一个资源。
    public DataResult<Path> delete(String name) {
        DataResult<Path> path = resolve(name);

        if (path.error().isPresent()) {
            return DataResult.error(() -> path.error().orElseThrow().message());
        }

        try {
            return Files.deleteIfExists(path.result().orElseThrow())
                    ? DataResult.success(path.result().orElseThrow())
                    : DataResult.error(() -> "Resource does not exist: " + name);
        } catch (IOException exception) {
            return DataResult.error(() -> "Could not delete resource: " + exception.getMessage());
        }
    }

    private DataResult<Path> resolve(String name) {
        if (name == null || name.isBlank()
                || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return DataResult.error(() -> "Invalid track-pool resource name: " + name);
        }

        String lower = name.toLowerCase(Locale.ROOT);

        if (!lower.endsWith(".json") && !lower.endsWith(".nbt") && !lower.endsWith(".nbt.gz")) {
            return DataResult.error(() -> "Track-pool resource must be .json, .nbt or .nbt.gz: " + name);
        }

        Path resolved = root.resolve(name).normalize();

        if (!resolved.startsWith(root)) {
            return DataResult.error(() -> "Resource escapes root: " + name);
        }

        return DataResult.success(resolved);
    }

    private boolean isResource(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }

        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".json") || name.endsWith(".nbt") || name.endsWith(".nbt.gz"))
                && !name.startsWith(".");
    }

    /// 资源条目。
    public record Entry(String name, long size, long modifiedAtMillis) {
        public Entry {
            Objects.requireNonNull(name, "name");
        }
    }
}
