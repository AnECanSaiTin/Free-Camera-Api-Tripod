package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 四元数轨道：默认值、端点、最短弧 SLERP、键顺序与 Codec。
class QuaternionTrackTest {

    private static final Quaternion YAW_90 =
            new Quaternion(0, Math.sin(Math.PI / 4), 0, Math.cos(Math.PI / 4));

    @Test
    void emptyTrackUsesDefaultValue() {
        QuaternionTrack track = track(Quaternion.IDENTITY, List.of());

        assertEquals(Quaternion.IDENTITY, track.at(5f));
        assertEquals(0, track.keyCount());
    }

    @Test
    void firstKeyBeforeDefaultThenSlerpHitsEndpoints() {
        QuaternionTrack track = track(new Quaternion(0, 0, 0, -1), List.of(
                new ValueKeyframe<>(new KeyId("a"), 1f, Quaternion.IDENTITY),
                new ValueKeyframe<>(new KeyId("b"), 3f, YAW_90)));

        assertEquals(new Quaternion(0, 0, 0, -1), track.at(0f));
        assertEquals(Quaternion.IDENTITY, track.at(1f));
        Quaternion middle = track.at(2f);
        assertEquals(Math.sin(Math.PI / 8), middle.y(), 1e-9);
        assertEquals(YAW_90.w(), track.at(3f).w(), 1e-12);
        assertEquals(YAW_90.w(), track.at(4f).w(), 1e-12);
        assertEquals(3f, track.duration());
    }

    @Test
    void duplicateKeyTimesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> track(Quaternion.IDENTITY, List.of(
                new ValueKeyframe<>(new KeyId("a"), 1f, Quaternion.IDENTITY),
                new ValueKeyframe<>(new KeyId("b"), 1f, YAW_90))));
    }

    @Test
    void codecRoundTrips() {
        QuaternionTrack track = track(YAW_90, List.of(
                new ValueKeyframe<>(new KeyId("a"), 0f, Quaternion.IDENTITY)));

        var encoded = QuaternionTrack.CODEC.encodeStart(JsonOps.INSTANCE, track).result().orElseThrow();
        QuaternionTrack decoded = QuaternionTrack.CODEC.parse(JsonOps.INSTANCE, encoded)
                .result().orElseThrow();

        assertEquals(track, decoded);
    }

    private static QuaternionTrack track(Quaternion defaultValue, List<ValueKeyframe<Quaternion>> keys) {
        return new QuaternionTrack(new TrackId("rotation"), "rotation", defaultValue, keys);
    }
}
