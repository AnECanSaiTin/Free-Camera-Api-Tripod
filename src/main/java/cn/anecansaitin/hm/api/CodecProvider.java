package cn.anecansaitin.hm.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public interface CodecProvider<T> {
    MapCodec<T> mapCodec();
    Codec<T> codec();
}
