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
/// 推荐通过 [bind] 统一创建，确保快照、事实、轨道查询与索引来自同一次绑定；公开构造器
/// 只保留给无法使用工厂的宿主。`TrackIndex` 必须由同一快照构建，不能与快照 / 查询混用；
/// 同源校验按**快照引用身份**比较，不使用 record 的 `equals`（内容相等不能证明来自同一 store /
/// 候选绑定）。索引必须保留原快照引用。
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

        // 引用身份而不是 equals：同 revision/document 的异源快照不能被当成同源绑定。
        if (trackIndex.snapshot() != snapshot) {
            throw new IllegalArgumentException(
                    "EvaluationContext trackIndex must hold the exact bound snapshot reference");
        }

        // 事实必须由同一快照的准备请求生成；内容相等不等于同源（复核 R49-H1）。
        if (!facts.sourceRevision().equals(snapshot.revision())) {
            throw new IllegalArgumentException(
                    "EvaluationContext facts were prepared for revision " + facts.sourceRevision()
                            + " but the bound snapshot is " + snapshot.revision());
        }
    }

    /// 统一绑定入口：调用方必须保证快照、事实、查询与索引来自同一次快照绑定。
    public static EvaluationContext bind(
            long contextId,
            AnimationSnapshot snapshot,
            FrameFacts facts,
            TrackLookup tracks,
            TrackIndex trackIndex) {
        return new EvaluationContext(contextId, snapshot, facts, tracks, trackIndex);
    }
}
