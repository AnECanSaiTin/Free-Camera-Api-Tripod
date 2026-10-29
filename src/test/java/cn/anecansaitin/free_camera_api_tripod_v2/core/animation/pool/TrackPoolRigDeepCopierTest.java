package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.IdGenerator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 机位深复制：新 TrackId、新 KeyId、绑定重写与源不变。
class TrackPoolRigDeepCopierTest {

    @Test
    void deepCopyCreatesIndependentTracksAndBindings() {
        TrackId originalId = new TrackId("number");
        CurveTrack original = CurveTrack.ofCurve(originalId, "number", Curve.of(
                new Keyframe(new KeyId("key"), 0f, new Constant(1f),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)));
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(original),
                List.of(PooledRig.create(new RigId("a"), "A").withProperties(properties(originalId))),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
        AtomicInteger next = new AtomicInteger();

        TrackPoolAnimation copied = new TrackPoolRigDeepCopier().copyDeep(
                document, new RigId("a"), new RigId("b"), "B", new SequentialIds(next));

        assertEquals(2, copied.rigs().size());
        assertEquals(2, copied.tracks().size());
        CurveTrack copiedTrack = (CurveTrack) copied.tracks().get(1);
        assertNotEquals(originalId, copiedTrack.id());
        assertNotEquals(new KeyId("key"), copiedTrack.curve().keys().getFirst().keyId());

        NumericSource.Track binding = (NumericSource.Track) ((PropertyBinding.Enabled<NumericSource>)
                copied.rig(new RigId("b")).orElseThrow().properties().position().x()).source();
        assertEquals(copiedTrack.id(), binding.trackId());
        assertTrue(((PropertyBinding.Enabled<NumericSource>)
                document.rig(new RigId("a")).orElseThrow().properties().position().x()).source()
                instanceof NumericSource.Track originalBinding
                && originalBinding.trackId().equals(originalId));
    }

    private static RigProperties properties(TrackId number) {
        return new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Track(number)),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                ObstacleProperty.disabled());
    }

    private static final class SequentialIds implements IdGenerator {
        private final AtomicInteger next;

        private SequentialIds(AtomicInteger next) {
            this.next = next;
        }

        @Override
        public String nextId(Class<? extends cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.DocumentObjectId> type) {
            return type.getSimpleName().toLowerCase() + "-" + next.incrementAndGet();
        }
    }
}
