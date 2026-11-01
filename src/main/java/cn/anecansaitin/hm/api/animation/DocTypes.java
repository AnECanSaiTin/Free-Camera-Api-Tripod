package cn.anecansaitin.hm.api.animation;

import cn.anecansaitin.hm.api.DocSet;
import cn.anecansaitin.hm.api.animation.block.PlaybackRange;
import cn.anecansaitin.hm.api.animation.registry.DocType;
import cn.anecansaitin.hm.api.animation.registry.DocTypeCodec;
import net.minecraft.resources.Identifier;

public final class DocTypes {
    public static final String MODID = "free_camera_api_tripod";
    public static final DocType<DocSet<?>> DOC_SET;
    public static final DocType<PlaybackRange.Auto> PLAYBACK_RANGE_AUTO;
    public static final DocType<PlaybackRange.Manual> PLAYBACK_RANGE_MANUAL;

    static {
        DOC_SET = new DocType<>(Identifier.fromNamespaceAndPath(MODID, "doc_set"), );
        PLAYBACK_RANGE_AUTO = new DocType<>(Identifier.fromNamespaceAndPath(MODID, "playback_range_auto"), DocTypeCodec.stable(PlaybackRange.Auto.CODEC, PlaybackRange.Auto.MAP_CODEC, PlaybackRange.Auto.STREAM_CODEC));
        PLAYBACK_RANGE_MANUAL = new DocType<>(Identifier.fromNamespaceAndPath(MODID, "playback_range_manual"), DocTypeCodec.stable(PlaybackRange.Manual.CODEC, PlaybackRange.Manual.MAP_CODEC, PlaybackRange.Manual.STREAM_CODEC));
    }
}
