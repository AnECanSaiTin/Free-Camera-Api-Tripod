package cn.anecansaitin.hm.api.animation.registry;

import cn.anecansaitin.hm.api.DocObject;
import net.minecraft.resources.Identifier;

public record DocType<T extends DocObject>(
        Identifier id,
        DocTypeCodec<T> codec) {
}
