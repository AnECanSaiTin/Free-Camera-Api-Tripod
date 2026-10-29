package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 机位复制默认共享轨道。
class TrackPoolRigCopierTest {

    @Test
    void copySharesTrackBindingsAndKeepsIndependentRigIdentity() {
        TrackId number = new TrackId("number");
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc",
                List.of(CurveTrack.ofCurve(number, "number", Curve.EMPTY)),
                List.of(PooledRig.create(new RigId("a"), "A").withProperties(properties(number))),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);

        TrackPoolAnimation copied = new TrackPoolRigCopier().copyShared(
                document, new RigId("a"), new RigId("b"), "B");

        assertEquals(2, copied.rigs().size());
        assertEquals(document.tracks(), copied.tracks());
        assertEquals(number, ((NumericSource.Track) ((PropertyBinding.Enabled<NumericSource>)
                copied.rig(new RigId("b")).orElseThrow().properties().position().x()).source()).trackId());
        assertThrows(IllegalArgumentException.class, () -> new TrackPoolRigCopier().copyShared(
                copied, new RigId("a"), new RigId("b"), "again"));
    }

    private static RigProperties properties(TrackId number) {
        return new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Track(number)),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                ObstacleProperty.disabled());
    }
}
