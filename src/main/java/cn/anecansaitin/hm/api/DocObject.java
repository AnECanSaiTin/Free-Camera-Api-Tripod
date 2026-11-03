package cn.anecansaitin.hm.api;

import cn.anecansaitin.hm.api.animation.registry.DocType;
import cn.anecansaitin.hm.api.animation.registry.DocTypeRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface DocObject {
    DocType<? extends DocObject> type();

    static MapCodec<DocObject> mapCodec(DocTypeRegistry registry) {
        return DocType.mapCodec(registry).dispatchMap(
                DocObject::type,
                type -> type.codec().mapCodec(registry)
        );
    }

    static Codec<DocObject> codec(DocTypeRegistry registry) {
        return mapCodec(registry).codec();
    }

    static StreamCodec<RegistryFriendlyByteBuf, DocObject> streamCodec(DocTypeRegistry registry) {
        return DocType.streamCodec(registry).dispatch(
                DocObject::type,
                type -> type.codec().streamCodec(registry)
        );
    }
}
