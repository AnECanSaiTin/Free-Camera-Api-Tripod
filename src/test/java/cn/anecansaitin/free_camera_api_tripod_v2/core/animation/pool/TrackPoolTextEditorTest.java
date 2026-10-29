package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 属性面板文本编辑：数值、布尔、Euler 旋转与非法输入。
class TrackPoolTextEditorTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void editsNumericBooleanAndRotationFields() {
        TrackPoolEditorSession session = session();
        TrackPoolTextEditor editor = new TrackPoolTextEditor(session);

        assertTrue(editor.setNumeric(RIG, TrackPoolTextEditor.NumericField.POSITION_X, "2.5")
                instanceof TrackPoolEditResult.Applied);
        assertTrue(editor.setNumeric(RIG, TrackPoolTextEditor.NumericField.RECT_WIDTH, "0.5")
                instanceof TrackPoolEditResult.Applied);
        assertTrue(editor.setActive(RIG, "true") instanceof TrackPoolEditResult.Applied);
        assertTrue(editor.setRotationEuler(RIG, "90,0,0") instanceof TrackPoolEditResult.Applied);

        var properties = session.snapshot().document().rig(RIG).orElseThrow().properties();
        assertTrue(properties.position().x().enabled());
        assertTrue(properties.active().source().enabled());
        assertTrue(properties.rotation().source().enabled());
        assertTrue(properties.rect() instanceof RectProperty.Normalized normalized
                && normalized.width().enabled());

        var rig = new TrackPoolEvaluator().evaluate(session.snapshot().document(),
                0f, session.snapshot().revision()).getFirst();
        assertEquals(Math.PI / 2, rig.rotation().value().toEuler().yaw(), 1e-9);
    }

    @Test
    void quaternionTextSetsRotation() {
        TrackPoolEditorSession session = session();
        TrackPoolTextEditor editor = new TrackPoolTextEditor(session);

        assertTrue(editor.setRotationQuaternion(RIG, "0,0.7071067811865476,0,0.7071067811865476")
                instanceof TrackPoolEditResult.Applied);

        var rig = new TrackPoolEvaluator().evaluate(session.snapshot().document(),
                0f, session.snapshot().revision()).getFirst();
        assertEquals(Math.PI / 2, rig.rotation().value().toEuler().yaw(), 1e-9);
    }

    @Test
    void formulaTextBindsNumericSource() {
        TrackPoolEditorSession session = session();
        TrackPoolTextEditor editor = new TrackPoolTextEditor(session);

        assertTrue(editor.setNumeric(RIG, TrackPoolTextEditor.NumericField.FOV, "= t * 2")
                instanceof TrackPoolEditResult.Applied);

        var rig = new TrackPoolEvaluator().evaluate(session.snapshot().document(),
                3f, session.snapshot().revision()).getFirst();
        assertEquals(6f, rig.fov().value(), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> editor.setNumeric(
                RIG, TrackPoolTextEditor.NumericField.FOV, "="));
    }

    @Test
    void blankDisablesAndInvalidTextDoesNotPublish() {
        TrackPoolEditorSession session = session();
        TrackPoolTextEditor editor = new TrackPoolTextEditor(session);
        editor.setNumeric(RIG, TrackPoolTextEditor.NumericField.FOV, "70");
        Revision before = session.snapshot().revision();

        editor.setNumeric(RIG, TrackPoolTextEditor.NumericField.FOV, " ");
        assertEquals(PropertyBinding.disabled().enabled(),
                session.snapshot().document().rig(RIG).orElseThrow()
                        .properties().fov().source().enabled());

        assertThrows(IllegalArgumentException.class, () -> editor.setNumeric(
                RIG, TrackPoolTextEditor.NumericField.FOV, "abc"));
        assertEquals(before.next(), session.snapshot().revision());
    }

    private static TrackPoolEditorSession session() {
        return new TrackPoolEditorSession(new TrackPoolDocumentStore(
                TrackPoolAnimation.create("doc", PooledRig.create(RIG, "rig"))));
    }
}
