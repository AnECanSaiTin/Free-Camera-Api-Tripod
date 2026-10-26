package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 一次公式求值的显式输入（主设计 §6.1、§6.3、§7.7）。
///
/// ## 为什么把输入都显式列出
///
/// 求值不得读取“当前编辑机位”或全局状态：
///
/// - `source`：**本次求值的源码位置**（轨道 / 关键帧 / 布局分量 / 变量 / 函数）。
///   同一份编译产物可能被多个入口复用，编译器只按文本共享；调用方必须显式告诉求值器
///   “现在求的是哪一处”，失败才能定位到具体字段（复核 R44-N2）。缓存命中的结果也按
///   本字段重新包装，不继承其它入口的来源链。
/// - `rigId`：读取空间事实（世界坐标 / 锚点局部坐标 / 到目标距离）时的机位上下文。
///   **可空**：布局公式或全局试算可能没有机位上下文；此时读取空间事实必须返回
///   `SPATIAL_CONTEXT_MISSING` 错误，而不是猜一个机位。`TrackRef` 不使用本字段——
///   它切到被引用轨道所属机位（主设计 §6.1）。
/// - `time` / `progress` / `worldTime`：内建量 `t` / `p` / `wt` 的本帧取值。
///   `wt` 是**世界日进度** `[0,1)`，唯一来源是 [FrameFacts#worldTime] 的
///   [WorldTime#progress]；不能把游戏刻或秒数直接塞进本字段。
/// - `spatial`：本帧已准备的空间读数；只读外部锚点/目标事实，不反读待求值相机。
/// - `randomSeed`：`random()` 的唯一随机来源。`random()` 明确非确定；需要确定性的公式
///   必须使用 `noise(seed, t)`。
/// - `session`：本帧共享的执行会话（工作量额度与嵌套深度）。经 `TrackRef` 进入曲线公式、
///   再进入变量 / 函数体时，调用链必须传入**同一个**会话，不能各自新建额度
///   （复核 R42-E1）。缓存命中按 `EvaluationSession` 的记账规则计 0。
/// - `sampler`：变量可能绑定 `TrackRef`，求值需要按被引用轨道所属机位取样。
///
/// 样本读数从 [EvaluationContext#facts] 的采样表读取；名字到 `SampleId` 的绑定在提交期完成。
///
/// @param context    绑定的快照 / 帧事实 / 轨道查询
/// @param source     本次求值的源码位置；非空
/// @param rigId      空间上下文机位；无上下文时为空
/// @param time       内建量 `t`：文档时间，秒，有限且非负
/// @param progress   内建量 `p`：动画进度，`[0, 1]`
/// @param worldTime  内建量 `wt`：世界日进度，`[0, 1)`
/// @param spatial    本帧空间读数
/// @param randomSeed `random()` 的随机种子
/// @param session    本帧共享执行会话；非空
/// @param sampler    曲线采样器（供 `TrackRef` 使用）
@NullMarked
public record ExpressionInputs(
        EvaluationContext context,
        ExpressionSource source,
        @Nullable RigId rigId,
        float time,
        float progress,
        float worldTime,
        SpatialReadings spatial,
        long randomSeed,
        EvaluationSession session,
        CurveSampler sampler) {

    public ExpressionInputs {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(spatial, "spatial");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(sampler, "sampler");

        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException("ExpressionInputs time must be finite and non-negative: " + time);
        }

        if (!Float.isFinite(progress) || progress < 0f || progress > 1f) {
            throw new IllegalArgumentException("ExpressionInputs progress must be finite and in [0, 1]: " + progress);
        }

        if (!Float.isFinite(worldTime) || worldTime < 0f || worldTime >= 1f) {
            throw new IllegalArgumentException(
                    "ExpressionInputs worldTime must be finite and in [0, 1): " + worldTime);
        }
    }
}
