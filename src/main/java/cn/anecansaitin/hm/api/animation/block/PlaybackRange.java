package cn.anecansaitin.hm.api.animation.block;

import cn.anecansaitin.hm.api.Codecs;
import cn.anecansaitin.hm.api.DocObject;
import cn.anecansaitin.hm.api.animation.registry.DocTypes;
import cn.anecansaitin.hm.api.animation.registry.DocType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/// 播放范围
public sealed interface PlaybackRange extends DocObject {
    Auto AUTO = new Auto();

    /// 自动播放
    record Auto() implements PlaybackRange {
        public static final MapCodec<Auto> MAP_CODEC = MapCodec.unit(AUTO);
        public static final Codec<Auto> CODEC = MAP_CODEC.codec();
        public static final StreamCodec<RegistryFriendlyByteBuf, Auto> STREAM_CODEC = StreamCodec.unit(AUTO);

        @Override
        public DocType<Auto> type() {
            return DocTypes.PLAYBACK_RANGE_AUTO;
        }
    }

    /// 自定义播放范围
    record Manual(float start, float end) implements PlaybackRange {
        public static final MapCodec<Manual> MAP_CODEC = Codec.mapPair(
                        Codecs.positiveFloat().fieldOf("start"),
                        Codecs.positiveFloat().fieldOf("end"))
                .flatXmap(
                        p -> p.getFirst() < p.getSecond()
                                ? DataResult.success(new Manual(p.getFirst(), p.getSecond()))
                                : DataResult.error(() -> "PlaybackRange.Manual requires finite 0 <= start < end, got " + p),
                        m -> DataResult.success(Pair.of(m.start(), m.end())));
        public static final Codec<Manual> CODEC = MAP_CODEC.codec();
        public static final StreamCodec<RegistryFriendlyByteBuf, Manual> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Manual::start,
                ByteBufCodecs.FLOAT, Manual::end,
                Manual::new
        );

        public Manual {
            if (!Float.isFinite(start) || start < 0f
                    || !Float.isFinite(end) || end <= start) {
                throw new IllegalArgumentException(
                        "PlaybackRange.Manual requires finite 0 <= start < end");
            }
        }

        @Override
        public DocType<Manual> type() {
            return DocTypes.PLAYBACK_RANGE_MANUAL;
        }
    }
}
