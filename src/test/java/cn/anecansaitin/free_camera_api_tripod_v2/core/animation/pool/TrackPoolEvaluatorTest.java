package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.EvaluatedRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
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

/// 轨道池属性求值：默认关闭、曲线插值、状态/四元数/Euler 与缺失引用。
class TrackPoolEvaluatorTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void disabledPropertiesAreAbsent() {
        EvaluatedRig evaluated = new TrackPoolEvaluator().evaluate(
                document(List.of(), RigProperties.disabled()), 0f, Revision.INITIAL).getFirst();

        assertTrue(evaluated.x().status() == cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SampleStatus.ABSENT);
        assertTrue(evaluated.rotation().status() == cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SampleStatus.ABSENT);
        assertFalse(evaluated.output().enabled());
    }

    @Test
    void numericTrackInterpolatesAndActiveTracksStep() {
        TrackId numberId = new TrackId("number");
        TrackId activeId = new TrackId("active");
        CurveTrack number = CurveTrack.ofCurve(numberId, "number", new Curve(List.of(
                key("a", 0f, 0f, Curve.Interpolation.LINEAR),
                key("b", 2f, 10f, Curve.Interpolation.LINEAR)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));
        ActiveTrack active = new ActiveTrack(activeId, "active", false, List.of(
                new ValueKeyframe<>(new KeyId("on"), 1f, true)));
        RigProperties properties = new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Track(numberId)),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                new ActiveProperty(PropertyBinding.enabled(new BooleanSource.Track(activeId))),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());

        EvaluatedRig evaluated = new TrackPoolEvaluator().evaluate(
                document(List.of(number, active), properties), 1f, Revision.INITIAL).getFirst();

        assertEquals(5f, evaluated.x().value(), 1e-6);
        assertTrue(evaluated.active().isValue());
        assertTrue(evaluated.active().value());
    }

    @Test
    void quaternionAndEulerSourcesEvaluate() {
        TrackId rotationId = new TrackId("rotation");
        QuaternionTrack rotation = new QuaternionTrack(rotationId, "rotation", Quaternion.IDENTITY,
                List.of(new ValueKeyframe<>(new KeyId("a"), 0f, Quaternion.IDENTITY),
                        new ValueKeyframe<>(new KeyId("b"), 2f,
                                new Quaternion(0, Math.sin(Math.PI / 4), 0, Math.cos(Math.PI / 4)))));
        RigProperties properties = new RigProperties(
                PositionProperty.disabled(),
                new RotationProperty(PropertyBinding.enabled(new QuaternionSource.Track(rotationId))),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());

        Quaternion middle = new TrackPoolEvaluator().evaluate(
                document(List.of(rotation), properties), 1f, Revision.INITIAL).getFirst()
                .rotation().value();
        assertEquals(Math.sin(Math.PI / 8), middle.y(), 1e-9);

        RigProperties eulerProperties = new RigProperties(
                PositionProperty.disabled(),
                new RotationProperty(PropertyBinding.enabled(new QuaternionSource.EulerComposite(
                        new NumericSource.Constant(90),
                        new NumericSource.Constant(0),
                        new NumericSource.Constant(0),
                        QuaternionSource.RotationOrder.YXZ,
                        QuaternionSource.AngleUnit.DEGREES))),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());
        Quaternion yaw = new TrackPoolEvaluator().evaluate(
                document(List.of(), eulerProperties), 0f, Revision.INITIAL).getFirst()
                .rotation().value();

        assertEquals(Math.sin(Math.PI / 4), yaw.y(), 1e-9);
        assertEquals(Math.cos(Math.PI / 4), yaw.w(), 1e-9);
    }

    @Test
    void numericFormulaAndFormulaKeysEvaluate() {
        RigProperties formulaProperties = new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Formula("t * 2 + 1")),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());
        EvaluatedRig formula = new TrackPoolEvaluator().evaluate(
                document(List.of(), formulaProperties), 3f, Revision.INITIAL).getFirst();
        assertEquals(7f, formula.x().value(), 1e-6);

        TrackId keyedId = new TrackId("keyed");
        CurveTrack keyed = CurveTrack.ofCurve(keyedId, "keyed", new Curve(List.of(
                new Keyframe(new KeyId("a"), 0f,
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula("t * 10"),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR),
                new Keyframe(new KeyId("b"), 2f, new Constant(20f),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));
        RigProperties keyProperties = new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Track(keyedId)),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());
        EvaluatedRig keyedResult = new TrackPoolEvaluator().evaluate(
                document(List.of(keyed), keyProperties), 1f, Revision.INITIAL).getFirst();
        assertEquals(15f, keyedResult.x().value(), 1e-6);
    }

    @Test
    void curveWrapIsAppliedBeforeInterpolation() {
        TrackId numberId = new TrackId("wrapped");
        CurveTrack wrapped = CurveTrack.ofCurve(numberId, "wrapped", new Curve(List.of(
                key("a", 0f, 0f, Curve.Interpolation.LINEAR),
                key("b", 2f, 10f, Curve.Interpolation.LINEAR)),
                Curve.Wrap.LOOP, Curve.Wrap.LOOP));
        RigProperties properties = new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Track(numberId)),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());

        EvaluatedRig evaluated = new TrackPoolEvaluator().evaluate(
                document(List.of(wrapped), properties), 3f, Revision.INITIAL).getFirst();

        assertEquals(5f, evaluated.x().value(), 1e-6);
    }

    @Test
    void variablesCustomFunctionsAndVariableTrackRefEvaluate() {
        TrackId curveId = new TrackId("curve-value");
        CurveTrack curve = CurveTrack.ofCurve(curveId, "curve-value",
                Curve.of(new Keyframe(new KeyId("k"), 0f, new Constant(5f),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)));
        Symbols symbols = new Symbols(
                List.of(Variable.constant("constant", 3f),
                        new Variable("curveValue",
                                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef(curveId))),
                List.of(new CustomFunction("double", List.of("x"), "x + x")));
        RigProperties properties = new RigProperties(
                PositionProperty.disabled(),
                RotationProperty.disabled(),
                new FovProperty(PropertyBinding.enabled(new NumericSource.Formula(
                        "constant + curveValue + double(2)"))),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(curve),
                List.of(PooledRig.create(RIG, "rig").withProperties(properties)),
                PropertyBinding.disabled(), symbols, List.of(), List.of(), List.of(), List.of(),
                PlaybackRange.AUTO);

        EvaluatedRig evaluated = new TrackPoolEvaluator().evaluate(
                document, 0f, Revision.INITIAL).getFirst();

        assertEquals(3f + 5f + 4f, evaluated.fov().value(), 1e-6);
    }

    @Test
    void formulaKeysUseDocumentTimeNotWrappedTime() {
        TrackId numberId = new TrackId("formula-wrapped");
        CurveTrack curve = CurveTrack.ofCurve(numberId, "formula-wrapped", new Curve(List.of(
                new Keyframe(new KeyId("a"), 0f,
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula("t"),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR),
                key("b", 2f, 10f, Curve.Interpolation.LINEAR)),
                Curve.Wrap.LOOP, Curve.Wrap.LOOP));
        RigProperties properties = new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Track(numberId)),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());

        EvaluatedRig evaluated = new TrackPoolEvaluator().evaluate(
                document(List.of(curve), properties), 3f, Revision.INITIAL).getFirst();

        assertEquals(6.5f, evaluated.x().value(), 1e-6);
    }

    @Test
    void progressAndWorldTimeInputsReachPropertyFormulas() {
        RigProperties properties = new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Formula("p + wt")),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());

        EvaluatedRig evaluated = new TrackPoolEvaluator().evaluate(
                document(List.of(), properties),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs(
                        4f, 0.25, 10.0),
                Revision.INITIAL).getFirst();

        assertEquals(10.25f, evaluated.x().value(), 1e-6);
    }

    @Test
    void unsupportedFormulaSymbolIsAnError() {
        RigProperties properties = new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Formula("missing + 1")),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());

        EvaluatedRig evaluated = new TrackPoolEvaluator().evaluate(
                document(List.of(), properties), 0f, Revision.INITIAL).getFirst();

        assertTrue(evaluated.x().isError());
    }

    @Test
    void missingTrackAndCameraSourceAreReported() {
        TrackId missing = new TrackId("missing");
        RigProperties properties = new RigProperties(
                new PositionProperty(
                        PropertyBinding.enabled(new NumericSource.Track(missing)),
                        PropertyBinding.disabled(),
                        PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty.disabled());
        EvaluatedRig evaluated = new TrackPoolEvaluator().evaluate(
                document(List.of(), properties), 0f, Revision.INITIAL).getFirst();

        assertTrue(evaluated.x().isError());
        assertTrue(evaluated.x().diagnostic() != null);

        EvaluatedRig.EvaluatedValue<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource> source =
                new TrackPoolEvaluator().evaluateCameraSource(
                        new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc",
                                List.of(), List.of(PooledRig.create(RIG, "rig")),
                                PropertyBinding.enabled(new CameraSourceProperty.Track(missing)),
                                Symbols.EMPTY, List.of(), List.of(), List.of(), List.of(),
                                PlaybackRange.AUTO), 0f, Revision.INITIAL);
        assertTrue(source.isError());
    }

    private static Keyframe key(String id, float time, float value, Curve.Interpolation interpolation) {
        return new Keyframe(new KeyId(id), time, new Constant(value),
                0f, 0f, 0f, 0f, interpolation);
    }

    private static TrackPoolAnimation document(List<AnimationTrack> tracks, RigProperties properties) {
        PooledRig rig = PooledRig.create(RIG, "rig").withProperties(properties);
        return new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", tracks,
                List.of(rig), PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(),
                List.of(), List.of(), PlaybackRange.AUTO);
    }
}
