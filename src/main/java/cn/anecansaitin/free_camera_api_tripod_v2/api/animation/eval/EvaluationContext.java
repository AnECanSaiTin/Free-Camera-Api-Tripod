package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一次求值的不可变上下文：快照 + 帧事实 + 轨道查询（主设计 §3.4、§6、I6）。
///
/// 上下文绑定**某一份快照**；版本变化后必须重新建立，不能清空数值缓存后继续引用旧文档。
/// 同一上下文内所有采样共享同一份 [FrameFacts] 与 [TrackLookup]；缓存以 [EvaluationKey]
/// 区分帧、机位与事实版本。
///
/// 预览候选使用独立上下文身份，不能与正式播放共享缓存。
///
/// @param contextId 上下文身份，非负；同一会话内区分正式 / 预览 / 试算
/// @param snapshot  绑定的已发布快照
/// @param facts     本帧冻结的世界与空间事实
/// @param tracks    按 `TrackId` 查曲线轨道的最小查询面
/// @param trackIndex 绑定快照的轨道索引（含所属机位与重复 ID 诊断）
@NullMarked
public record EvaluationContext(
        long contextId,
        AnimationSnapshot snapshot,
        FrameFacts facts,
        TrackLookup tracks,
        TrackIndex trackIndex) {

    public EvaluationContext {
        if (contextId < 0) {
            throw new IllegalArgumentException("EvaluationContext contextId must be non-negative: " + contextId);
        }

        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(trackIndex, "trackIndex");
    }
}
