package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampleRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SampleStatus;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackLookup;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 常量曲线采样器的求值口径：三态、wrap、插值与公式拒绝。
///
/// 上下文使用真实快照与帧事实，只以最小假 [TrackLookup] / [TrackIndex] 提供待采样轨道；
/// 常量轨道不读取任何空间事实，因此不需要伪造世界或机位读数。
class ConstantCurveSamplerTest {

    private static final Identifier DIMENSION =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Revision REVISION = new Revision(7);
    private static final ConstantCurveSampler SAMPLER = new ConstantCurveSampler();
    private static final float EPSILON = 1.0e-5f;

    @Test
    void emptyCurveIsAbsent() {
        ChannelSample sample = sample(track(Curve.EMPTY), 0f);

        assertEquals(SampleStatus.ABSENT, sample.status());
        assertEquals(0f, sample.value());
        assertNull(sample.diagnostic());
    }

    @Test
    void missingTrackIsErrorWithDocumentScopeAndLocation() {
        CurveTrack track = track(Curve.EMPTY);
        EvaluationContext context = context(track);
        TrackId missing = new TrackId("missing");

        ChannelSample sample = SAMPLER.sample(new CurveSampleRequest(
                context, EvaluationSession.create(64L, 4), missing, null, 1f));

        assertTrue(sample.isError());

        Diagnostic diagnostic = sample.diagnostic();
        assertNotNull(diagnostic);
        assertTrue(diagnostic.scope() instanceof DiagnosticScope.Document);
        assertEquals(REVISION, ((DiagnosticScope.Document) diagnostic.scope()).revision());

        Diagnostic.Location location = diagnostic.location();
        assertNotNull(location);
        assertEquals(missing, location.objectId());
        assertFalse(location.fieldPath().isBlank());
    }

    @Test
    void clampUsesFirstAndLastEndpoints() {
        CurveTrack track = track(curve(Curve.Wrap.CLAMP, Curve.Wrap.CLAMP,
                constantKey("a", 1f, 10f, Curve.Interpolation.LINEAR),
                constantKey("b", 3f, 20f, Curve.Interpolation.LINEAR)));

        assertEquals(10f, value(track, 0f), EPSILON);
        assertEquals(10f, value(track, 1f), EPSILON);
        assertEquals(20f, value(track, 3f), EPSILON);
        assertEquals(20f, value(track, 4f), EPSILON);
    }

    @Test
    void stepUsesLeftValueWithinSegment() {
        CurveTrack track = track(curve(Curve.Wrap.CLAMP, Curve.Wrap.CLAMP,
                constantKey("a", 1f, 10f, Curve.Interpolation.STEP),
                constantKey("b", 3f, 20f, Curve.Interpolation.STEP)));

        assertEquals(10f, value(track, 1f), EPSILON);
        assertEquals(10f, value(track, 2f), EPSILON);
        assertEquals(10f, value(track, 2.999f), EPSILON);
    }

    @Test
    void linearInterpolatesMidpoint() {
        CurveTrack track = track(curve(Curve.Wrap.CLAMP, Curve.Wrap.CLAMP,
                constantKey("a", 1f, 10f, Curve.Interpolation.LINEAR),
                constantKey("b", 3f, 20f, Curve.Interpolation.LINEAR)));

        assertEquals(15f, value(track, 2f), EPSILON);
        assertEquals(12.5f, value(track, 1.5f), EPSILON);
    }

    @Test
    void hermiteUsesBothSlopesAndHandleLengths() {
        CurveTrack track = track(curve(Curve.Wrap.CLAMP, Curve.Wrap.CLAMP,
                constantKey("a", 1f, 0f, Curve.Interpolation.HERMITE, 0f, 0f, 1f, 1f),
                constantKey("b", 3f, 10f, Curve.Interpolation.HERMITE, 0f, 0f, 1f, 1f)));

        // 两侧斜率都为 0 时，Hermite 在本段退化为 smoothstep：u=0.5 → 0.5，u=0.25 → 0.15625。
        assertEquals(5f, value(track, 2f), EPSILON);
        assertEquals(1.5625f, value(track, 1.5f), EPSILON);
    }

