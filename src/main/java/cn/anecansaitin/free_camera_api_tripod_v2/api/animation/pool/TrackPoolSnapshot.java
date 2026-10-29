package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 轨道池文档的一次发布快照：文档与发布序号一起读取。
@NullMarked
public record TrackPoolSnapshot(Revision revision, TrackPoolAnimation document) {

    public TrackPoolSnapshot {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(document, "document");
    }
}
