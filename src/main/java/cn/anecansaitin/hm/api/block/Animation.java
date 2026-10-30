package cn.anecansaitin.hm.api.block;

import cn.anecansaitin.hm.api.DocSet;

public record Animation(
        int dataVersion,
        String name,
        DocSet<?> tracks,
        Object symbols,
        Object playbackRange,
        DocSet<?> rigs
        ) {

}
