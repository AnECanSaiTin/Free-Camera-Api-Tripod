package cn.anecansaitin.hm.api.block;

import cn.anecansaitin.hm.api.DocObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/// 播放范围
public sealed interface PlaybackRange extends DocObject<PlaybackRange> {
    Auto AUTO = new Auto();
    /// 自动播放
    record Auto() implements PlaybackRange {
        @Override
        public MapCodec<PlaybackRange> mapCodec() {
            return null;
        }

        @Override
        public Codec<PlaybackRange> codec() {
            return null;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, PlaybackRange> streamCodec() {
            return null;
        }
    }
    /// 自定义播放范围
    record Manual(int start, int end) implements PlaybackRange {
         public Manual {
             if (!Float.isFinite(start) || start < 0f
                     || !Float.isFinite(end) || end <= start) {
                 throw new IllegalArgumentException(
                         "PlaybackRange.Manual requires finite 0 <= start < end");
             }
        }

        @Override
        public MapCodec<PlaybackRange> mapCodec() {
            return null;
        }

        @Override
        public Codec<PlaybackRange> codec() {
            return null;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, PlaybackRange> streamCodec() {
            return null;
        }
    }

//    MapCodec<PlaybackRange> MAP_CODEC = Codec.dispatchedMap(Codec.STRING, );
//    Codec<PlaybackRange> CODEC = MAP_CODEC.codec();
}
