package cn.anecansaitin.hm.api.animation.block;

import cn.anecansaitin.hm.api.DocSet;

public record Animation(
        int dataVersion,
        String name,
        DocSet<?, ?> tracks,
        Symbols symbols,
        PlaybackRange playbackRange,
        DocSet<?, ?> rigs
        ) {

}
