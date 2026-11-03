package cn.anecansaitin.hm.api;

import cn.anecansaitin.hm.api.animation.registry.DocTypes;
import cn.anecansaitin.hm.api.animation.registry.DocSetKey;
import cn.anecansaitin.hm.api.animation.registry.DocSetKeyRegistry;
import cn.anecansaitin.hm.api.animation.registry.DocType;
import cn.anecansaitin.hm.api.animation.registry.DocTypeRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.*;

/// 不可变文档对象集合。
///
/// 元素只要求是 [DocObject]；集合按哪个键去重/查找由注册在
/// [DocSetKeyRegistry] 里的 [DocSetKey] 决定，而不是在集合里存 lambda。
///
/// - `list` 是唯一的内容与顺序来源，`map` 只是索引视图；
/// - 索引键必须唯一，重复键在构造期抛 [IllegalArgumentException]；
/// - `equals`/`hashCode` 由「键策略 + 元素序列」决定（键策略是注册项，有稳定 id）。
@NullMarked
public final class DocSet<T extends DocObject, K> implements DocObject {
    /// 单个 doc_set 的元素上限。
    ///
    /// `ByteBufCodecs.list()` 无参重载等价于 `list(Integer.MAX_VALUE)`，解码端会按对端声明的
    /// 数量分配，必须显式给上限。
    private static final int MAX_ELEMENTS = 4096;

    private final DocSetKey<T, K> key;
    private final List<T> list;
    private final Map<K, T> map;
    private final int hash;

    public DocSet(Collection<T> documentObjects, DocSetKey<T, K> key) {
        this.key = Objects.requireNonNull(key, "key");

        LinkedHashMap<K, T> byKey = new LinkedHashMap<>();
        for (T documentObject : documentObjects) {
            K elementKey = key.keyOf(documentObject);
            if (byKey.putIfAbsent(elementKey, documentObject) != null) {
                throw new IllegalArgumentException("Duplicate document object key: " + elementKey);
            }
        }

        this.list = List.copyOf(byKey.values());
        this.map = Collections.unmodifiableMap(byKey);
        this.hash = Objects.hash(this.key, this.list);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DocSet<?, ?> that
                && Objects.equals(key, that.key)
                && Objects.equals(list, that.list);
    }

    /// 当前的索引键策略。
    public DocSetKey<T, K> key() {
        return key;
    }

    /// 计算某个元素在当前集合里的索引键。
    public K keyOf(T documentObject) {
        return key.keyOf(documentObject);
    }

    public T get(K key) {
        return map.get(key);
    }

    public T get(int index) {
        return list.get(index);
    }

    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public boolean contains(T documentObject) {
        return Objects.equals(map.get(keyOf(documentObject)), documentObject);
    }

    public List<T> items() {
        return list;
    }

    public int size() {
        return list.size();
    }

    @Override
    public DocType<?> type() {
        return DocTypes.DOC_SET;
    }

    // region 通用（DOC_SET）编解码器：键策略 id 写进数据，解码时查 DocSetKeyRegistry

    public static Codec<DocSet<?, ?>> codec(DocTypeRegistry registry) {
        return mapCodec(registry).codec();
    }

