package cn.anecansaitin.hm.api.animation.registry;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public interface Registry<T> {
    void register(T type);
    @Nullable
    T get(Identifier id);
    @Nullable
    T get(int id);
    int getId(T type);
    Identifier getKey(T type);
}
