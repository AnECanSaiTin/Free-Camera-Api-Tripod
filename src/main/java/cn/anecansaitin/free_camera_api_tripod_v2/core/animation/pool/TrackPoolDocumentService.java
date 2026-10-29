package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import com.mojang.serialization.DataResult;

import java.nio.file.Path;
import java.util.Objects;

/// 轨道池文档服务：文件读取 → 校验 → store/editor；保存当前发布快照。
///
/// 只接受新 `data_version`；旧文件在 Codec 阶段拒绝。GUI、网络与自动恢复不属于本类。
public final class TrackPoolDocumentService {

    private final DocumentRegistries registries;
    private final TrackPoolFileService files;

    public TrackPoolDocumentService(DocumentRegistries registries) {
        this(registries, new TrackPoolFileService());
    }

    public TrackPoolDocumentService(DocumentRegistries registries, TrackPoolFileService files) {
        this.registries = Objects.requireNonNull(registries, "registries");
        this.files = Objects.requireNonNull(files, "files");
    }

    /// 读取并建立编辑会话。
    public DataResult<TrackPoolEditorSession> open(Path path) {
        DataResult<TrackPoolAnimation> loaded = files.read(path, registries);

        if (loaded.error().isPresent()) {
            return DataResult.error(() -> loaded.error().orElseThrow().message());
        }

        try {
            return DataResult.success(new TrackPoolEditorSession(
                    new TrackPoolDocumentStore(loaded.result().orElseThrow())));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Invalid track-pool document: " + exception.getMessage());
        }
    }

    /// 保存会话当前快照。
    public DataResult<Path> save(Path path, TrackPoolEditorSession session) {
        Objects.requireNonNull(session, "session");
        return files.write(path, session.snapshot().document(), registries);
    }
}
