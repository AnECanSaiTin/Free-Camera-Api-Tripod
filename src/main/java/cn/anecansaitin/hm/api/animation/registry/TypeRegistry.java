package cn.anecansaitin.hm.api.animation.registry;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public interface TypeRegistry {
    void register(DocType<?> type);
    @Nullable
    DocType<?> get(Identifier id);
    @Nullable
    DocType<?> get(int id);
    int getId(DocType<?> type);
    Identifier getKey(DocType<?> type);
}
