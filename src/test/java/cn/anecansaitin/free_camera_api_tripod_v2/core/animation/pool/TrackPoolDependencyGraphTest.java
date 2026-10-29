package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.QuaternionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池绑定依赖索引。
class TrackPoolDependencyGraphTest {

    @Test
    void collectsRigAndDocumentUsages() {
        TrackId number = new TrackId("number");
        TrackId active = new TrackId("active");
        TrackId rotation = new TrackId("rotation");
        TrackId source = new TrackId("source");
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(
                CurveTrack.ofCurve(number, "number", Curve.EMPTY),
                ActiveTrack.enabled(active),
                new QuaternionTrack(rotation, "rotation", Quaternion.IDENTITY, List.of()),
                CameraSourceTrack.player(source)),
                List.of(PooledRig.create(new RigId("rig"), "rig").withProperties(new RigProperties(
                        new PositionProperty(
                                PropertyBinding.enabled(new NumericSource.Track(number)),
                                PropertyBinding.disabled(),
                                PropertyBinding.disabled()),
                        new RotationProperty(PropertyBinding.enabled(
                                new QuaternionSource.Track(rotation))),
                        FovProperty.disabled(),
                        new ActiveProperty(PropertyBinding.enabled(
                                new BooleanSource.Track(active))),
                        RectProperty.fullScreen(),
                        ObstacleProperty.disabled()))),
                PropertyBinding.enabled(new CameraSourceProperty.Track(source)),
                Symbols.EMPTY, List.of(), List.of(), List.of(), List.of(), PlaybackRange.AUTO);

        TrackPoolDependencyGraph graph = TrackPoolDependencyGraph.build(document);

        assertEquals(1, graph.useCount(number));
        assertEquals(1, graph.useCount(active));
        assertEquals(1, graph.useCount(rotation));
        assertEquals(1, graph.useCount(source));
        assertTrue(graph.usages(source).iterator().next().rigId() == null);
        assertTrue(graph.usages(number).iterator().next().rigId().value().equals("rig"));
        assertTrue(graph.isReferenced(rotation));

        TrackPoolAnimation unbound = TrackPoolAnimation.create("doc", PooledRig.create(new RigId("r"), "r"));
        assertFalse(TrackPoolDependencyGraph.build(unbound).isReferenced(number));
    }
}
