package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/// 文档播放范围：自动从内容推导，或作者显式保存。
@NullMarked
public sealed interface PlaybackRange permits PlaybackRange.Auto, PlaybackRange.Manual {

    /// 自动模式：按全局轨道内容、标记、叠加与音轨范围推导。
    record Auto() implements PlaybackRange {
    }

    /// 手动区间，秒；`0 <= start < end`。
    record Manual(float start, float end) implements PlaybackRange {
        public Manual {
            if (!Float.isFinite(start) || start < 0f
                    || !Float.isFinite(end) || end <= start) {
                throw new IllegalArgumentException(
                        "PlaybackRange.Manual requires finite 0 <= start < end");
            }
        }
    }

    /// 默认自动。
    PlaybackRange AUTO = new Auto();

    /// 结构编码。
    Codec<PlaybackRange> CODEC = Codecs.variant("auto", "manual").<PlaybackRange>partialDispatch(
            "type",
            range -> DataResult.success(range instanceof Manual ? "manual" : "auto"),
            type -> switch (type) {
                case "auto" -> DataResult.<MapCodec<? extends PlaybackRange>>success(
                        MapCodec.unit(AUTO));
                case "manual" -> DataResult.<MapCodec<? extends PlaybackRange>>success(
                        RecordCodecBuilder.<Manual>mapCodec(instance -> instance.group(
                                Codecs.seconds().fieldOf("start").forGetter(Manual::start),
                                Codecs.seconds().fieldOf("end").forGetter(Manual::end)
                        ).apply(instance, Manual::new)));
                default -> DataResult.error(() -> "Unknown playback range: " + type);
            });
}
