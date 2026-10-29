package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 四元数数据与最短路径 SLERP。
class QuaternionTest {

    @Test
    void identityAndNormalization() {
        assertEquals(1.0, Quaternion.IDENTITY.length(), 1e-12);

        Quaternion normalized = new Quaternion(0, 0, 0, 2).normalized();

        assertEquals(0, normalized.x());
        assertEquals(1, normalized.w(), 1e-12);
    }

    @Test
    void slerpHitsEndpoints() {
        Quaternion from = new Quaternion(0, 0, 0, 1);
        Quaternion to = new Quaternion(0, Math.sin(Math.PI / 4), 0, Math.cos(Math.PI / 4));

        assertEquals(from, from.slerp(to, 0));
        Quaternion end = from.slerp(to, 1);
        assertEquals(to.y(), end.y(), 1e-12);
        assertEquals(to.w(), end.w(), 1e-12);
    }

    @Test
    void slerpChoosesShortArcForNegatedTarget() {
        Quaternion from = Quaternion.IDENTITY;
        Quaternion target = new Quaternion(0, 0, 0, -1).normalized();

        Quaternion halfway = from.slerp(target, 0.5);

        assertEquals(1.0, Math.abs(halfway.w()), 1e-12);
    }

    @Test
    void eulerConversionRoundTripsYxz() {
        Rotation euler = new Rotation(0.4, -0.3, 0.2);
        Rotation decoded = Quaternion.fromEuler(euler).toEuler();

        assertEquals(euler.yaw(), decoded.yaw(), 1e-9);
        assertEquals(euler.pitch(), decoded.pitch(), 1e-9);
        assertEquals(euler.roll(), decoded.roll(), 1e-9);
    }

    @Test
    void zeroQuaternionCannotNormalize() {
        assertThrows(IllegalArgumentException.class,
                () -> new Quaternion(0, 0, 0, 0).normalized());
    }

    @Test
    void invalidInterpolationFractionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Quaternion.IDENTITY.slerp(Quaternion.IDENTITY, 1.5));
    }
}
