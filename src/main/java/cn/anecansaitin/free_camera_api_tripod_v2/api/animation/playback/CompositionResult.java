package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 一次多路合成的结果（主设计 §7.6）。
///
/// 失败时停止后续输出与事件、释放资源，并报告可能的部分执行；不得把失败呈现为新的成功帧。
///
/// @param presented 是否成功呈现一帧
/// @param statuses  每路渲染状态；顺序与活动机位顺序一致
/// @param failure   失败；成功时为空
@NullMarked
public record CompositionResult(
        boolean presented,
        List<RigRenderStatus> statuses,
        @Nullable PlaybackFailure failure) {

    public CompositionResult {
        // 防御性复制：不保留调用方容器（主设计 §3.2）
        statuses = List.copyOf(statuses);

        if (presented && failure != null) {
            throw new IllegalArgumentException("Presented CompositionResult must not carry a failure");
        }

        if (!presented && failure == null) {
            throw new IllegalArgumentException("Failed CompositionResult must carry a failure");
        }
    }
}
