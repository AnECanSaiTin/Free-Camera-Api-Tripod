package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrameResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池播放控制器：推进、循环、暂停与定位。
class TrackPoolPlaybackControllerTest {

    @Test
    void tickProvidesProgressAndWorldTimeToFormulas() {
        var properties = new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties(
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty.disabled(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty.disabled(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty(
                        PropertyBinding.enabled(new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource.Formula("p + wt"))),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty.disabled(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(new RigId("rig"), "rig").withProperties(properties)),
                PropertyBinding.disabled(), Symbols.EMPTY,
                List.of(Marker.at(new MarkerId("end"), 10f)), List.of(), List.of(), List.of(),
                PlaybackRange.AUTO);
        TrackPoolPlaybackController controller = new TrackPoolPlaybackController(
                new TrackPoolDocumentStore(document));
        controller.play();

        TrackPoolFrameResult result = controller.tick(5f, 5.0);

        assertEquals(5.5, ((TrackPoolFrameResult.Prepared) result).frame()
                .rigs().getFirst().fov().value(), 1e-6);
    }

    @Test
    void advancesLoopsAndPauses() {
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(new RigId("rig"), "rig")), PropertyBinding.disabled(),
                Symbols.EMPTY, List.of(Marker.at(new MarkerId("end"), 5f)), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
        TrackPoolPlaybackController controller = new TrackPoolPlaybackController(
                new TrackPoolDocumentStore(document));

        controller.play();
        TrackPoolFrameResult first = controller.tick(2f);
        assertTrue(first instanceof TrackPoolFrameResult.Prepared);
        assertEquals(2f, controller.time());

        controller.tick(4f);
        assertEquals(1f, controller.time());

        controller.pause();
        controller.tick(2f);
        assertEquals(1f, controller.time());
        assertFalse(controller.playing());

        controller.seek(-3f);
        assertEquals(0f, controller.time());
    }
}
