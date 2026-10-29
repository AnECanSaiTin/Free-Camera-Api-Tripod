package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEditResult;
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

/// 轨道池编辑器会话：共享复制、使用次数与撤销。
class TrackPoolEditorSessionTest {

    @Test
    void deepCopySubmitsThroughSession() {
        TrackId curve = new TrackId("curve");
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc",
                List.of(CurveTrack.ofCurve(curve, "curve", Curve.EMPTY)),
                List.of(PooledRig.create(new RigId("a"), "A").withProperties(new RigProperties(
                        new PositionProperty(
                                PropertyBinding.enabled(new NumericSource.Track(curve)),
                                PropertyBinding.disabled(),
                                PropertyBinding.disabled()),
                        RotationProperty.disabled(),
                        FovProperty.disabled(),
                        ActiveProperty.disabled(),
                        RectProperty.fullScreen(),
                        ObstacleProperty.disabled()))),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
        TrackPoolEditorSession session = new TrackPoolEditorSession(
                new TrackPoolDocumentStore(document));

        TrackPoolEditResult result = session.copyRigDeep(new RigId("a"), new RigId("b"), "B",
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.IdGenerator.random());

        assertTrue(result instanceof TrackPoolEditResult.Applied);
        assertEquals(2, session.snapshot().document().tracks().size());
        assertEquals(2, session.snapshot().document().rigs().size());
    }

    @Test
    void documentNameCanBeChanged() {
        TrackPoolEditorSession session = new TrackPoolEditorSession(new TrackPoolDocumentStore(
                TrackPoolAnimation.create("old", PooledRig.create(new RigId("rig"), "rig"))));

        assertTrue(session.renameDocument("new") instanceof TrackPoolEditResult.Applied);
        assertEquals("new", session.snapshot().document().name());
    }

    @Test
    void propertyUpdateSubmitsThroughSession() {
        TrackPoolEditorSession session = new TrackPoolEditorSession(new TrackPoolDocumentStore(
                TrackPoolAnimation.create("doc", PooledRig.create(new RigId("a"), "A"))));

        TrackPoolEditResult result = session.updateRigProperties(new RigId("a"), properties ->
                new RigProperties(
                        new PositionProperty(
                                PropertyBinding.enabled(new NumericSource.Constant(3)),
                                properties.position().y(),
                                properties.position().z()),
                        properties.rotation(),
                        properties.fov(),
                        properties.active(),
                        properties.rect(),
                        properties.obstacle()));

        assertTrue(result instanceof TrackPoolEditResult.Applied);
        assertTrue(session.snapshot().document().rig(new RigId("a")).orElseThrow()
                .properties().position().x().enabled());
    }

    @Test
    void copySharedSubmitsAtomicallyAndExposesUsage() {
        TrackId number = new TrackId("number");
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc",
                List.of(CurveTrack.ofCurve(number, "number", Curve.EMPTY)),
                List.of(PooledRig.create(new RigId("a"), "A").withProperties(new RigProperties(
                        new PositionProperty(
                                PropertyBinding.enabled(new NumericSource.Track(number)),
                                PropertyBinding.disabled(),
                                PropertyBinding.disabled()),
                        RotationProperty.disabled(),
                        FovProperty.disabled(),
                        ActiveProperty.disabled(),
                        RectProperty.fullScreen(),
                        ObstacleProperty.disabled()))),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
        TrackPoolEditorSession session = new TrackPoolEditorSession(
                new TrackPoolDocumentStore(document));

        TrackPoolEditResult result = session.copyRigShared(new RigId("a"),
                new RigId("b"), "B");

        assertTrue(result instanceof TrackPoolEditResult.Applied);
        assertEquals(2, session.snapshot().document().rigs().size());
        assertEquals(1, session.snapshot().revision().value());
        assertEquals(2, session.trackUseCount(number));
        assertEquals(2, session.deleteImpact(number).size());

        assertTrue(session.undo());
        assertEquals(1, session.snapshot().document().rigs().size());
        assertEquals(2, session.snapshot().revision().value());
    }
}
