package cn.anecansaitin.hm.api;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface StreamCodecProvider<T> {
    StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec();
}
