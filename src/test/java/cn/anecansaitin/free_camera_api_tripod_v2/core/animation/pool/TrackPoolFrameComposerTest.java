package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrame;
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
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 帧合成：属性结果 + 静态配置 + 基础姿态 → RigPose。
class TrackPoolFrameComposerTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void composesPreparedFrame() {
        TrackPoolAnimation document = document();
        TrackPoolFrameResult.Prepared prepared = (TrackPoolFrameResult.Prepared)
                new TrackPoolFrameFactory().prepare(
                        new TrackPoolSnapshot(Revision.INITIAL, document), 0f);
        TrackPoolFrame frame = prepared.frame();

        List<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose> poses =
                new TrackPoolFrameComposer().compose(frame,
                        Map.of(RIG, document.rigs().getFirst()),
                        Map.of(RIG, new WorldPose(new Vec3(1, 2, 3), Rotation.NONE, 70f)),
                        Map.of(RIG, new ResolvedRect.Normalized(0, 0, 1, 1)),
                        Map.of(), null);

        assertEquals(1, poses.size());
        assertEquals(new Vec3(5, 2, 3), poses.getFirst().pose().position());
    }

    @Test
    void visibleCompositionSkipsDefaultHiddenRig() {
        TrackPoolAnimation document = document();
        TrackPoolFrame frame = ((TrackPoolFrameResult.Prepared)
                new TrackPoolFrameFactory().prepare(
                        new TrackPoolSnapshot(Revision.INITIAL, document), 0f)).frame();
        TrackPoolFrameComposer composer = new TrackPoolFrameComposer();

        assertTrue(composer.composeVisible(frame,
                Map.of(RIG, document.rigs().getFirst()),
                Map.of(RIG, new WorldPose(new Vec3(1, 2, 3), Rotation.NONE, 70f)),
                Map.of(RIG, new ResolvedRect.Normalized(0, 0, 1, 1)),
                Map.of(), null).isEmpty());

        TrackPoolAnimation visible = document.withRig(document.rigs().getFirst()
                .withOutput(new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput(
                        true, 0, 1.0, 1)));
        TrackPoolFrame visibleFrame = ((TrackPoolFrameResult.Prepared)
                new TrackPoolFrameFactory().prepare(
                        new TrackPoolSnapshot(Revision.INITIAL, visible), 0f)).frame();
        assertEquals(1, composer.composeVisible(visibleFrame,
                Map.of(RIG, visible.rigs().getFirst()),
                Map.of(RIG, new WorldPose(new Vec3(1, 2, 3), Rotation.NONE, 70f)),
                Map.of(RIG, new ResolvedRect.Normalized(0, 0, 1, 1)),
                Map.of(), null).size());
    }

    @Test
    void missingBasePoseIsRejected() {
        TrackPoolFrameResult.Prepared prepared = (TrackPoolFrameResult.Prepared)
                new TrackPoolFrameFactory().prepare(
                        new TrackPoolSnapshot(Revision.INITIAL, document()), 0f);

        assertThrows(IllegalArgumentException.class, () -> new TrackPoolFrameComposer().compose(
                prepared.frame(), Map.of(RIG, document().rigs().getFirst()),
                Map.of(), Map.of(RIG, new ResolvedRect.Normalized(0, 0, 1, 1)),
                Map.of(), null));
    }

    private static TrackPoolAnimation document() {
        RigProperties properties = new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Constant(5)),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                ObstacleProperty.disabled());
        return new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "rig").withProperties(properties)),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
    }
}
