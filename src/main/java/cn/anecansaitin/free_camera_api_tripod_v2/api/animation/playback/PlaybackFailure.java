package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 播放 / 帧准备的失败（主设计 §7.2、§7.3）。
///
/// 失败按**一次**汇总，避免逐通道重复刷屏。任何实际依赖失败都终止整场：
/// 记录失败时刻、释放相机控制、不执行本帧事件；执行阶段不承诺对宿主副作用回滚。
///
/// @param reason      失败类别
/// @param message     不可变、可本地化的摘要
/// @param failureTime 失败时的动画时间，秒；有限且非负
/// @param diagnostics 结构化诊断；不要求至少一条，但空时仍须有 [message]
@NullMarked
public record PlaybackFailure(
        Reason reason,
        LocalizedMessage message,
        float failureTime,
        List<Diagnostic> diagnostics) {

    public PlaybackFailure {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(message, "message");

        if (!Float.isFinite(failureTime) || failureTime < 0f) {
            throw new IllegalArgumentException(
                    "PlaybackFailure failureTime must be finite and non-negative: " + failureTime);
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        diagnostics = List.copyOf(diagnostics);
    }

    /// 失败类别；与设计 §7.3 的表对应。
    public enum Reason {

        /// 当前实际依赖的锚点不可用、类型或解析器缺失。
        ANCHOR_UNAVAILABLE,

        /// 当前实际依赖的注视目标不可用 / 解析失败。
        AIM_UNAVAILABLE,

        /// 启用通道求值错误，或最终姿态非法。
        EVALUATION_ERROR,

        /// 活动机位矩形非法或布局字段非法。
        INVALID_LAYOUT,

        /// `cameraSource` 指向不存在 / 不活动的 Rig，或来源区间失效。
        INVALID_CAMERA_SOURCE,

        /// 事件数量或区间处理工作量超限。
        EVENT_BUDGET_EXCEEDED,

        /// 必需样本 UNKNOWN 且没有显式默认值。
        SAMPLE_UNKNOWN,

        /// 渲染宿主异常或输出失败。
        RENDER_FAILURE,

        /// 宿主缺少所需能力（锚点解析器、采集器、渲染特性等）。
        HOST_CAPABILITY_MISSING
    }
}
