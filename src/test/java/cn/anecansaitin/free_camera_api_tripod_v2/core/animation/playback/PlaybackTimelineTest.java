package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 时间推进、循环与定位的纯口径。
class PlaybackTimelineTest {

    @Test
    void advancesByScaledDeltaAndClampsAtEnd() {
        PlaybackTimeline timeline = PlaybackTimeline.start(10f, 2f, false, 0f);

        PlaybackTimeline advanced = timeline.advance(3f);
        assertEquals(6f, advanced.time());
        assertFalse(advanced.finished());

        PlaybackTimeline clamped = advanced.advance(10f);
        assertEquals(10f, clamped.time());
        assertTrue(clamped.finished());
        assertEquals(10f, clamped.advance(5f).time());
    }

    @Test
    void loopsAroundDuration() {
        PlaybackTimeline timeline = PlaybackTimeline.start(10f, 1f, true, 8f);

        assertEquals(2f, timeline.advance(4f).time());
        assertEquals(0f, timeline.advance(2f).time());
        assertFalse(timeline.advance(20f).finished());
    }

    @Test
    void loopKeepsUnwrappedAdvanceEndpointForEventExpansion() {
        PlaybackTimeline timeline = PlaybackTimeline.start(10f, 1f, true, 2f);

        PlaybackTimeline advanced = timeline.advance(20f);
        assertEquals(2f, advanced.time());
        assertEquals(22f, advanced.advancedTime());

        PlaybackTimeline again = advanced.advance(3f);
        assertEquals(5f, again.time());
        assertEquals(25f, again.advancedTime());

        // 定位重置未取模终点，避免把定位前的推进量带入下一帧区间。
        assertEquals(3f, advanced.seek(3f).advancedTime());
    }

    @Test
    void nonLinearClampAlsoKeepsAdvancedEndpointAtDuration() {
        PlaybackTimeline timeline = PlaybackTimeline.start(10f, 1f, false, 2f);

        PlaybackTimeline clamped = timeline.advance(20f);
        assertEquals(10f, clamped.time());
        assertEquals(10f, clamped.advancedTime());
        assertEquals(10f, clamped.advance(5f).advancedTime());
    }

    @Test
    void zeroDurationStaysAtZero() {
        PlaybackTimeline timeline = PlaybackTimeline.start(0f, 1f, false, 0f);

        assertSame(timeline, timeline.advance(5f));
        assertEquals(0f, timeline.time());
        assertTrue(timeline.finished());
    }

    @Test
    void seekStaysInsidePlayableRange() {
        PlaybackTimeline timeline = PlaybackTimeline.start(10f, 1f, false, 0f);

        assertEquals(4.5f, timeline.seek(4.5f).time());
        assertThrows(IllegalArgumentException.class, () -> timeline.seek(10.1f));
        assertThrows(IllegalArgumentException.class, () -> timeline.seek(-1f));
        assertThrows(IllegalArgumentException.class, () -> timeline.seek(Float.NaN));
    }

    @Test
    void rejectsIllegalStartAndDelta() {
        assertThrows(IllegalArgumentException.class,
                () -> PlaybackTimeline.start(0f, 1f, false, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> PlaybackTimeline.start(5f, 1f, false, 6f));

        PlaybackTimeline timeline = PlaybackTimeline.start(5f, 1f, false, 0f);
        assertThrows(IllegalArgumentException.class, () -> timeline.advance(-1f));
        assertThrows(IllegalArgumentException.class, () -> timeline.advance(Float.NaN));
    }
}
