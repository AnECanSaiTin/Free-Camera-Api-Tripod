package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 扩展轨道纯派生时间轴能力（主设计 §4.1）。
class ExtensionTimelineContractTest {

    @Test
    void timelineKeyValidatesIdentityAndTime() {
        assertTrue(AnimationTrack.ExtensionTrack.class.isAssignableFrom(ExtensionTimelineTrack.class));
        assertThrows(IllegalArgumentException.class,
                () -> new ExtensionTimelineTrack.TimelineKey(null, 0f));
        assertThrows(IllegalArgumentException.class,
                () -> new ExtensionTimelineTrack.TimelineKey(new KeyId("k"), -1f));
        assertThrows(IllegalArgumentException.class,
                () -> new ExtensionTimelineTrack.TimelineKey(new KeyId("k"), Float.NaN));
    }
}
