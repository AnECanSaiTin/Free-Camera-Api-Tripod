package cn.anecansaitin.hm.api;

import cn.anecansaitin.hm.api.animation.registry.DocType;
import cn.anecansaitin.hm.api.animation.registry.TypeRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.KeyDispatchCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface IdDocObject extends DocObject {
    DocId id();

    @SuppressWarnings("unchecked")
    static MapCodec<IdDocObject> mapCodec(TypeRegistry registry) {
        return new KeyDispatchCodec<>(
                DocType.mapCodec(registry),
                obj -> DataResult.success(obj.type()),
                type -> {
                    if (!IdDocObject.class.isAssignableFrom(type.clazz())) {
                        return DataResult.error(() -> "Type " + type.clazz() + " does not implement IdDocObject");
                    }

                    MapCodec<IdDocObject> mapCodec = (MapCodec<IdDocObject>) type.codec().mapCodec(registry);
                    return DataResult.success(mapCodec);
                }
        );
    }

    static Codec<IdDocObject> codec(TypeRegistry registry) {
        return mapCodec(registry).codec();
    }

    @SuppressWarnings("unchecked")
    static StreamCodec<RegistryFriendlyByteBuf, IdDocObject> streamCodec(TypeRegistry registry) {
        return DocType.streamCodec(registry).dispatch(
                IdDocObject::type,
                type -> {
                    if (!IdDocObject.class.isAssignableFrom(type.clazz())) {
                        throw new IllegalArgumentException("Type " + type.clazz() + " does not implement IdDocObject");
                    }

                    return (StreamCodec<RegistryFriendlyByteBuf, IdDocObject>)type.codec().streamCodec(registry);
                }
        );
    }
}
