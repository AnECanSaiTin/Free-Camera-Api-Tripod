package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampleRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SampleStatus;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 公式曲线采样：常量/公式端点、STEP 惰性、失败与预算。
class ExpressionCurveSamplerTest {

    private static final Revision REVISION = new Revision(3);
    private static final Identifier DIMENSION =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final CoreExpressionEvaluator EVALUATOR = new CoreExpressionEvaluator();
    private static final ExpressionCurveSampler SAMPLER = new ExpressionCurveSampler(EVALUATOR);

    @Test
    void constantCurveStillInterpolatesLinearly() {
        CurveTrack track = track(new Curve(List.of(
                constantKey("a", 0f, 0f, Curve.Interpolation.LINEAR),
                constantKey("b", 10f, 10f, Curve.Interpolation.LINEAR)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));

        assertEquals(5f, sample(track, 5f).value(), 1e-5f);
    }

    @Test
    void formulaEndpointsAreEvaluatedAtSampleTime() {
        CurveTrack track = track(new Curve(List.of(
                constantKey("a", 0f, 0f, Curve.Interpolation.LINEAR),
                formulaKey("b", 10f, "t * 2", Curve.Interpolation.LINEAR)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));

        assertEquals(5f, sample(track, 5f).value(), 1e-5f);
    }

    @Test
    void stepOnlyEvaluatesTheLeftEndpoint() {
        CurveTrack track = track(new Curve(List.of(
                formulaKey("a", 0f, "t * 2", Curve.Interpolation.STEP),
                formulaKey("b", 10f, "missing_variable", Curve.Interpolation.STEP)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));

        ChannelSample sample = sample(track, 5f);
        assertEquals(SampleStatus.VALUE, sample.status());
        assertEquals(10f, sample.value(), 1e-5f);
    }

    @Test
    void formulaFailureIsReportedWithDiagnostic() {
        CurveTrack track = track(new Curve(List.of(
                formulaKey("a", 0f, "missing_variable", Curve.Interpolation.LINEAR),
                constantKey("b", 10f, 1f, Curve.Interpolation.LINEAR)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));

        ChannelSample sample = sample(track, 5f);

        assertEquals(SampleStatus.ERROR, sample.status());
        assertNotNull(sample.diagnostic());
        assertTrue(sample.diagnostic().message().toString().contains("unknown variable")
                        || sample.diagnostic().message().toString().contains("missing_variable"),
                sample.diagnostic().message().toString());
    }

    @Test
    void invalidFormulaCompilesToAnErrorInsteadOfThrowing() {
        CurveTrack track = track(new Curve(List.of(
                formulaKey("a", 0f, "1 +", Curve.Interpolation.LINEAR),
                constantKey("b", 10f, 1f, Curve.Interpolation.LINEAR)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));

        assertEquals(SampleStatus.ERROR, sample(track, 5f).status());
    }

    @Test
    void nonFiniteFormulaIsAnError() {
        CurveTrack track = track(new Curve(List.of(
                formulaKey("a", 0f, "1 / 0", Curve.Interpolation.LINEAR),
                constantKey("b", 10f, 1f, Curve.Interpolation.LINEAR)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));

        assertEquals(SampleStatus.ERROR, sample(track, 5f).status());
    }

    @Test
    void loopWrapOnlySelectsTheSegmentAndFormulaSeesDocumentTime() {
        CurveTrack track = track(new Curve(List.of(
                formulaKey("a", 1f, "t * 2", Curve.Interpolation.LINEAR),
                formulaKey("b", 3f, "t * 2", Curve.Interpolation.LINEAR)),
                Curve.Wrap.LOOP, Curve.Wrap.LOOP));

        // 0s 回绕到 2s 选段；公式里的 t 仍是文档时间 0，而不是曲线局部时间 2。
        assertEquals(0f, sample(track, 0f).value(), 1e-5f);
    }

    @Test
    void missingTrackIsAnError() {
        CurveTrack track = track(new Curve(List.of(
                constantKey("a", 0f, 0f, Curve.Interpolation.LINEAR),
                constantKey("b", 1f, 1f, Curve.Interpolation.LINEAR)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));
        EvaluationContext context = context(track);

        ChannelSample sample = SAMPLER.sample(new CurveSampleRequest(
                context, EvaluationSession.create(64L, 8), new TrackId("missing"), null, 0f));

        assertEquals(SampleStatus.ERROR, sample.status());
    }

    @Test
    void exhaustedBudgetIsReported() {
        CurveTrack track = track(new Curve(List.of(
                formulaKey("a", 0f, "t * 2", Curve.Interpolation.LINEAR),
                formulaKey("b", 10f, "t * 2", Curve.Interpolation.LINEAR)),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));
        EvaluationContext context = context(track);

        ChannelSample sample = SAMPLER.sample(new CurveSampleRequest(
                context, EvaluationSession.create(0L, 8), track.id(), null, 5f));

        assertEquals(SampleStatus.ERROR, sample.status());
    }

    private static ChannelSample sample(CurveTrack track, float time) {
        return SAMPLER.sample(new CurveSampleRequest(
                context(track), EvaluationSession.create(1024L, 16), track.id(), null, time));
    }

    private static EvaluationContext context(CurveTrack track) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(track),
                new TrackId("aim"), new TrackId("active"));
        AnimationSnapshot snapshot = new AnimationSnapshot(REVISION,
                CameraAnimation.create("doc", rig, new TrackId("source")));
        FrameFacts facts = new FrameFacts(snapshot, DIMENSION, 1L, 10L, 0.5f,
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime(3000, 12000),
                1L, Map.of(), Map.of(), true);
        TrackIndex index = new TrackIndex() {
            @Override
            public AnimationSnapshot snapshot() {
                return snapshot;
            }

            @Override
            public Optional<Entry> find(TrackId trackId) {
                return Optional.of(new Entry(track, rig.id()));
            }

            @Override
            public List<Diagnostic> diagnostics() {
                return List.of();
            }
        };

        return EvaluationContext.bind(1L, snapshot, facts,
                trackId -> trackId.equals(track.id()) ? Optional.of(track) : Optional.empty(), index);
    }

    private static CurveTrack track(Curve curve) {
        return CurveTrack.ofCurve(new TrackId("curve"), "curve", curve);
    }

    private static Keyframe constantKey(String id,
                                        float time,
                                        float value,
                                        Curve.Interpolation interpolation) {
        return new Keyframe(new KeyId(id), time, new Constant(value),
                0f, 0f, 1f, 1f, interpolation);
    }

    private static Keyframe formulaKey(String id,
                                       float time,
                                       String expression,
                                       Curve.Interpolation interpolation) {
        return new Keyframe(new KeyId(id), time, new Formula(expression),
                0f, 0f, 1f, 1f, interpolation);
    }
}
