package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 运行时组合：播放 + 合成 + 宿主输入。
class TrackPoolRuntimeTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void ticksAndComposesVisiblePose() {
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "rig")
                        .withOutput(new RigOutput(true, 0, 1.0, 1))),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
        TrackPoolRuntime runtime = new TrackPoolRuntime(
                new TrackPoolDocumentStore(document));
        runtime.basePose(RIG, new WorldPose(new Vec3(1, 2, 3), Rotation.NONE, 70f));
        runtime.defaultRect(RIG, new ResolvedRect.Normalized(0, 0, 1, 1));
        runtime.playback().play();

        TrackPoolRuntime.Result result = runtime.tick(1f, 0.0);

        assertTrue(result instanceof TrackPoolRuntime.Result.Prepared);
        assertEquals(1, ((TrackPoolRuntime.Result.Prepared) result).poses().size());
    }

    @Test
    void missingHostInputIsRejected() {
        TrackPoolRuntime runtime = new TrackPoolRuntime(new TrackPoolDocumentStore(
                TrackPoolAnimation.create("doc", PooledRig.create(RIG, "rig")
                        .withOutput(new RigOutput(true, 0, 1.0, 1)))));

        assertTrue(runtime.tick(0f, 0.0) instanceof TrackPoolRuntime.Result.Rejected);
    }

    @Test
    void invalidSamplePortIsNotNull() {
        assertThrows(NullPointerException.class, () -> new TrackPoolRuntime(
                new TrackPoolDocumentStore(TrackPoolAnimation.create(
                        "doc", PooledRig.create(RIG, "rig"))),
                new TrackPoolFrameFactory(), null));
    }
}