    @Test
    void loopWrapsBySegmentDuration() {
        CurveTrack track = track(curve(Curve.Wrap.LOOP, Curve.Wrap.LOOP,
                constantKey("a", 1f, 10f, Curve.Interpolation.LINEAR),
                constantKey("b", 3f, 20f, Curve.Interpolation.LINEAR)));

        assertEquals(15f, value(track, 0f), EPSILON);  // 回绕到 2s
        assertEquals(15f, value(track, 4f), EPSILON);  // 回绕到 2s
        assertEquals(10f, value(track, 5f), EPSILON);  // 回绕到 1s
    }

    @Test
    void pingPongReflectsByTwiceSegmentDuration() {
        CurveTrack track = track(curve(Curve.Wrap.PING_PONG, Curve.Wrap.PING_PONG,
                constantKey("a", 1f, 10f, Curve.Interpolation.LINEAR),
                constantKey("b", 3f, 20f, Curve.Interpolation.LINEAR)));

        assertEquals(15f, value(track, 0f), EPSILON);  // 反射到 2s
        assertEquals(15f, value(track, 4f), EPSILON);  // 反射到 2s
        assertEquals(10f, value(track, 5f), EPSILON);  // 周期起点 1s
    }

    @Test
    void singleKeyCurveClampsToThatKey() {
        CurveTrack track = track(Curve.of(
                constantKey("only", 2f, 42f, Curve.Interpolation.LINEAR)));

        assertEquals(42f, value(track, 0f), EPSILON);
        assertEquals(42f, value(track, 2f), EPSILON);
        assertEquals(42f, value(track, 100f), EPSILON);
    }

    @Test
    void formulaEndpointsAreErrorsAndNeverFallBackToTheOtherEndpoint() {
        CurveTrack leftFormula = track(curve(Curve.Wrap.CLAMP, Curve.Wrap.CLAMP,
                formulaKey("f", 1f, "t + 1", Curve.Interpolation.LINEAR),
                constantKey("c", 3f, 10f, Curve.Interpolation.LINEAR)));

        assertFormulaError(sample(leftFormula, 2f));

        CurveTrack rightFormula = track(curve(Curve.Wrap.CLAMP, Curve.Wrap.CLAMP,
                constantKey("c", 1f, 10f, Curve.Interpolation.LINEAR),
                formulaKey("f", 3f, "t + 1", Curve.Interpolation.LINEAR)));

        assertFormulaError(sample(rightFormula, 2f));
    }

    @Test
    void nonFiniteResultIsError() {
        CurveTrack track = track(curve(Curve.Wrap.CLAMP, Curve.Wrap.CLAMP,
                constantKey("bad", 1f, Float.NaN, Curve.Interpolation.LINEAR),
                constantKey("b", 3f, 10f, Curve.Interpolation.LINEAR)));

        ChannelSample sample = sample(track, 2f);

        assertTrue(sample.isError());
        assertNotNull(sample.diagnostic());
        assertTrue(messageOf(sample).contains("non-finite"), messageOf(sample));
    }

    @Test
    void interpolationHelperClampsHandlesAndHandlesZeroSpan() {
        assertEquals(1.5d, CurveInterpolation.clampHandleLength(100d), 1.0e-9d);
        assertEquals(0d, CurveInterpolation.clampHandleLength(-100d), 1.0e-9d);

        // 曲柄长度超限与恰好夹到上限必须得到同一结果。
        double clamped = CurveInterpolation.hermite(0.4d, 2d, 0d, 10d, 3d, -2d, 1.5d, 0d);
        double oversized = CurveInterpolation.hermite(0.4d, 2d, 0d, 10d, 3d, -2d, 100d, -100d);
        assertEquals(clamped, oversized, 1.0e-9d);

        // 段长为 0 按 CLAMP 处理：任何一侧都返回唯一时间。
        assertEquals(2d, CurveInterpolation.wrapTime(0d, 2d, 2d, Curve.Wrap.LOOP, Curve.Wrap.LOOP),
                1.0e-9d);

        // PING_PONG 的反射：首键 1s、末键 3s 时，0s 反射到 2s，-1s 反射到 3s。
        assertEquals(2d, CurveInterpolation.wrapTime(0d, 1d, 3d,
                Curve.Wrap.PING_PONG, Curve.Wrap.CLAMP), 1.0e-9d);
        assertEquals(3d, CurveInterpolation.wrapTime(-1d, 1d, 3d,
                Curve.Wrap.PING_PONG, Curve.Wrap.CLAMP), 1.0e-9d);
    }

