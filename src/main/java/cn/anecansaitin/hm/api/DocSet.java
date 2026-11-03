package cn.anecansaitin.hm.api;

import cn.anecansaitin.hm.api.animation.DocTypes;
import cn.anecansaitin.hm.api.animation.registry.DocType;
import cn.anecansaitin.hm.api.animation.registry.TypeRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;

import java.util.*;

/// 不可变文档对象集合
///
/// 要求文档对象的 id 必须唯一，且每个文档对象均为深不可变。
@NullMarked
public class DocSet<T extends IdDocObject> implements DocObject{
    private final List<T> list;
    private final Map<DocId, T> map;
    private final int hash;

    public DocSet(Collection<T> documentObjects) {
        HashMap<DocId, T> map = new HashMap<>();

        for (T t : documentObjects) {
            T old = map.put(t.id(), t);

            if (old != null) {
                throw new IllegalArgumentException("Duplicate document object id: " + t.id());
            }
        }

        list = List.copyOf(documentObjects);
        this.map = Map.copyOf(map);
        this.hash = hash();
    }

    public DocSet(Map<DocId, T> documentObjects) {
        this.list = List.copyOf(documentObjects.values());
        this.map = Map.copyOf(documentObjects);
        this.hash = hash();
    }

    private int hash() {
        return Objects.hash(list);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DocSet<?> that)) return false;
        return Objects.equals(list, that.list);
    }

    public T get(DocId id) {
        return map.get(id);
    }

    public T get(int index) {
        return list.get(index);
    }

    public int size() {
        return list.size();
    }

    public boolean contains(DocId id) {
        return map.containsKey(id);
    }

    public boolean contains(T documentObject) {
        return map.containsValue(documentObject);
    }

    @Override
    public DocType<?> type() {
        return DocTypes.DOC_SET;
    }

    public static Codec<DocSet<?>> codec(TypeRegistry registry) {
        return mapCodec(registry).codec();
    }

    public static MapCodec<DocSet<?>> mapCodec(TypeRegistry registry) {
        return Codec.list(IdDocObject.codec(registry))
                .<DocSet<?>>comapFlatMap(
                        list -> {
                            try {
                                return DataResult.success(new DocSet<>(list));
                            } catch (IllegalArgumentException e) {
                                return DataResult.error(e::getMessage);
                            }
                        },
                        set -> List.copyOf(set.list))
                .fieldOf("values");
    }

    public static StreamCodec<RegistryFriendlyByteBuf, DocSet<?>> streamCodec(TypeRegistry registry) {
        return StreamCodec.composite(
                ByteBufCodecs.list().apply(IdDocObject.streamCodec(registry)),
                set -> List.copyOf(set.list),
                list -> new DocSet<IdDocObject>(list)
        );
    }
}
