package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// 一帧的空间准备计划：宿主按动画时间、播放覆盖与实际依赖算出的**有效输入**
/// （主设计 §7.2、§6.3；复核 R49-H1）。
///
/// ## 为什么要有计划
///
/// 帧准备的顺序是“先纯求值机位活动与注视状态、应用显式覆盖并确定实际依赖，再捕获世界事实”
/// （主设计 §7.2）。这些结果属于宿主层，**不能**让 `FrameFactsProvider` 再从文档、当前播放器或
/// 编辑器选中机位里重新推断：
///
/// - 同一游戏刻可能定位到动画的不同时刻（9 秒注视 A、10 秒注视 B），请求必须能区分；
/// - `Inherit` / `Disabled` / `Replace(C)` 的覆盖结果不同，同一快照会得到不同的生效注视状态；
/// - 局部机位没有文档默认锚点、由播放请求提供锚点时，解析器也必须收到那个锚点；
/// - 活动机位只要读取另一机位的**锚点**空间读数时，只能要求解析锚点，不能顺带要求目标。
///
/// 因此计划逐机位给出三项：**锚点需求**、**目标需求**与**生效注视状态**。两项需求都只有
/// “需要 / 不需要”，解析失败由 [SpatialResolution.Failed] 表达，不能把“不需要”和“失败”
/// 混成一个空值（不变量 I9）。
///
/// ## 与结果的关系
///
/// 计划是一次运行期绑定，不进入文档、历史、网络或撤销；[FrameFacts] 必须由同一份请求的
/// 快照与计划生成，两者不得混用不同帧的输入（复核 R49-H1）。
///
/// @param rigs 本帧需要空间事实的机位；只读副本，键必须存在于请求的快照中
@NullMarked
public record FrameFactsPlan(Map<RigId, RigDemand> rigs) {

    /// 空计划：本帧不解析任何机位空间事实。
    public static FrameFactsPlan empty() {
        return new FrameFactsPlan(Map.of());
    }

    public FrameFactsPlan {
        Objects.requireNonNull(rigs, "rigs");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        rigs = Map.copyOf(rigs);
    }

    /// 计划中的机位集合；只读。
    public Set<RigId> rigIds() {
        return rigs.keySet();
    }

    /// 某机位的空间需求；未列入计划时为空。
    public Optional<RigDemand> demand(RigId rigId) {
        Objects.requireNonNull(rigId, "rigId");
        return Optional.ofNullable(rigs.get(rigId));
    }

    /// 一台机位本帧的空间需求。
    ///
    /// @param anchor 锚点需求：`Required` 时必须解析该有效锚点
    /// @param target 目标需求：`Required` 时必须解析该有效注视目标
    /// @param aim    本帧**生效**的注视状态（已应用请求覆盖）；原样写入 [FrameFacts.RigFacts]
    public record RigDemand(
            SpatialDemand<Anchor> anchor,
            SpatialDemand<AimTarget> target,
            AimState aim) {

        public RigDemand {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(aim, "aim");

            // 需要解析目标时，它必须是本帧生效的同一个目标：解析一个不会被使用的目标
            // 只会在失败时误伤整场（主设计 §6.3、§7.3）。
            target.optionalValue().ifPresent(demanded -> {
                boolean effective = aim.targetOptional()
                        .filter(candidate -> candidate.equals(demanded))
                        .isPresent();

                if (!effective) {
                    throw new IllegalArgumentException(
                            "FrameFactsPlan.RigDemand target demand must equal the effective aim target");
                }
            });
        }

        /// 不需要锚点与目标，只携带生效注视状态。
        public static RigDemand aiming(AimState aim) {
            return new RigDemand(SpatialDemand.notRequired(), SpatialDemand.notRequired(), aim);
        }

        /// 只解析锚点（活动机位只读取另一机位的锚点读数时使用）。
        public static RigDemand anchored(Anchor anchor, AimState aim) {
            return new RigDemand(SpatialDemand.required(anchor), SpatialDemand.notRequired(), aim);
        }

        /// 解析锚点与目标；目标必须是生效注视状态里的同一个目标。
        public static RigDemand anchoredAndAiming(Anchor anchor, AimTarget target, AimState aim) {
            return new RigDemand(SpatialDemand.required(anchor), SpatialDemand.required(target), aim);
        }

        /// 是否要求解析锚点。
        public boolean needsAnchor() {
            return anchor.required();
        }

        /// 是否要求解析目标。
        public boolean needsTarget() {
            return target.required();
        }
    }

    /// 一项空间需求：需要解析（[Required]）或本帧不需要（[NotRequired]）。
    ///
    /// “不需要”不是失败，也不是“未知”；解析不出来的情况只能由
    /// [SpatialResolution.Failed] 表达，不能通过省掉需求来掩盖。
    ///
    /// @param <T> 需求的锚点 / 目标类型
    public sealed interface SpatialDemand<T> permits SpatialDemand.NotRequired, SpatialDemand.Required {

        /// 本帧不需要该项事实。
        record NotRequired<T>() implements SpatialDemand<T> {
        }

        /// 本帧需要解析该项事实。
        ///
        /// @param value 有效锚点 / 有效注视目标，非空
        record Required<T>(T value) implements SpatialDemand<T> {

            public Required {
                Objects.requireNonNull(value, "value");
            }
        }

        /// 不需要解析。
        static <T> SpatialDemand<T> notRequired() {
            return new NotRequired<>();
        }

        /// 需要解析给定值。
        static <T> SpatialDemand<T> required(T value) {
            return new Required<>(value);
        }

        /// 是否要求解析。
        default boolean required() {
            return this instanceof Required<?>;
        }

        /// 需要解析的值；[NotRequired] 时为空。
        default Optional<T> optionalValue() {
            if (this instanceof Required<?> required) {
                @SuppressWarnings("unchecked")
                T value = (T) required.value();
                return Optional.of(value);
            }

            return Optional.empty();
        }
    }
}
