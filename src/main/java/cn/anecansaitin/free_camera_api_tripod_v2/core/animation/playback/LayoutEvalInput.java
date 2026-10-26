package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampler;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialReadings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一次布局数值求值的显式输入：本帧上下文、机位、分量、时间与空间读数。
///
/// ## 为什么显式列出
///
/// 布局数值不是轨道，没有 `TrackId`，不能沿用 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampleRequest]
/// 的轨道身份。求值所需的全部环境因此集中在本记录里，由调用方（后续
/// `PlaybackFrameFactory`）在准备阶段一次绑定：
///
/// - [context] / [session]：与本帧其它通道、公式共享同一快照、事实与执行额度；
/// - [rigId] / [field]：失败诊断能定位到具体机位的具体矩形分量；
/// - [time] / [progress] / [worldTime] / [spatial] / [randomSeed]：公式内建量与空间读数；
/// - [sampler]：公式里的变量可能绑定 `TrackRef`，曲线读取需要按被引用轨道所属机位采样。
///
/// [time] 是**文档时间**，不是曲线键时间。与曲线上的公式键一致：公式总是在当前采样时刻
/// 求值，而不是在端点键的时刻求值；这样同一帧内所有依赖读到的都是同一时刻。
///
/// ## 四个矩形分量共用一份帧输入
///
/// [ResolvedRectEvaluator] 对 `x / y / width / height` 依次求值，只通过 [withField] 切换
/// [field]；其余字段保持不变。键身份不在输入里：`AnimatedNumber.Value` 的公式没有键，
/// `AnimatedNumber.Keyed` 的端点公式由求值器按实际端点键补入 `KeyId`。
///
/// ## 输入不合法与求值失败的区别
///
/// 本记录的构造器只拒绝**调用方编程错误**（非有限时间、越界进度、空引用）。作者数据导致
/// 的公式编译失败、非有限结果、非正宽高等属于**本帧求值失败**，由
/// [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSample.Failure] 与
/// [LayoutValue.Failure] 表达，不能用 `NaN` 或抛异常冒充。
///
/// @param context    本帧已绑定的求值上下文，非空
/// @param session    本帧共享执行会话，非空
/// @param rigId      布局所属机位，非空；机位身份也用于诊断定位与公式的空间上下文
/// @param field      当前求值的矩形分量，非空
/// @param time       文档时间，秒；有限且非负，`-0.0` 规范化为 `0.0`
/// @param progress   动画进度，`[0, 1]`
/// @param worldTime  内建量 `wt`：世界日进度，`[0, 1)`；来自 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime#progress]
/// @param spatial    本帧已准备的空间读数，非空；没有读数时使用 [SpatialReadings#EMPTY]
/// @param randomSeed `random()` 的种子；同一帧由调用方固定
/// @param sampler    曲线采样器，非空；供公式里的 `TrackRef` 使用
@NullMarked
public record LayoutEvalInput(
        EvaluationContext context,
        EvaluationSession session,
        RigId rigId,
        ExpressionSource.LayoutField field,
        float time,
        float progress,
        float worldTime,
        SpatialReadings spatial,
        long randomSeed,
        CurveSampler sampler) {

    public LayoutEvalInput {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(spatial, "spatial");
        Objects.requireNonNull(sampler, "sampler");

        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException(
                    "LayoutEvalInput time must be finite and non-negative: " + time);
        }

        if (!Float.isFinite(progress) || progress < 0f || progress > 1f) {
            throw new IllegalArgumentException(
                    "LayoutEvalInput progress must be finite and in [0, 1]: " + progress);
        }

        if (!Float.isFinite(worldTime) || worldTime < 0f || worldTime >= 1f) {
            throw new IllegalArgumentException(
                    "LayoutEvalInput worldTime must be finite and in [0, 1): " + worldTime);
        }

        // -0.0 规范化；其余有效时间不变（与 ExpressionInputs / CurveSampleRequest 一致）
        if (time == 0f) {
            time = 0f;
        }
    }

    /// 换矩形分量，其余输入不变。
    ///
    /// 一个矩形的四个分量共享同一份帧上下文；求值器不复制快照、不重建会话，也不改变时间。
    ///
    /// @param newField 目标分量，非空
    /// @return 仅 [field] 不同的输入
    public LayoutEvalInput withField(ExpressionSource.LayoutField newField) {
        Objects.requireNonNull(newField, "newField");

        return new LayoutEvalInput(context, session, rigId, newField, time, progress, worldTime,
                spatial, randomSeed, sampler);
    }
}
