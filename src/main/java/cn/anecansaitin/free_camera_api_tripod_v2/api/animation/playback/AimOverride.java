package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;

/// 播放 / 预览的注视覆盖：继承 / 禁用 / 替换三态（主设计 §6.3、§7.1，Q5 裁决）。
///
/// ## 与文档 `AimState` 的分工
///
/// | 类型 | 含义 | 是否进文档 |
/// | --- | --- | --- |
/// | `api.spatial.AimState` | **某个时刻**的注视状态（`Off` / `Target`） | 是，属于 `CameraRig.aim` 轨道 |
/// | 本类型 | **一次播放或预览**的覆盖策略 | 否，属于请求参数 |
///
/// 两者不可互换：覆盖不能被写进状态键，状态轨道也不能表达“整次播放固定 Target”。
///
/// ## 三态语义
///
/// - [Inherit]：按当前时刻 `rig.aim` 的求值结果使用；等价于“没有覆盖”。
/// - [Disabled]：整次播放 / 预览视为 `Off`，不覆盖任何旋转轴；**不写文档、不插旋转键**。
/// - [Replace]：整次播放 / 预览固定使用请求给定的 `AimTarget`，不回写或改动状态键。
///
/// 覆盖只在宿主层应用：先按时间求值状态轨道，再应用覆盖得到**生效的 `AimState`**，
/// 求值层只接收这个已解析结果（不依赖本类型，主设计 §2.1 的依赖方向、§7.2 的准备顺序）。
///
/// ## 固定 Target 覆盖不能靠写文档 Off 关闭
///
/// 若请求 / 预览固定在 [Replace]，仅往文档里写 `Off` 键并不能关闭注视；
/// 显式姿态捕获工具（主设计 §4.7）必须先提示清除该覆盖，再按清除后的上下文重新预览与捕获，
/// 不能静默修改请求，也不能把未关闭的结果显示为成功。
///
/// ## 校验
///
/// 固定 Target 的掩码、锚点完整性与文档侧同一套规则；`Replace` 的参考目标不要求
/// “在当前时刻存在”以外的时间点存在（覆盖在整次播放内固定，不随时变化）。
/// 覆盖的序列化归属在播放请求（`StreamCodec`）与网络包，不进入文档 Codec。
///
/// ## 位置
///
/// 本类型属于 `api.animation.playback`（Q5 裁决）：请求、预览与网络都从这里引用；
/// `api.spatial` 保持“文档值与空间变换”的职责，不承载请求策略。
@NullMarked
public sealed interface AimOverride permits AimOverride.Inherit, AimOverride.Disabled, AimOverride.Replace {

    /// 继承当前时刻状态：不覆盖。
    record Inherit() implements AimOverride {
    }

    /// 整次播放 / 预览禁用注视：视为 `Off`。
    record Disabled() implements AimOverride {
    }

    /// 整次播放 / 预览替换为固定目标；不回写状态键。
    ///
    /// @param target 固定注视配置，非空
    record Replace(AimTarget target) implements AimOverride {

        public Replace {
            Objects.requireNonNull(target, "target");
        }
    }

    /// 继承的共享取值。
    AimOverride INHERIT = new Inherit();

    /// 显式禁用的共享取值。
    AimOverride DISABLED = new Disabled();

    /// 继承当前时刻状态。
    static AimOverride inherit() {
        return INHERIT;
    }

    /// 整次播放禁用注视。
    static AimOverride disabled() {
        return DISABLED;
    }

    /// 整次播放替换为固定目标。
    static AimOverride replace(AimTarget target) {
        return new Replace(target);
    }

    /// 是否没有覆盖。
    default boolean inherits() {
        return this instanceof Inherit;
    }

    /// 是否显式禁用注视。
    default boolean disables() {
        return this instanceof Disabled;
    }

    /// 固定目标；非 [Replace] 时为空。
    default Optional<AimTarget> fixedTarget() {
        return this instanceof Replace replace ? Optional.of(replace.target()) : Optional.empty();
    }
}
