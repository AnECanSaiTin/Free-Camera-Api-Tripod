package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// 一帧内冻结的世界与空间事实（主设计 §3.4、§7.2，不变量 I5）。
///
/// 帧开始捕获一次，准备、执行与渲染阶段都只读；**不进入文档、历史或网络文档**。
/// 同一帧内所有机位、通道与公式读取同一份事实，不会出现“两块面板看到两个位置”。
///
/// [worldInstanceId] 是运行期分配的独立世界实例身份：同一维度重新进入世界也必须是新值，
/// 不能由维度 ID、游戏刻或 revision 推导。缓存身份同时包含本类型的有效 `AimState`、来源、
/// 活动集合与采样表版本，不能只比较 `RigId`（主设计 §3.4）。
///
/// @param dimension          维度身份
/// @param worldInstanceId    世界实例身份，非负；同维度重进世界也会变化
/// @param gameTick           采集时的游戏刻，非负
/// @param partialTick        帧内插值比例，`[0, 1)`
/// @param sampleTableVersion 采样表版本；读数、有效性或采样时间变化时递增
/// @param rigs               本帧实际需要的机位空间事实
/// @param samples            本帧采样读数表
/// @param valid              本帧事实是否完整有效；无效时依赖它的求值必须报错而非兜底
@NullMarked
public record FrameFacts(
        Identifier dimension,
        long worldInstanceId,
        long gameTick,
        float partialTick,
        long sampleTableVersion,
        Map<RigId, RigFacts> rigs,
        Map<SampleId, SampleReading> samples,
        boolean valid) {

    public FrameFacts {
        Objects.requireNonNull(dimension, "dimension");

        if (worldInstanceId < 0) {
            throw new IllegalArgumentException(
                    "FrameFacts worldInstanceId must be non-negative: " + worldInstanceId);
        }

        if (gameTick < 0) {
            throw new IllegalArgumentException("FrameFacts gameTick must be non-negative: " + gameTick);
        }

        if (!Float.isFinite(partialTick) || partialTick < 0f || partialTick >= 1f) {
            throw new IllegalArgumentException(
                    "FrameFacts partialTick must be finite and in [0, 1): " + partialTick);
        }

        if (sampleTableVersion < 0) {
            throw new IllegalArgumentException(
                    "FrameFacts sampleTableVersion must be non-negative: " + sampleTableVersion);
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        rigs = Map.copyOf(rigs);
        samples = Map.copyOf(samples);
    }

    /// 某机位本帧的空间事实；未捕获时为空。
    public Optional<RigFacts> rig(RigId rigId) {
        return Optional.ofNullable(rigs.get(rigId));
    }

    /// 某样本本帧的读数；未声明或未采集时为空。
    public Optional<SampleReading> sample(SampleId sampleId) {
        return Optional.ofNullable(samples.get(sampleId));
    }

    /// 一台机位在本帧实际需要的空间事实。
    ///
    /// 锚点与目标都用 [SpatialResolution] 三态：`NotRequired` 表示当前上下文不需要，
    /// `Failed` 表示解析失败并阻止依赖它的姿态 / 通道；两者不能混为可空值。
    ///
    /// @param anchor 锚点解析结果
    /// @param target 当前生效目标的解析结果
    /// @param aim    本帧生效的注视状态（含请求覆盖后的结果）
    public record RigFacts(
            SpatialResolution anchor,
            SpatialResolution target,
            AimState aim) {

        public RigFacts {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(aim, "aim");
        }
    }
}
