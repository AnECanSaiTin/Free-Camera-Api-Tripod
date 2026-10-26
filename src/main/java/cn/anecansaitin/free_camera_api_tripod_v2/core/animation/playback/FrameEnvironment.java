package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一帧的宿主环境身份：维度、世界实例、游戏刻、partial tick 与世界日时间。
///
/// 该值由宿主在帧准备前提供；世界实例身份用于使缓存失效，不能由维度或游戏刻推导。
/// [worldTime] 携带原始世界日时刻与**可被数据包修改**的昼夜长度，`wt` 的归一化只在
/// [WorldTime#progress] 一处发生。
///
/// @param dimension       维度身份
/// @param worldInstanceId 世界实例身份，非负
/// @param gameTick        游戏刻，非负
/// @param partialTick     帧内插值比例，`[0, 1)`
/// @param worldTime       世界日时刻与昼夜长度，非空
@NullMarked
public record FrameEnvironment(Identifier dimension,
                               long worldInstanceId,
                               long gameTick,
                               float partialTick,
                               WorldTime worldTime) {

    public FrameEnvironment {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(worldTime, "worldTime");

        if (worldInstanceId < 0) {
            throw new IllegalArgumentException("worldInstanceId must be non-negative: " + worldInstanceId);
        }

        if (gameTick < 0) {
            throw new IllegalArgumentException("gameTick must be non-negative: " + gameTick);
        }

        if (!Float.isFinite(partialTick) || partialTick < 0f || partialTick >= 1f) {
            throw new IllegalArgumentException("partialTick must be finite and in [0, 1): " + partialTick);
        }
    }
}
