package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/// 机位静态输出配置：默认不输出。
///
/// @param enabled           是否输出；默认 false
/// @param zOrder            层级，大的在上
/// @param resolutionScale   请求的内部渲染分辨率比例，`(0, 1]`
/// @param updateEveryFrames 更新间隔，`>= 1`
@NullMarked
public record RigOutput(
        boolean enabled,
        int zOrder,
        double resolutionScale,
        int updateEveryFrames) {

    /// 默认输出配置：不输出、层级 0、全分辨率、每帧更新。
    public static final RigOutput HIDDEN = new RigOutput(false, 0, 1.0, 1);

    /// 结构编码。
    public static final MapCodec<RigOutput> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("enabled").forGetter(RigOutput::enabled),
                    Codec.INT.fieldOf("z_order").forGetter(RigOutput::zOrder),
                    Codec.DOUBLE.fieldOf("resolution_scale").forGetter(RigOutput::resolutionScale),
                    Codec.INT.fieldOf("update_every_frames").forGetter(RigOutput::updateEveryFrames)
            ).apply(instance, RigOutput::new));

    /// 独立 Codec。
    public static final Codec<RigOutput> CODEC = MAP_CODEC.codec();

    public RigOutput {
        if (!Double.isFinite(resolutionScale) || resolutionScale <= 0.0 || resolutionScale > 1.0) {
            throw new IllegalArgumentException(
                    "RigOutput resolutionScale must be finite and in (0, 1]: " + resolutionScale);
        }

        if (updateEveryFrames < 1) {
            throw new IllegalArgumentException(
                    "RigOutput updateEveryFrames must be >= 1: " + updateEveryFrames);
        }
    }

    public RigOutput withEnabled(boolean value) {
        return new RigOutput(value, zOrder, resolutionScale, updateEveryFrames);
    }
}