    private static void assertFormulaError(ChannelSample sample) {
        assertTrue(sample.isError());
        assertNotNull(sample.diagnostic());

        Diagnostic diagnostic = sample.diagnostic();
        assertTrue(diagnostic.scope() instanceof DiagnosticScope.Document);
        assertEquals(REVISION, ((DiagnosticScope.Document) diagnostic.scope()).revision());

        Diagnostic.Location location = diagnostic.location();
        assertNotNull(location);
        assertEquals(new TrackId("track"), location.objectId());
        assertFalse(location.fieldPath().isBlank());

        String message = messageOf(sample);
        assertTrue(message.contains("expression evaluator"), message);
        assertTrue(message.contains("not implemented"), message);
    }

    private static String messageOf(ChannelSample sample) {
        Diagnostic diagnostic = sample.diagnostic();

        if (diagnostic != null && diagnostic.message() instanceof LocalizedMessage.Literal literal) {
            return literal.text();
        }

        return "";
    }

    private static float value(CurveTrack track, float time) {
        ChannelSample sample = sample(track, time);

        assertEquals(SampleStatus.VALUE, sample.status());
        return sample.value();
    }

    private static ChannelSample sample(CurveTrack track, float time) {
        CurveSampleRequest request = new CurveSampleRequest(context(track),
                EvaluationSession.create(1024L, 8), track.id(), null, time);

        return SAMPLER.sample(request);
    }

    private static EvaluationContext context(CurveTrack track) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(track),
                new TrackId("aim"), new TrackId("active"));
        AnimationSnapshot snapshot = new AnimationSnapshot(REVISION,
                CameraAnimation.create("doc", rig, new TrackId("source")));

        TrackLookup lookup = trackId ->
                trackId.equals(track.id()) ? Optional.of(track) : Optional.empty();

        TrackIndex index = new TrackIndex() {
            @Override
            public AnimationSnapshot snapshot() {
                return snapshot;
            }

            @Override
            public Optional<Entry> find(TrackId trackId) {
                return Optional.empty();
            }

            @Override
            public List<Diagnostic> diagnostics() {
                return List.of();
            }
        };

        FrameFacts facts = new FrameFacts(snapshot, DIMENSION, 1L, 2L, 0.5f, 1L,
                Map.of(), Map.of(), true);

        return EvaluationContext.bind(11L, snapshot, facts, lookup, index);
    }

    private static CurveTrack track(Curve curve) {
        return CurveTrack.ofCurve(new TrackId("track"), "track", curve);
    }

    private static Curve curve(Curve.Wrap preMode, Curve.Wrap postMode, Keyframe... keys) {
        return new Curve(List.of(keys), preMode, postMode);
    }

    private static Keyframe constantKey(String id,
                                        float time,
                                        float value,
                                        Curve.Interpolation interpolation) {
        return constantKey(id, time, value, interpolation, 0f, 0f, 1f, 1f);
    }

    private static Keyframe constantKey(String id,
                                        float time,
                                        float value,
                                        Curve.Interpolation interpolation,
                                        float inSlope,
                                        float outSlope,
                                        float inLength,
                                        float outLength) {
        return new Keyframe(new KeyId(id), time, new Constant(value),
                inSlope, outSlope, inLength, outLength, interpolation);
    }

    private static Keyframe formulaKey(String id,
                                       float time,
                                       String expression,
                                       Curve.Interpolation interpolation) {
        return new Keyframe(new KeyId(id), time, new Formula(expression),
                0f, 0f, 1f, 1f, interpolation);
    }
}
