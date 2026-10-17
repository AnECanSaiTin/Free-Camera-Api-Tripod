package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一帧内某个世界采样的读数（主设计 §7.7、§3.4）。
///
/// ## 三种状态
///
/// ```text
/// SampleReading = VALUE(finiteNumber, sampledAt) | UNKNOWN(reason, sampledAt)
///               | COLLECTION_ERROR(reason, sampledAt)
/// ```
///
/// | 分支 | 含义 | 处理 |
/// | --- | --- | --- |
/// | [Value] | 得到有限数值 | 参与求值 |
/// | [Unknown] | 本周期因未加载 / 未同步 / 目标不存在而取不到 | `sample(name)` 使依赖通道 ERROR；`sample(name, fallback)` 才惰性求值显式兜底 |
/// | [CollectionError] | 采集器缺失、抛异常、输出非法或超出预算 | **不可兜底**：终止整场并报告采集错误 |
///
/// 第三分支不是“UNKNOWN 的另一种写法”：主设计 §7.7 明确采集器异常、非法输出与
/// 预算违规是**独立采集错误**，不能标成可兜底的 UNKNOWN，否则一个坏采集器会被作者
/// 的 `fallback` 悄悄掩盖（不变量 I9、D18）。
///
/// ## 时间元数据
///
/// `sampledAtTick` 是采集发生时的**游戏刻**，与 `WorldSample.resampleEveryTicks`
/// 同口径：重采周期按游戏刻计，渲染帧率高不会重复采集。它同时参与采样表版本判定
/// （读数、有效性与采样时间元数据变化才使缓存失效，主设计 §7.7）。
///
/// ## 不算文档数据
///
/// 读数由宿主在帧准备阶段生成，属于本帧事实（`FrameFacts` 中的采样表），
/// 不进文档、不进历史、不参与撤销；世界切换、声明 / 参数改变或资源重新解析使表失效。
///
/// ## 采样器不能伪装
///
/// 当前周期取不到数据时必须写 [Unknown]，**不能**沿用上一周期成功值伪装新读数；
/// 读数只保存有限数值，Infinity 与 NaN 一律属于 [CollectionError]。
@NullMarked
public sealed interface SampleReading
        permits SampleReading.Value, SampleReading.Unknown, SampleReading.CollectionError {

    /// 采集发生时的游戏刻。
    long sampledAtTick();

    /// 得到有限数值。
    ///
    /// @param value         读数，必须有限
    /// @param sampledAtTick 采集时的游戏刻，非负
    record Value(float value, long sampledAtTick) implements SampleReading {

        public Value {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("SampleReading.Value must be finite: " + value);
            }

            requireTick(sampledAtTick);
        }
    }

    /// 本周期取不到数据，但属于可以显式兜底的情形。
    ///
    /// @param reason        取不到的原因，供诊断与面板展示
    /// @param sampledAtTick 采集时的游戏刻，非负
    record Unknown(SampleUnknownReason reason, long sampledAtTick) implements SampleReading {

        public Unknown {
            Objects.requireNonNull(reason, "reason");
            requireTick(sampledAtTick);
        }
    }

    /// 采集本身失败：采集器缺失、抛异常、输出非法或超出预算。**不可兜底**。
    ///
    /// @param reason        采集错误原因
    /// @param sampledAtTick 采集时的游戏刻，非负
    record CollectionError(SampleCollectionErrorReason reason, long sampledAtTick)
            implements SampleReading {

        public CollectionError {
            Objects.requireNonNull(reason, "reason");
            requireTick(sampledAtTick);
        }
    }

    /// 读数是否可用（可参与求值）。
    default boolean usable() {
        return this instanceof Value;
    }

    /// 是否属于可显式兜底的“取不到”（只有 [Unknown] 可以）。
    default boolean fallbackable() {
        return this instanceof Unknown;
    }

    /// 三个分支共用的游戏刻校验。
    ///
    /// 采样发生在帧准备阶段，宿主给出的游戏刻不会是负数；负值只可能来自宿主实现错误，
    /// 因此在本类型上直接拒绝，避免把非法时间带进缓存身份与面板展示。
    private static void requireTick(long sampledAtTick) {
        if (sampledAtTick < 0) {
            throw new IllegalArgumentException(
                    "SampleReading sampledAtTick must be non-negative: " + sampledAtTick);
        }
    }
}
