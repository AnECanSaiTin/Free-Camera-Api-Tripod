package cn.anecansaitin.free_camera_api_tripod_v2.core.math;

import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 旋转数学：YXZ 旋转、父子合成、注视角与万向节边界。
class CoreRotationMathTest {

    private static final double EPS = 1e-6;

    @Test
    void identityDoesNotChangeVector() {
        Vec3 rotated = CoreRotationMath.rotate(Rotation.NONE, new Vec3(1, 2, 3));

        assertEquals(1, rotated.x(), EPS);
        assertEquals(2, rotated.y(), EPS);
        assertEquals(3, rotated.z(), EPS);
    }

    @Test
    void yawNinetyDegreesTurnsBackwardIntoNegativeX() {
        Vec3 rotated = CoreRotationMath.rotate(new Rotation(Math.PI / 2, 0, 0),
                new Vec3(0, 0, -1));

        assertEquals(-1, rotated.x(), EPS);
        assertEquals(0, rotated.z(), EPS);
    }

    @Test
    void composesYawAndPitch() {
        Rotation composed = CoreRotationMath.compose(
                new Rotation(Math.PI / 4, 0, 0),
                new Rotation(Math.PI / 4, 0, 0));

        assertEquals(Math.PI / 2, composed.yaw(), EPS);
        assertEquals(0, composed.pitch(), EPS);
        assertEquals(0, composed.roll(), EPS);
    }

    @Test
    void composesParentYawWithChildPitch() {
        Rotation composed = CoreRotationMath.compose(
                new Rotation(Math.PI / 2, 0, 0),
                new Rotation(0, Math.PI / 3, 0));

        assertEquals(Math.PI / 2, composed.yaw(), EPS);
        assertEquals(Math.PI / 3, composed.pitch(), EPS);
    }

    @Test
    void appliesParentRotationToLocalPosition() {
        Vec3 rotated = CoreRotationMath.rotate(new Rotation(Math.PI / 2, 0, 0),
                new Vec3(1, 0, 0));

        assertEquals(0, rotated.x(), EPS);
        assertEquals(-1, rotated.z(), EPS);
    }

    @Test
    void lookRotationMatchesForwardDirection() {
        Rotation forward = CoreRotationMath.lookRotation(new Vec3(0, 0, -1));

        assertEquals(0, forward.yaw(), EPS);
        assertEquals(0, forward.pitch(), EPS);
    }

    @Test
    void lookRotationForPositiveXUsesNegativeYaw() {
        Rotation rotation = CoreRotationMath.lookRotation(new Vec3(1, 0, 0));

        assertEquals(-Math.PI / 2, rotation.yaw(), EPS);
        assertEquals(0, rotation.pitch(), EPS);
    }

    @Test
    void zeroAimDirectionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CoreRotationMath.lookRotation(Vec3.ZERO));
    }

    @Test
    void gimbalPitchStillProducesFiniteAngles() {
        Rotation composed = CoreRotationMath.compose(
                new Rotation(0.3, Math.PI / 2, 0.2),
                new Rotation(0.4, 0, 0.1));

        assertTrue(Double.isFinite(composed.yaw()));
        assertTrue(Double.isFinite(composed.pitch()));
        assertTrue(Double.isFinite(composed.roll()));
    }
}
