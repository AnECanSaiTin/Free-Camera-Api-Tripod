package cn.anecansaitin.hm.api.animation.registry;

import cn.anecansaitin.hm.Hm;
import cn.anecansaitin.hm.api.DocId;
import cn.anecansaitin.hm.api.DocObject;
import cn.anecansaitin.hm.api.IdDocObject;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Function;

/// 内置的 [DocSetKey] 与自定义工厂。
public final class DocSetKeys {
    public static final DocSetKey<IdDocObject, DocId> ID = of(id("id"), IdDocObject.class, IdDocObject::id);

    private static final DocSetKey<DocObject, Object> ID_OR_SELF = of(id("id_or_self"), DocObject.class,
                    element -> element instanceof IdDocObject idDoc ? idDoc.id() : element);

    private DocSetKeys() {
    }

    public static <T extends DocObject, K> DocSetKey<T, K> of(
            Identifier id, Class<?> elementType, Function<? super T, ? extends K> keyFunction) {
        return DocSetKey.simple(id, elementType, keyFunction);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Hm.MODID, path);
    }
}
