package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.IdGenerator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/// 单轨道复制：新 TrackId/KeyId，源轨道不变。
class TrackPoolTrackDuplicatorTest {

    @Test
    void duplicatesCurveWithNewIdentity() {
        CurveTrack original = CurveTrack.ofCurve(new TrackId("old"), "curve", Curve.of(
                new Keyframe(new KeyId("key"), 1f, new Constant(3f),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)));
        AtomicInteger next = new AtomicInteger();

        CurveTrack copy = (CurveTrack) new TrackPoolTrackDuplicator().duplicate(
                original, new TrackId("new"), new IdGenerator() {
                    @Override
                    public String nextId(Class<? extends cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.DocumentObjectId> type) {
                        return "generated-" + next.incrementAndGet();
                    }
                });

        assertEquals(new TrackId("new"), copy.id());
        assertNotEquals(original.curve().keys().getFirst().keyId(),
                copy.curve().keys().getFirst().keyId());
        assertEquals(original.curve().keys().getFirst().constantValue(),
                copy.curve().keys().getFirst().constantValue());
        assertEquals(new KeyId("key"), original.curve().keys().getFirst().keyId());
    }
}
