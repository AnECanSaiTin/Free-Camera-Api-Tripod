package cn.anecansaitin.hm.api.animation.registry;

import cn.anecansaitin.hm.api.DocObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.KeyDispatchCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record DocType<T extends DocObject>(
        Identifier id,
        Class<?> clazz,
        DocTypeCodec<T> codec) {

    public static MapCodec<DocType<?>> mapCodec(TypeRegistry registry) {
        return new KeyDispatchCodec<>(
                Identifier.CODEC.fieldOf("doc_type"),
                type -> DataResult.success(type.id),
                id -> {
                    DocType<?> docType = registry.get(id);
                    return docType == null
                            ? DataResult.error(() -> "DocType not registered. DocType id: " + id)
                            : DataResult.success(MapCodec.unit(docType));
                }
        );
    }

    public static Codec<DocType<?>> codec(TypeRegistry registry) {
        return mapCodec(registry).codec();
    }

    public static StreamCodec<RegistryFriendlyByteBuf, DocType<?>> streamCodec(TypeRegistry registry) {
        return ByteBufCodecs.<DocType<?>>idMapper(
                id -> {
                    DocType<?> docType = registry.get(id);
                    if (docType == null) throw new RuntimeException("No DocType with id " + id);
                    return docType;
                },
                registry::getId
        ).cast();
    }
}
