package cn.anecansaitin.hm.api.animation.block;

import cn.anecansaitin.hm.api.DocSet;

public record Animation(
        int dataVersion,
        String name,
        DocSet<?> tracks,
        Object symbols,
        PlaybackRange playbackRange,
        DocSet<?> rigs
        ) {

}
