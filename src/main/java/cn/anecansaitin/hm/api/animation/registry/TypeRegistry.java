package cn.anecansaitin.hm.api.animation.registry;

import net.minecraft.resources.Identifier;

public interface TypeRegistry {
    void register(DocType<?> type);
    DocType<?> get(Identifier id);
    DocType<?> get(int id);
    int getId(DocType<?> type);
    Identifier getKey(DocType<?> type);
}
