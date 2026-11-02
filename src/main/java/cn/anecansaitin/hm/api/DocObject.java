package cn.anecansaitin.hm.api;

import cn.anecansaitin.hm.api.animation.registry.DocType;
import cn.anecansaitin.hm.api.animation.registry.TypeRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface DocObject {
    DocType<? extends DocObject> type();

    static MapCodec<DocObject> mapCodec(TypeRegistry registry) {
        return DocType.mapCodec(registry).dispatchMap(
                DocObject::type,
                type -> type.codec().mapCodec(registry)
        );
    }

    static Codec<DocObject> codec(TypeRegistry registry) {
        return mapCodec(registry).codec();
    }

    static StreamCodec<RegistryFriendlyByteBuf, DocObject> streamCodec(TypeRegistry registry) {
        return DocType.streamCodec(registry).dispatch(
               DocObject::type,
               type -> {
                   StreamCodec<? super RegistryFriendlyByteBuf, ?> codec = type.codec().streamCodec(registry);
                   return codec;
               }
        );
    }
}