    public static MapCodec<DocSet<?, ?>> mapCodec(DocTypeRegistry registry) {
        return RecordCodecBuilder.<Keyed>mapCodec(i -> i.group(
                        Identifier.CODEC.fieldOf("key").forGetter(Keyed::key),
                        Codec.list(DocObject.codec(registry)).fieldOf("values").forGetter(Keyed::values))
                .apply(i, Keyed::new))
                .flatXmap(
                        keyed -> decodeUntyped(registry.keys(), keyed),
                        set -> DataResult.success(new Keyed(set.key().id(), untypedItems(set))));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, DocSet<?, ?>> streamCodec(DocTypeRegistry registry) {
        return StreamCodec.composite(
                keyStreamCodec(registry.keys()), DocSet::key,
                DocObject.streamCodec(registry).apply(ByteBufCodecs.list(MAX_ELEMENTS)), DocSet::untypedItems,
                DocSet::buildOrThrow);
    }

    private record Keyed(Identifier key, List<DocObject> values) {
    }

    private static DataResult<DocSet<?, ?>> decodeUntyped(DocSetKeyRegistry keys, Keyed keyed) {
        DocSetKey<?, ?> key = keys.get(keyed.key());
        if (key == null) {
            return DataResult.error(() -> "Unknown DocSetKey: " + keyed.key());
        }

        DataResult<DocSet<?, ?>> typeCheck = checkElementTypes(key, keyed.values());
        if (typeCheck.isError()) {
            return typeCheck;
        }

        try {
            return DataResult.success(build(key, keyed.values()));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    private static DocSet<?, ?> buildOrThrow(DocSetKey<?, ?> key, List<DocObject> values) {
        checkElementTypesOrThrow(key, values);

        try {
            return build(key, values);
        } catch (IllegalArgumentException e) {
            // StreamCodec 没有 DataResult 错误通道，重复键只能转成 DecoderException
            throw new DecoderException("Invalid doc_set payload: " + e.getMessage(), e);
        }
    }

    private static DataResult<DocSet<?, ?>> checkElementTypes(DocSetKey<?, ?> key, List<DocObject> values) {
        for (DocObject value : values) {
            if (!key.elementType().isInstance(value)) {
                return DataResult.error(() -> "Element " + value.type().id() + " is not a "
                        + key.elementType().getName() + " required by DocSetKey " + key.id());
            }
        }
        return DataResult.success(null);
    }

    private static void checkElementTypesOrThrow(DocSetKey<?, ?> key, List<DocObject> values) {
        for (DocObject value : values) {
            if (!key.elementType().isInstance(value)) {
                throw new DecoderException("Element " + value.type().id() + " is not a "
                        + key.elementType().getName() + " required by DocSetKey " + key.id());
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DocSet<?, ?> build(DocSetKey<?, ?> key, List<DocObject> values) {
        return new DocSet(values, (DocSetKey) key);
    }

    private static StreamCodec<RegistryFriendlyByteBuf, DocSetKey<?, ?>> keyStreamCodec(DocSetKeyRegistry keys) {
        return ByteBufCodecs.<DocSetKey<?, ?>>idMapper(
                id -> {
                    DocSetKey<?, ?> key = keys.get(id);
                    if (key == null) {
                        throw new DecoderException("Unknown DocSetKey id: " + id);
                    }
                    return key;
                },
                keys::getId).cast();
    }

    /// `DocSet<?, ?>` 的元素静态类型是捕获变量，取成 `List<DocObject>` 只是擦除层面的转换。
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<DocObject> untypedItems(DocSet<?, ?> set) {
        return (List) set.list;
    }

    // endregion

    // region 强类型（键策略由调用方固定）的编解码器

    public static <T extends DocObject, K> Codec<DocSet<T, K>> codec(
            Codec<T> elementCodec, DocSetKey<T, K> key) {
        return DocSet.<T, K>mapCodec(elementCodec, key).codec();
    }

    public static <T extends DocObject, K> MapCodec<DocSet<T, K>> mapCodec(
            Codec<T> elementCodec, DocSetKey<T, K> key) {
        return elementCodec.listOf()
                .<DocSet<T, K>>comapFlatMap(
                        list -> {
                            try {
                                return DataResult.success(new DocSet<>(list, key));
                            } catch (IllegalArgumentException e) {
                                return DataResult.error(e::getMessage);
                            }
                        },
                        DocSet::items)
                .fieldOf("values");
    }

    public static <T extends DocObject, K> StreamCodec<RegistryFriendlyByteBuf, DocSet<T, K>> streamCodec(
            StreamCodec<RegistryFriendlyByteBuf, T> elementCodec, DocSetKey<T, K> key) {
        return elementCodec
                .apply(ByteBufCodecs.list(MAX_ELEMENTS))
                .map(list -> {
                    try {
                        return new DocSet<>(list, key);
                    } catch (IllegalArgumentException e) {
                        throw new DecoderException("Invalid doc_set payload: " + e.getMessage(), e);
                    }
                }, DocSet::items);
    }

    // endregion
}
