package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrameResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSnapshot;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池帧准备：成功出帧与失败整帧拒绝。
class TrackPoolFrameFactoryTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void preparesFrameWithPlaybackRange() {
        TrackPoolAnimation document = document(new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Constant(1.5)),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                ObstacleProperty.disabled()));
        TrackPoolFrameResult result = new TrackPoolFrameFactory().prepare(
                new TrackPoolSnapshot(Revision.INITIAL, document), 2f);

        assertTrue(result instanceof TrackPoolFrameResult.Prepared);
        TrackPoolFrameResult.Prepared prepared = (TrackPoolFrameResult.Prepared) result;
        assertEquals(1, prepared.frame().rigs().size());
        assertEquals(1.5f, prepared.frame().rigs().getFirst().x().value(), 1e-6);
        assertEquals(0f, prepared.frame().playbackStart());
        assertEquals(0f, prepared.frame().playbackEnd());
    }

    @Test
    void cameraSourceRigMustBeActive() {
        TrackPoolAnimation inactive = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "rig")),
                PropertyBinding.enabled(new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty.Constant(
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource.rig(RIG))),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols.EMPTY,
                List.of(), List.of(), List.of(), List.of(), PlaybackRange.AUTO);
        assertTrue(new TrackPoolFrameFactory().prepare(
                new TrackPoolSnapshot(Revision.INITIAL, inactive), 0f)
                instanceof TrackPoolFrameResult.Rejected);

        PropertyBinding<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty> binding =
                PropertyBinding.enabled(
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty.Constant(
                                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource.rig(RIG)));
        TrackPoolAnimation active = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "rig").withProperties(new RigProperties(
                        PositionProperty.disabled(), RotationProperty.disabled(), FovProperty.disabled(),
                        new ActiveProperty(PropertyBinding.enabled(
                                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource.Constant(true))),
                        RectProperty.fullScreen(), ObstacleProperty.disabled()))),
                binding, cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols.EMPTY,
                List.of(), List.of(), List.of(), List.of(), PlaybackRange.AUTO);
        assertTrue(new TrackPoolFrameFactory().prepare(
                new TrackPoolSnapshot(Revision.INITIAL, active), 0f)
                instanceof TrackPoolFrameResult.Prepared);
    }

    @Test
    void anyFailedPropertyRejectsWholeFrame() {
        TrackPoolAnimation document = document(new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Track(new TrackId("missing"))),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                ObstacleProperty.disabled()));
        TrackPoolFrameResult result = new TrackPoolFrameFactory().prepare(
                new TrackPoolSnapshot(Revision.INITIAL, document), 0f);

        assertTrue(result instanceof TrackPoolFrameResult.Rejected);
        TrackPoolFrameResult.Rejected rejected = (TrackPoolFrameResult.Rejected) result;
        assertTrue(!rejected.diagnostics().isEmpty());
    }

    private static TrackPoolAnimation document(RigProperties properties) {
        return new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "rig").withProperties(properties)),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
    }
}
