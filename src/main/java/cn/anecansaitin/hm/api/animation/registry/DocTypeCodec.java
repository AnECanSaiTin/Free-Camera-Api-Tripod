package cn.anecansaitin.hm.api.animation.registry;

import cn.anecansaitin.hm.api.DocObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.Function;

public interface DocTypeCodec<T extends DocObject> {
    Codec<T> codec(TypeRegistry registry);

    MapCodec<T> mapCodec(TypeRegistry registry);

    StreamCodec<RegistryFriendlyByteBuf, T> streamCodec(TypeRegistry registry);

    static <R extends DocObject> DocTypeCodec<R> stable(Codec<R> codec, MapCodec<R> mapCodec,
                                                        StreamCodec<RegistryFriendlyByteBuf, R> streamCodec) {
        return new Stable<>(codec, mapCodec, streamCodec);
    }

    static <R extends DocObject> DocTypeCodec<R> flex(Function<TypeRegistry, Codec<R>> codec,
                                                      Function<TypeRegistry, MapCodec<R>> mapCodec,
                                                      Function<TypeRegistry, StreamCodec<RegistryFriendlyByteBuf, R>> streamCodec) {
        return new Flex<>(codec, mapCodec, streamCodec);
    }

    record Stable<T extends DocObject>(Codec<T> codec, MapCodec<T> mapCodec,
                                       StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) implements DocTypeCodec<T> {

        @Override
        public Codec<T> codec(TypeRegistry registry) {
            return codec;
        }

        @Override
        public MapCodec<T> mapCodec(TypeRegistry registry) {
            return mapCodec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec(TypeRegistry registry) {
            return streamCodec;
        }
    }

    record Flex<T extends DocObject>(Function<TypeRegistry, Codec<T>> codec,
                                     Function<TypeRegistry, MapCodec<T>> mapCodec,
                                     Function<TypeRegistry, StreamCodec<RegistryFriendlyByteBuf, T>> streamCodec) implements DocTypeCodec<T> {
        @Override
        public Codec<T> codec(TypeRegistry registry) {
            return codec.apply(registry);
        }

        @Override
        public MapCodec<T> mapCodec(TypeRegistry registry) {
            return mapCodec.apply(registry);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec(TypeRegistry registry) {
            return streamCodec.apply(registry);
        }
    }
}
