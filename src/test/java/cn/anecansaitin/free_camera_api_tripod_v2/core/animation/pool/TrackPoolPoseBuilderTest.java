package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.EvaluatedRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// 属性结果到宿主姿态：默认继承、位置/FOV 覆盖、四元数转 YXZ、矩形回退。
class TrackPoolPoseBuilderTest {

    private static final RigId RIG = new RigId("rig");
    private static final WorldPose BASE = new WorldPose(new Vec3(1, 2, 3),
            new Rotation(0.1, 0.2, 0.3), 70f);
    private static final ResolvedRect DEFAULT_RECT =
            new ResolvedRect.Normalized(0, 0, 1, 1);

    @Test
    void unboundPropertiesInheritBasePoseAndRect() {
        RigPose pose = new TrackPoolPoseBuilder().build(evaluated(
                ChannelSample.absent(), ChannelSample.absent(), ChannelSample.absent(),
                EvaluatedRig.EvaluatedValue.absent(), ChannelSample.absent(),
                new EvaluatedRig.EvaluatedRect(EvaluatedRig.EvaluatedRect.Kind.NORMALIZED, null,
                        ChannelSample.absent(), ChannelSample.absent(),
                        ChannelSample.absent(), ChannelSample.absent())),
                BASE, DEFAULT_RECT);

        assertEquals(BASE.position(), pose.pose().position());
        assertEquals(BASE.rotation(), pose.pose().rotation());
        assertEquals(BASE.fov(), pose.pose().fov());
        assertEquals(DEFAULT_RECT, pose.rect());
    }

    @Test
    void overridesPositionRotationFovAndRect() {
        Quaternion yaw90 = new Quaternion(0, Math.sin(Math.PI / 4), 0, Math.cos(Math.PI / 4));
        RigPose pose = new TrackPoolPoseBuilder().build(evaluated(
                ChannelSample.value(10f), ChannelSample.value(20f), ChannelSample.value(30f),
                EvaluatedRig.EvaluatedValue.value(yaw90), ChannelSample.value(90f),
                new EvaluatedRig.EvaluatedRect(EvaluatedRig.EvaluatedRect.Kind.ANCHORED,
                        RigView.Rect.Corner.TOP_LEFT,
                        ChannelSample.value(1f), ChannelSample.value(2f),
                        ChannelSample.value(3f), ChannelSample.value(4f))),
                BASE, new ResolvedRect.Anchored(RigView.Rect.Corner.TOP_LEFT, 0, 0, 1, 1));

        assertEquals(new Vec3(10, 20, 30), pose.pose().position());
        assertEquals(Math.PI / 2, pose.pose().rotation().yaw(), 1e-9);
        assertEquals(0, pose.pose().rotation().pitch(), 1e-9);
        assertEquals(90f, pose.pose().fov());
        assertEquals(new ResolvedRect.Anchored(RigView.Rect.Corner.TOP_LEFT, 1, 2, 3, 4),
                pose.rect());
    }

    @Test
    void localSpaceComposesAnchorPositionAndRotation() {
        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig rig =
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig(
                        RIG, "rig",
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.Path.EMPTY,
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.MotionMode.COORDINATE,
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.DistanceMode.ABSOLUTE,
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space.Local(
                                null, cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask.ALL),
                        RigOutput.HIDDEN, RigProperties.disabled());
        WorldPose anchor = new WorldPose(new Vec3(10, 0, 0),
                new Rotation(Math.PI / 2, 0, 0), 70f);
        Quaternion yaw90 = new Quaternion(0, Math.sin(Math.PI / 4), 0, Math.cos(Math.PI / 4));
        EvaluatedRig evaluated = evaluated(
                ChannelSample.value(2f), ChannelSample.absent(), ChannelSample.absent(),
                EvaluatedRig.EvaluatedValue.value(yaw90), ChannelSample.absent(),
                new EvaluatedRig.EvaluatedRect(EvaluatedRig.EvaluatedRect.Kind.NORMALIZED, null,
                        ChannelSample.absent(), ChannelSample.absent(),
                        ChannelSample.absent(), ChannelSample.absent()));

        WorldPose baseAtAnchor = new WorldPose(anchor.position(), Rotation.NONE, 70f);
        RigPose pose = new TrackPoolPoseBuilder().build(evaluated, rig, baseAtAnchor, DEFAULT_RECT, anchor);

        assertEquals(10, pose.pose().position().x(), 1e-9);
        assertEquals(0, pose.pose().position().y(), 1e-9);
        assertEquals(-2, pose.pose().position().z(), 1e-9);
        assertEquals(Math.PI, pose.pose().rotation().yaw(), 1e-9);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TrackPoolPoseBuilder().build(evaluated, rig, BASE, DEFAULT_RECT, null));
    }

    @Test
    void mismatchedDefaultRectBranchIsRejected() {
        EvaluatedRig evaluated = evaluated(ChannelSample.absent(), ChannelSample.absent(),
                ChannelSample.absent(), EvaluatedRig.EvaluatedValue.absent(), ChannelSample.absent(),
                new EvaluatedRig.EvaluatedRect(EvaluatedRig.EvaluatedRect.Kind.ANCHORED,
                        RigView.Rect.Corner.TOP_LEFT,
                        ChannelSample.absent(), ChannelSample.absent(),
                        ChannelSample.absent(), ChannelSample.absent()));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TrackPoolPoseBuilder().build(evaluated, BASE, DEFAULT_RECT));
    }

    private static EvaluatedRig evaluated(ChannelSample x,
                                          ChannelSample y,
                                          ChannelSample z,
                                          EvaluatedRig.EvaluatedValue<Quaternion> rotation,
                                          ChannelSample fov,
                                          EvaluatedRig.EvaluatedRect rect) {
        return new EvaluatedRig(RIG, new RigOutput(true, 0, 1.0, 1),
                x, y, z, rotation, fov, EvaluatedRig.EvaluatedValue.absent(), rect,
                EvaluatedRig.EvaluatedValue.absent(), ChannelSample.absent());
    }
}
