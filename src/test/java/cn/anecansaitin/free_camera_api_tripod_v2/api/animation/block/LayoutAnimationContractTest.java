package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 布局连续属性 `AnimatedNumber` 与可动画矩形（主设计 §3.1.2、§3.1）。
///
/// 锁定三件事：
///
/// 1. **两条封闭分支**（值源 / 数值曲线）的静态与动态判定、空曲线显式默认值、
///    非空曲线端点保持（只接受 `CLAMP / CLAMP`）；
/// 2. **静态判定只用于静态检查**：可判定时拒绝非法几何，动态分量留给运行期逐帧检查；
/// 3. **布局曲线计入时长**，静态布局不延长动画（主设计 §3.1）。
class LayoutAnimationContractTest {

    private static TrackId track(String value) {
        return new TrackId(value);
    }

    private static Curve layoutCurveEndingAt(float seconds, float value) {
        Keyframe keyframe = new Keyframe(new KeyId("layout-key"), seconds, new Constant(value),
                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR);

        return Curve.of(keyframe);
    }

    private static RigView.Rect animatedWidthRect(AnimatedNumber width) {
        return new RigView.Rect.Normalized(
                AnimatedNumber.constant(0f),
                AnimatedNumber.constant(0f),
                width,
                AnimatedNumber.constant(1f));
    }

    private static CameraRig rigWith(RigView view) {
        return CameraRig.create(new RigId("rig"), "rig", List.of(),
                        track("aim"), track("active"))
                .withView(view);
    }

    // region AnimatedNumber 分支

    @Test
    void constantSourceIsStaticAndFormulaIsDynamic() {
        AnimatedNumber constant = AnimatedNumber.constant(0.25f);
        AnimatedNumber formula = AnimatedNumber.formula("0.25 + 0");

        assertTrue(constant.isStatic());
        assertEquals(0.25f, constant.staticValue().orElseThrow());
        assertEquals(0f, constant.endTime());

        assertFalse(formula.isStatic());
        assertTrue(formula.staticValue().isEmpty());
        assertEquals(0f, formula.endTime());
    }

    @Test
    void emptyCurveUsesExplicitDefaultAndCountsAsStatic() {
        AnimatedNumber empty = AnimatedNumber.keyed(Curve.EMPTY, 0.5f);

        assertTrue(empty.isKeyed());
        assertTrue(empty.isStatic());
        assertEquals(0.5f, empty.staticValue().orElseThrow());
        assertEquals(0f, empty.endTime());
        assertTrue(empty.asCurve().orElseThrow().isEmpty());
    }

    @Test
    void nonEmptyCurveIsDynamicAndEndsAtLastKey() {
        AnimatedNumber keyed = AnimatedNumber.keyed(layoutCurveEndingAt(25f, 0.2f), 0.5f);

        assertFalse(keyed.isStatic());
        assertTrue(keyed.staticValue().isEmpty());
        assertEquals(25f, keyed.endTime());

        AnimatedNumber.Keyed branch = (AnimatedNumber.Keyed) keyed;

        assertEquals(0.5f, branch.defaultValue());
        assertEquals(25f, branch.curve().last().time());
    }

    @Test
    void keyedRejectsLoopingCurvesAndNonFiniteDefaults() {
        Curve looping = new Curve(
                List.of(new Keyframe(new KeyId("k"), 1f, new Constant(0f), 0f, 0f, 0f, 0f,
                        Curve.Interpolation.LINEAR)),
                Curve.Wrap.LOOP,
                Curve.Wrap.CLAMP);

        assertThrows(IllegalArgumentException.class, () -> AnimatedNumber.keyed(looping, 0f));
        assertThrows(IllegalArgumentException.class,
                () -> AnimatedNumber.keyed(Curve.EMPTY, Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> AnimatedNumber.keyed(Curve.EMPTY, Float.POSITIVE_INFINITY));
    }

    @Test
    void branchDerivationStaysInsideItsBranch() {
        AnimatedNumber.Value value = new AnimatedNumber.Value(new Constant(1f));
        Constant replaced = (Constant) value.withSource(new Constant(2f)).source();

        assertEquals(2f, replaced.value());

        AnimatedNumber.Keyed keyed = new AnimatedNumber.Keyed(Curve.EMPTY, 0.5f);

        assertEquals(0.25f, keyed.withDefaultValue(0.25f).defaultValue());
        assertEquals(9f, keyed.withCurve(layoutCurveEndingAt(9f, 0.1f)).endTime());
    }

    // endregion

    // region 矩形

    @Test
    void staticRectRejectsIllegalGeometryButDynamicWidthIsAllowed() {
        assertThrows(IllegalArgumentException.class,
                () -> animatedWidthRect(AnimatedNumber.constant(0f)));
        assertThrows(IllegalArgumentException.class,
                () -> animatedWidthRect(AnimatedNumber.constant(Float.NaN)));

        // 动态分量无法静态判定，允许构造；运行期逐帧检查（主设计 §3.1.2）。
        RigView.Rect dynamic = animatedWidthRect(AnimatedNumber.formula("0.5"));

        assertFalse(dynamic.isStatic());
        assertEquals(0f, dynamic.endTime());
    }

    @Test
    void rectEndTimeTakesLatestComponentCurve() {
        RigView.Rect rect = new RigView.Rect.Normalized(
                AnimatedNumber.keyed(layoutCurveEndingAt(9f, 0.1f), 0f),
                AnimatedNumber.constant(0f),
                AnimatedNumber.keyed(layoutCurveEndingAt(12f, 0.4f), 0.4f),
                AnimatedNumber.constant(1f));

        assertEquals(12f, rect.endTime());
        assertFalse(rect.isStatic());
        assertTrue(RigView.Rect.full().isStatic());
        assertEquals(0f, RigView.Rect.full().endTime());
        assertTrue(RigView.Rect.full().scalesWithWindow());
    }

    // endregion

    // region 时长

    @Test
    void layoutCurveExtendsRigAndDocumentDurationButStaticLayoutDoesNot() {
        ActiveTrack active = ActiveTrack.enabled(track("active"));
        RigView animated = RigView.full(active).withRect(
                animatedWidthRect(AnimatedNumber.keyed(layoutCurveEndingAt(25f, 0.3f), 0.5f)));

        CameraRig rig = rigWith(animated);

        assertEquals(25f, rig.view().layoutDuration());
        assertEquals(25f, rig.contentDuration());
        assertEquals(25f, CameraAnimation.create("doc", rig, track("source")).duration());

        // 静态布局不延长动画：常量矩形与空曲线都贡献 0。
        CameraRig staticRig = rigWith(RigView.full(active).withRect(
                animatedWidthRect(AnimatedNumber.keyed(Curve.EMPTY, 0.5f))));

        assertEquals(0f, staticRig.view().layoutDuration());
        assertEquals(0f, CameraAnimation.create("doc", staticRig, track("source")).duration());
    }

    @Test
    void disabledRigLayoutDoesNotExtendDocumentDuration() {
        ActiveTrack active = ActiveTrack.enabled(track("active"));
        RigView animated = RigView.full(active)
                .withRect(animatedWidthRect(AnimatedNumber.keyed(layoutCurveEndingAt(25f, 0.3f), 0.5f)))
                .withEnabled(false);

        CameraAnimation document = CameraAnimation.create("doc", rigWith(animated), track("source"));

        assertEquals(0f, document.duration());
    }

    // endregion
}
