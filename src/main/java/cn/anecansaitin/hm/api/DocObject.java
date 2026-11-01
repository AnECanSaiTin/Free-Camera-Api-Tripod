package cn.anecansaitin.hm.api;

import cn.anecansaitin.hm.api.animation.registry.DocType;

public interface DocObject {
    DocType<?> getType();
}
