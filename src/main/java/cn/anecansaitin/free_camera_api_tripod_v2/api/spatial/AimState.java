package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/// 某一时刻的注视状态：`Off` 或 `Target(AimTarget)`（主设计 §3.1.1、§6.3）。
///
/// ## 为什么是两分支而不是可空 AimTarget
///
/// 注视在文档里是一条时间轴轨道（`CameraRig.aim`），每个时刻都必须有一个**明确的值**：
///
/// - `Off` 是作者写下的状态：从该键起不覆盖任何旋转轴，立即使用当前旋转通道结果，
///   不恢复进入注视前的旧角度，也不自动插入旋转键（主设计 §3.1.1、§6.4）。
/// - `Target` 携带完整注视配置（目标锚点 + 旋转掩码 + 世界轴偏移），切换目标即切换整套配置。
///
/// 若用可空 [AimTarget] 表达，则“作者显式关闭注视”与“这里还没有值 / 未求值”无法区分，
/// 切回旋转通道的边界也就没有可校验的对象。播放请求的“继承 / 显式禁用 / 替换”三态
/// 覆盖属于 `api.animation.playback` 的请求契约（P1 计划 §7.3 Q5），本类型只表达
/// 文档与帧事实里的有效注视状态。
///
/// ## 与旋转通道的关系
///
/// `Target` 只接管掩码启用的世界 Yaw/Pitch，Roll 保留通道或基础姿态；
/// 被接管的轴上原旋转通道仍须通过错误检查（不变量 I9）。目标重合或方向非有限是错误，
/// 不是“保持上一帧”（主设计 §6.4）。
///
/// ## 缓存身份
///
/// 求值缓存身份包含**有效 AimState**，不能只比较 `RigId`（主设计 §3.4）：
/// 同一机位在 A/B 目标间切换时，空间读数与姿态都必须重新求值。
@NullMarked
public sealed interface AimState permits AimState.Off, AimState.Target {

    /// 不注视：从该时刻起不覆盖任何旋转轴。
    record Off() implements AimState {
    }

    /// 注视某个目标：目标 + 掩码 + 世界轴偏移。
    ///
    /// @param target 注视配置，非空
    record Target(AimTarget target) implements AimState {

        public Target {
            Objects.requireNonNull(target, "target");
        }
    }

    /// 关闭注视的共享取值。
    AimState OFF = new Off();

    /// 关闭注视。
    static AimState off() {
        return OFF;
    }

    /// 注视给定配置。
    static AimState target(AimTarget target) {
        return new Target(target);
    }

    /// 当前是否在注视；`Target` 时给出配置。
    default boolean aiming() {
        return this instanceof Target;
    }

    /// 当前注视配置；`Off` 时为空。
    default Optional<AimTarget> targetOptional() {
        return this instanceof Target target ? Optional.of(target.target()) : Optional.empty();
    }

    /// 当前注视配置；`Off` 时为 `null`。供不需要 `Optional` 的热路径使用。
    default @Nullable AimTarget targetOrNull() {
        return this instanceof Target target ? target.target() : null;
    }

    /// 分支标签：`off` / `target`。
    static Codec<String> VARIANT = Codecs.variant("off", "target");

    /// 结构编码：按 `type` 分派。`off` 无额外字段；`target` 携带完整注视配置。
    ///
    /// 目标立即切换，不做双目标混合或平滑过渡（主设计 §3.1.1）。
    public static Codec<AimState> codec(DocumentRegistries registries) {
        return VARIANT.<AimState>partialDispatch(
                "type",
                state -> DataResult.success(state instanceof AimState.Target ? "target" : "off"),
                name -> switch (name) {
                    case "off" -> DataResult.<MapCodec<? extends AimState>>success(
                            MapCodec.unit(AimState.OFF));
                    case "target" -> DataResult.<MapCodec<? extends AimState>>success(
                            AimTarget.codec(registries).fieldOf("target")
                                    .xmap(AimState.Target::new, target -> target.target()));
                    default -> DataResult.error(() -> "Unknown aim state: " + name);
                });
    }
}
