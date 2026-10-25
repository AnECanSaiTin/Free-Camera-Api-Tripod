package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeRegistry;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/// 不可变快照式类型注册表。
///
/// 注册阶段在同步块内维护可变映射；每次注册后把不可变副本发布到 `volatile` 字段，
/// 冻结后的查询只读该副本。这样解码线程在冻结后不需要加锁，也不会看到半更新状态。
///
/// 重复 ID、冻结后注册与空类型都在此处拒绝；具体注册表的语义由子类固定。
@NullMarked
public class FrozenTypeRegistry<T> implements TypeRegistry<T> {

    private final String kind;
    private final Function<T, Identifier> idOf;
    private final Map<Identifier, T> mutable = new HashMap<>();

    private volatile Map<Identifier, T> published = Map.of();
    private volatile boolean frozen;

    protected FrozenTypeRegistry(String kind, Function<T, Identifier> idOf) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.idOf = Objects.requireNonNull(idOf, "idOf");
    }

    @Override
    public Optional<T> find(Identifier id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(published.get(id));
    }

    @Override
    public Set<Identifier> ids() {
        return published.keySet();
    }

    @Override
    public synchronized void register(T type) {
        Objects.requireNonNull(type, "type");

        if (frozen) {
            throw new IllegalStateException(kind + " registry is frozen");
        }

        Identifier id = Objects.requireNonNull(idOf.apply(type), "type id");

        if (mutable.containsKey(id)) {
            throw new IllegalArgumentException(kind + " type already registered: " + id);
        }

        mutable.put(id, type);
        published = Map.copyOf(mutable);
    }

    @Override
    public synchronized void freeze() {
        if (frozen) {
            return;
        }

        published = Map.copyOf(mutable);
        frozen = true;
    }

    @Override
    public boolean frozen() {
        return frozen;
    }
}
