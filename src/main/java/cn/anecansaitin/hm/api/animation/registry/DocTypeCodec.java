package cn.anecansaitin.hm.api.animation.registry;

import cn.anecansaitin.hm.api.DocObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.Function;

public interface DocTypeCodec<T extends DocObject> {
    Codec<T> codec(DocTypeRegistry registry);

    MapCodec<T> mapCodec(DocTypeRegistry registry);

    StreamCodec<RegistryFriendlyByteBuf, T> streamCodec(DocTypeRegistry registry);

    static <R extends DocObject> DocTypeCodec<R> stable(Codec<R> codec, MapCodec<R> mapCodec,
                                                        StreamCodec<RegistryFriendlyByteBuf, R> streamCodec) {
        return new Stable<>(codec, mapCodec, streamCodec);
    }

    static <R extends DocObject> DocTypeCodec<R> flex(Function<DocTypeRegistry, Codec<R>> codec,
                                                      Function<DocTypeRegistry, MapCodec<R>> mapCodec,
                                                      Function<DocTypeRegistry, StreamCodec<RegistryFriendlyByteBuf, R>> streamCodec) {
        return new Flex<>(codec, mapCodec, streamCodec);
    }

    record Stable<T extends DocObject>(Codec<T> codec, MapCodec<T> mapCodec,
                                       StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) implements DocTypeCodec<T> {

        @Override
        public Codec<T> codec(DocTypeRegistry registry) {
            return codec;
        }

        @Override
        public MapCodec<T> mapCodec(DocTypeRegistry registry) {
            return mapCodec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec(DocTypeRegistry registry) {
            return streamCodec;
        }
    }

    record Flex<T extends DocObject>(Function<DocTypeRegistry, Codec<T>> codec,
                                     Function<DocTypeRegistry, MapCodec<T>> mapCodec,
                                     Function<DocTypeRegistry, StreamCodec<RegistryFriendlyByteBuf, T>> streamCodec) implements DocTypeCodec<T> {
        @Override
        public Codec<T> codec(DocTypeRegistry registry) {
            return codec.apply(registry);
        }

        @Override
        public MapCodec<T> mapCodec(DocTypeRegistry registry) {
            return mapCodec.apply(registry);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec(DocTypeRegistry registry) {
            return streamCodec.apply(registry);
        }
    }
}
