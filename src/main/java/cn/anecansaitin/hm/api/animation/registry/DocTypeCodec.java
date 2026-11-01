package cn.anecansaitin.hm.api.animation.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface DocTypeCodec<T> {
    Codec<T> codec(TypeRegistry registry);

    MapCodec<T> mapCodec(TypeRegistry registry);

    StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec(TypeRegistry registry);

    static <T> DocTypeCodec<T> stable(Codec<T> codec, MapCodec<T> mapCodec,
                                      StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        return new Stable<>(codec, mapCodec, streamCodec);
    }

    record Stable<T>(Codec<T> codec, MapCodec<T> mapCodec,
                     StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) implements DocTypeCodec<T> {

        @Override
        public Codec<T> codec(TypeRegistry registry) {
            return codec;
        }

        @Override
        public MapCodec<T> mapCodec(TypeRegistry registry) {
            return mapCodec;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec(TypeRegistry registry) {
            return streamCodec;
        }
    }
}
