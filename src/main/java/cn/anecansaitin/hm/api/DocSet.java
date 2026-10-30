package cn.anecansaitin.hm.api;

import org.jspecify.annotations.NullMarked;

import java.util.*;

/// 不可变文档对象集合
///
/// 要求文档对象的 id 必须唯一，且每个文档对象均为深不可变。
@NullMarked
public class DocSet<T extends IdentityDocObject> {
    private final List<T> list;
    private final Map<DocObjectId, T> map;
    private final int hash;

    public DocSet(Collection<T> documentObjects) {
        HashMap<DocObjectId, T> map = new HashMap<>();

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

    public DocSet(Map<DocObjectId, T> documentObjects) {
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

    public T get(DocObjectId id) {
        return map.get(id);
    }

    public T get(int index) {
        return list.get(index);
    }

    public int size() {
        return list.size();
    }

    public boolean contains(DocObjectId id) {
        return map.containsKey(id);
    }

    public boolean contains(T documentObject) {
        return map.containsValue(documentObject);
    }
}
