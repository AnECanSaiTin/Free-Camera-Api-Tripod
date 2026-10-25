package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 文档级扩展轨道的事件区间 / 周期展开、顺序与预算。
class PlaybackEventExpanderTest {

    private static final Identifier TIMELINE_TYPE =
            Identifier.fromNamespaceAndPath("test", "timeline");
    private static final Identifier PLAIN_TYPE =
            Identifier.fromNamespaceAndPath("test", "plain");
    private static final EventBudget LARGE_BUDGET = new EventBudget(100, 100);

    private final PlaybackEventExpander expander = new PlaybackEventExpander();

    @Test
    void expandsLinearIntervalIncludingZeroAndEndButNotStart() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 2f, 5f, 6f));

        EventExpansion fromZero = expander.expand(snapshot, 0f, 5f, false, 10f, LARGE_BUDGET);

        assertTrue(fromZero.succeeded());
        assertNull(fromZero.failure());
        assertEquals(1, fromZero.intervalsUsed());
        assertEquals(List.of(event("a", 0f), event("a", 2f), event("a", 5f)),
                fromZero.events());

        EventExpansion fromMiddle = expander.expand(snapshot, 2f, 5f, false, 10f, LARGE_BUDGET);

        assertTrue(fromMiddle.succeeded());
        assertEquals(List.of(event("a", 5f)), fromMiddle.events(),
                "区间左端是开区间：恰在 fromTime 的键不应重复触发");
    }

    @Test
    void clampsLinearRangeToPlayableDuration() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 5f, 10f, 12f));

        EventExpansion result = expander.expand(snapshot, 0f, 20f, false, 10f, LARGE_BUDGET);

        assertTrue(result.succeeded());
        assertEquals(1, result.intervalsUsed());
        assertEquals(List.of(event("a", 0f), event("a", 5f), event("a", 10f)),
                result.events());
    }

    @Test
    void ordersSameInstantByDocumentTrackOrder() {
        // 文档顺序是 b、a；同一时刻必须先 b 后 a，不能按测试构造或时间相同的偶然顺序。
        AnimationSnapshot snapshot = snapshot(
                track("b", 0f, 1f),
                track("a", 0f, 1f));

        EventExpansion result = expander.expand(snapshot, 0f, 1f, false, 5f, LARGE_BUDGET);

        assertTrue(result.succeeded());
        assertEquals(List.of(
                event("b", 0f), event("a", 0f),
                event("b", 1f), event("a", 1f)), result.events());
    }

    @Test
    void loopStartAtZeroIncludesZeroTimeOnceWithinFirstPeriod() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 5f));

        EventExpansion result = expander.expand(snapshot, 0f, 5f, true, 10f, LARGE_BUDGET);

        assertTrue(result.succeeded());
        assertEquals(1, result.intervalsUsed());
        assertEquals(List.of(event("a", 0f), event("a", 5f)), result.events());
    }

    @Test
    void zeroRangeAtZeroIncludesZeroTimeOnce() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f));

        EventExpansion linear = expander.expand(snapshot, 0f, 0f, false, 10f, LARGE_BUDGET);
        EventExpansion looping = expander.expand(snapshot, 0f, 0f, true, 10f, LARGE_BUDGET);

        assertTrue(linear.succeeded());
        assertEquals(0, linear.intervalsUsed());
        assertEquals(List.of(event("a", 0f)), linear.events());

        assertTrue(looping.succeeded());
        assertEquals(0, looping.intervalsUsed());
        assertEquals(List.of(event("a", 0f)), looping.events());
    }

    @Test
    void expandsAcrossLoopCycleInProgressionOrder() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 2f, 8f, 10f));

        EventExpansion result = expander.expand(snapshot, 8f, 2f, true, 10f, LARGE_BUDGET);

        assertTrue(result.succeeded());
        assertEquals(2, result.intervalsUsed());
        // (8, 10] → 新周期零时刻 → (0, 2]；8 本身不再触发。
        assertEquals(List.of(event("a", 10f), event("a", 0f), event("a", 2f)),
                result.events());
    }

    @Test
    void zeroTimeEventIsIncludedOnceWhenWrappingToExactlyZero() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 9f, 9.5f));

        EventExpansion result = expander.expand(snapshot, 9f, 0f, true, 10f, LARGE_BUDGET);

        assertTrue(result.succeeded());
        assertEquals(1, result.intervalsUsed());
        assertEquals(List.of(event("a", 9.5f), event("a", 0f)), result.events());
    }

    @Test
    void expandsMultipleCyclesForUnwrappedEndpoint() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 5f, 10f));

        EventExpansion result = expander.expand(snapshot, 8f, 28f, true, 10f, LARGE_BUDGET);

        assertTrue(result.succeeded());
        assertEquals(3, result.intervalsUsed());
        assertEquals(List.of(
                event("a", 10f),
                event("a", 0f), event("a", 5f), event("a", 10f),
                event("a", 0f), event("a", 5f)), result.events());
    }

    @Test
    void rejectsIntervalBudgetExceededWithoutPartialEvents() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 9f));

        EventExpansion result = expander.expand(
                snapshot, 8f, 2f, true, 10f, new EventBudget(10, 1));

        assertFalse(result.succeeded());
        assertTrue(result.overBudget());
        assertEquals(PlaybackFailure.Reason.EVENT_BUDGET_EXCEEDED, result.failure().reason());
        assertEquals(1, result.intervalsUsed());
        assertTrue(result.events().isEmpty(), "超预算时不得返回部分事件");
    }

    @Test
    void rejectsEventBudgetExceededWithoutPartialEvents() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 1f, 2f));

        EventExpansion result = expander.expand(
                snapshot, 0f, 2f, false, 10f, new EventBudget(2, 10));

        assertFalse(result.succeeded());
        assertTrue(result.overBudget());
        assertEquals(PlaybackFailure.Reason.EVENT_BUDGET_EXCEEDED, result.failure().reason());
        assertEquals(1, result.intervalsUsed());
        assertTrue(result.events().isEmpty(), "超预算时不得返回部分事件");
    }

    @Test
    void acceptsEventCountExactlyOnBudget() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 1f));

        EventExpansion result = expander.expand(
                snapshot, 0f, 1f, false, 10f, new EventBudget(2, 1));

        assertTrue(result.succeeded());
        assertEquals(List.of(event("a", 0f), event("a", 1f)), result.events());
    }

    @Test
    void skipsExtensionTrackWithoutTimelineCapability() {
        AnimationSnapshot snapshot = snapshot(
                new PlainExtensionTrack(new TrackId("plain")),
                track("a", 0f, 1f));

        EventExpansion result = expander.expand(snapshot, 0f, 1f, false, 5f, LARGE_BUDGET);

        assertTrue(result.succeeded());
        assertEquals(List.of(event("a", 0f), event("a", 1f)), result.events());
    }

    @Test
    void emptyDocumentAndEmptyTimelineProduceNoEvents() {
        EventExpansion noTracks = expander.expand(snapshot(), 0f, 5f, false, 10f, LARGE_BUDGET);

        assertTrue(noTracks.succeeded());
        assertEquals(1, noTracks.intervalsUsed());
        assertTrue(noTracks.events().isEmpty());

        EventExpansion emptyTimeline = expander.expand(
                snapshot(track("a")), 0f, 5f, false, 10f, LARGE_BUDGET);

        assertTrue(emptyTimeline.succeeded());
        assertEquals(1, emptyTimeline.intervalsUsed());
        assertTrue(emptyTimeline.events().isEmpty());
    }

    @Test
    void zeroLengthAnimationAcceptsOnlyZeroTime() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f));

        EventExpansion result = expander.expand(snapshot, 0f, 0f, false, 0f, LARGE_BUDGET);

        assertTrue(result.succeeded());
        assertEquals(0, result.intervalsUsed());
        assertEquals(List.of(event("a", 0f)), result.events());

        assertThrows(IllegalArgumentException.class,
                () -> expander.expand(snapshot, 0f, 1f, false, 0f, LARGE_BUDGET));
    }

    @Test
    void emptyRangeProducesNoEvents() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f, 5f));

        EventExpansion linear = expander.expand(snapshot, 5f, 5f, false, 10f, LARGE_BUDGET);
        EventExpansion looping = expander.expand(snapshot, 5f, 5f, true, 10f, LARGE_BUDGET);

        assertTrue(linear.succeeded());
        assertEquals(0, linear.intervalsUsed());
        assertTrue(linear.events().isEmpty());

        assertTrue(looping.succeeded());
        assertEquals(0, looping.intervalsUsed());
        assertTrue(looping.events().isEmpty());
    }

    @Test
    void trackReadFailureBecomesPlaybackFailure() {
        AnimationSnapshot snapshot = snapshot(new ThrowingTimelineTrack(new TrackId("bad")));

        EventExpansion result = expander.expand(snapshot, 0f, 5f, false, 10f, LARGE_BUDGET);

        assertFalse(result.succeeded());
        assertFalse(result.overBudget());
        assertEquals(PlaybackFailure.Reason.EVALUATION_ERROR, result.failure().reason());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void rejectsIllegalParameters() {
        AnimationSnapshot snapshot = snapshot(track("a", 0f));

        assertThrows(IllegalArgumentException.class,
                () -> expander.expand(snapshot, Float.NaN, 1f, false, 10f, LARGE_BUDGET));
        assertThrows(IllegalArgumentException.class,
                () -> expander.expand(snapshot, 0f, Float.POSITIVE_INFINITY, false, 10f,
                        LARGE_BUDGET));
        assertThrows(IllegalArgumentException.class,
                () -> expander.expand(snapshot, 0f, 1f, false, -1f, LARGE_BUDGET));
        assertThrows(IllegalArgumentException.class,
                () -> expander.expand(snapshot, 5f, 3f, false, 10f, LARGE_BUDGET));
        assertThrows(IllegalArgumentException.class,
                () -> expander.expand(snapshot, 1f, 0f, true, -1f, LARGE_BUDGET));
    }

    // region 测试替身

    private static AnimationSnapshot snapshot(AnimationTrack.ExtensionTrack... tracks) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withTracks(List.of(tracks));
        return new AnimationSnapshot(new Revision(1), document);
    }

    private static TestTimelineTrack track(String id, float... times) {
        List<ExtensionTimelineTrack.TimelineKey> keys = new ArrayList<>();

        for (int i = 0; i < times.length; i++) {
            keys.add(new ExtensionTimelineTrack.TimelineKey(
                    new KeyId(id + "-key-" + i), times[i]));
        }

        return new TestTimelineTrack(new TrackId(id), id, List.copyOf(keys));
    }

    private static PreparedFrame.Event event(String trackId, float time) {
        return new PreparedFrame.Event(new TrackId(trackId), time);
    }

    private record TestTimelineTrack(TrackId id, String name,
                                     List<ExtensionTimelineTrack.TimelineKey> keys)
            implements ExtensionTimelineTrack {

        @Override
        public List<TimelineKey> timelineKeys() {
            return keys;
        }

        @Override
        public TestTimelineTrack withName(String newName) {
            return new TestTimelineTrack(id, newName, keys);
        }

        @Override
        public Identifier type() {
            return TIMELINE_TYPE;
        }

        @Override
        public float duration() {
            return keys.isEmpty() ? 0f : keys.getLast().time();
        }

        @Override
        public int keyCount() {
            return keys.size();
        }
    }

    private record PlainExtensionTrack(TrackId id) implements AnimationTrack.ExtensionTrack {

        @Override
        public PlainExtensionTrack withName(String newName) {
            return this;
        }

        @Override
        public String name() {
            return id.value();
        }

        @Override
        public Identifier type() {
            return PLAIN_TYPE;
        }

        @Override
        public float duration() {
            return 0f;
        }

        @Override
        public int keyCount() {
            return 0;
        }
    }

    private static final class ThrowingTimelineTrack implements ExtensionTimelineTrack {

        private final TrackId id;

        private ThrowingTimelineTrack(TrackId id) {
            this.id = id;
        }

        @Override
        public TrackId id() {
            return id;
        }

        @Override
        public String name() {
            return id.value();
        }

        @Override
        public Identifier type() {
            return TIMELINE_TYPE;
        }

        @Override
        public float duration() {
            return 0f;
        }

        @Override
        public int keyCount() {
            return 0;
        }

        @Override
        public ThrowingTimelineTrack withName(String newName) {
            return this;
        }

        @Override
        public List<TimelineKey> timelineKeys() {
            throw new IllegalStateException("boom");
        }
    }

    // endregion
}
