package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.EvaluatedRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback.ObstacleResolver;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 避障修正：启用、禁用、解析失败。
class TrackPoolObstacleCorrectorTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void enabledObstacleReplacesPosition() {
        AtomicReference<ObstacleResolver.Request> request = new AtomicReference<>();
        RigPose pose = pose();
        EvaluatedRig evaluated = evaluated(ChannelSample.value(2f),
                EvaluatedRig.EvaluatedValue.value(true));

        RigPose corrected = new TrackPoolObstacleCorrector().apply(
                pose, evaluated, new Vec3(0, 0, 0), r -> {
                    request.set(r);
                    return new ObstacleResolver.Result.Adjusted(new Vec3(1, 2, 3));
                });

        assertEquals(new Vec3(1, 2, 3), corrected.pose().position());
        assertEquals(pose.pose().rotation(), corrected.pose().rotation());
        assertEquals(new Vec3(5, 6, 7), request.get().expected());
        assertEquals(2.0, request.get().margin());
    }

    @Test
    void disabledObstacleDoesNotCallResolver() {
        RigPose pose = pose();
        EvaluatedRig evaluated = evaluated(ChannelSample.absent(),
                EvaluatedRig.EvaluatedValue.absent());
        RigPose same = new TrackPoolObstacleCorrector().apply(
                pose, evaluated, new Vec3(0, 0, 0), r -> {
                    throw new AssertionError("resolver must not be called");
                });

        assertSame(pose, same);
    }

    @Test
    void unavailableResolverIsAnError() {
        EvaluatedRig evaluated = evaluated(ChannelSample.value(1f),
                EvaluatedRig.EvaluatedValue.value(true));

        assertThrows(IllegalArgumentException.class, () -> new TrackPoolObstacleCorrector().apply(
                pose(), evaluated, new Vec3(0, 0, 0),
                r -> new ObstacleResolver.Result.Unavailable("no collision world")));
    }

    private static RigPose pose() {
        return new RigPose(RIG, new WorldPose(new Vec3(5, 6, 7), Rotation.NONE, 70f),
                new ResolvedRect.Normalized(0, 0, 1, 1), 0, 1.0, 1);
    }

    private static EvaluatedRig evaluated(ChannelSample margin,
                                          EvaluatedRig.EvaluatedValue<Boolean> enabled) {
        return new EvaluatedRig(RIG, RigOutput.HIDDEN,
                ChannelSample.absent(), ChannelSample.absent(), ChannelSample.absent(),
                EvaluatedRig.EvaluatedValue.absent(), ChannelSample.absent(),
                EvaluatedRig.EvaluatedValue.absent(),
                new EvaluatedRig.EvaluatedRect(EvaluatedRig.EvaluatedRect.Kind.NORMALIZED, null,
                        ChannelSample.absent(), ChannelSample.absent(),
                        ChannelSample.absent(), ChannelSample.absent()),
                enabled, margin);
    }
}
