package cn.anecansaitin.hm.api.animation.registry;

import cn.anecansaitin.hm.api.DocObject;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/// 集合的索引键策略（注册项）。
///
/// 一个 [cn.anecansaitin.hm.api.DocSet] 用哪个键去重/查找，由这里注册的条目决定，
/// 而不是在集合里存一个 lambda：注册项有稳定 id，可以写进数据、可以比较、可以跨文档复用。
///
/// @param <T> 元素类型
/// @param <K> 键类型
public interface DocSetKey<T extends DocObject, K> {

    /// 注册 id（同时也是数据里的 `"key"` 字段值）。
    Identifier id();

    /// 该策略接受的元素类型；解码时会用 `elementType().isInstance(element)` 校验。
    Class<?> elementType();

    /// 从一个元素取出索引键。
    K keyOf(T element);

    static <T extends DocObject, K> DocSetKey<T, K> simple(Identifier id, Class<?> elementType, Function<? super T, ? extends K> keyFunction) {
        return new Simple<>(id, elementType, keyFunction);
    }

    record Simple<T extends DocObject, K>(
            Identifier id,
            Class<?> elementType,
            Function<? super T, ? extends K> keyFunction) implements DocSetKey<T, K> {

        @Override
        public K keyOf(T element) {
            return keyFunction.apply(element);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DocSetKey<?, ?> other && id.equals(other.id());
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "DocSetKey[" + id + "]";
        }
    }
}
