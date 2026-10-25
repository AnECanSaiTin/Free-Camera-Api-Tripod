package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampleRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampler;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionEvaluator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionCompiler;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionInputs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialReadings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.BoundedExpressionCompiler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 支持公式关键帧的曲线采样器。
///
/// 常量端的插值 / wrap 口径与 [ConstantCurveSampler] 一致；端点为 [Formula] 时，本类构造
/// [ExpressionInputs] 并交给 [ExpressionEvaluator]。公式的来源是
/// `ExpressionSource.Channel(trackId, keyId)`，因此失败链能定位到具体关键帧。
///
/// ## 公式的求值时刻
///
/// 曲线上的公式按**当前采样时间**求值，而不是键时间；这是曲线采样“同一时刻的所有依赖读同一
/// 帧”的口径。端点公式失败会阻止该端点参与插值，不会退回常量或另一端点。
///
/// ## 空间读数
///
/// [CurveSampleRequest] 目前不携带解析后的空间读数，因此公式中的空间内建读数只能拿到空事实；
/// 本版没有定义空间内建名，若后续加入，应由请求扩展阶段补入。
@NullMarked
public final class ExpressionCurveSampler implements CurveSampler {

    private final ExpressionEvaluator evaluator;
    private final ExpressionCompiler compiler;

    /// 使用默认有界编译缓存创建采样器。
    ///
    /// @param evaluator 公式求值器；不得读取当前 store
    public ExpressionCurveSampler(ExpressionEvaluator evaluator) {
        this(evaluator, new BoundedExpressionCompiler());
    }

    /// @param evaluator 公式求值器；不得读取当前 store
    /// @param compiler  公式编译器；同一源码文本应复用缓存
    public ExpressionCurveSampler(ExpressionEvaluator evaluator, ExpressionCompiler compiler) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    @Override
    public ChannelSample sample(CurveSampleRequest request) {
        Objects.requireNonNull(request, "request");

        Optional<CurveTrack> found = request.context().tracks().findCurve(request.trackId());

        if (found.isEmpty()) {
            return error(request, null, "Curve track '" + request.trackId()
                    + "' is not present in the bound snapshot");
        }

        Curve curve = found.get().curve();

        if (curve.isEmpty()) {
            return ChannelSample.absent();
        }

        List<Keyframe> keys = curve.keys();
        Keyframe first = keys.getFirst();
        Keyframe last = keys.getLast();
        double time = CurveInterpolation.wrapTime(
                request.sampleTime(), first.time(), last.time(), curve.preMode(), curve.postMode());

        if (!Double.isFinite(time)) {
            return error(request, null, "Curve wrap produced a non-finite time: " + time);
        }

        if (!(last.time() > first.time())) {
            return sampleKey(request, first, 0);
        }

        if (time <= first.time()) {
            return sampleKey(request, first, 0);
        }

        if (time >= last.time()) {
            return sampleKey(request, last, keys.size() - 1);
        }

        int leftIndex = floorIndex(keys, time);
        Keyframe left = keys.get(leftIndex);
        Keyframe right = keys.get(leftIndex + 1);

        Endpoint leftValue = endpoint(request, left, leftIndex);

        if (leftValue.error != null) {
            return leftValue.error;
        }

        // STEP 只需要左端；右端公式不该被无谓求值。
        if (left.interpolation() == Curve.Interpolation.STEP) {
            return ChannelSample.value(leftValue.value);
        }

        Endpoint rightValue = endpoint(request, right, leftIndex + 1);

        if (rightValue.error != null) {
            return rightValue.error;
        }

        double normalized = CurveInterpolation.normalizedTime(time, left.time(), right.time());
        double result = switch (left.interpolation()) {
            case STEP -> leftValue.value;
            case LINEAR -> CurveInterpolation.linear(normalized, leftValue.value, rightValue.value);
            case HERMITE -> CurveInterpolation.hermite(
                    normalized,
                    (double) right.time() - left.time(),
                    leftValue.value,
                    rightValue.value,
                    left.outSlope(),
                    right.inSlope(),
                    left.outLength(),
                    right.inLength());
        };

        return finite(request, result, "curve.keys[" + leftIndex + ".." + (leftIndex + 1) + "]");
    }

    private ChannelSample sampleKey(CurveSampleRequest request, Keyframe key, int index) {
        Endpoint endpoint = endpoint(request, key, index);
        return endpoint.error != null ? endpoint.error : ChannelSample.value(endpoint.value);
    }

    private Endpoint endpoint(CurveSampleRequest request, Keyframe key, int index) {
        if (key.value() instanceof Constant constant) {
            return new Endpoint(constant.value(), null);
        }

        Formula formula = (Formula) key.value();
        var compiled = compile(formula.expression());

        if (compiled == null) {
            return new Endpoint(0f, error(request, key,
                    "Curve formula cannot compile: " + formula.expression()));
        }

        ExpressionInputs inputs = inputs(request, key, index);
        ExpressionSample sample = evaluator.evaluate(compiled, inputs);

        if (sample instanceof ExpressionSample.Failure failure) {
            return new Endpoint(0f, expressionError(request, key, index, failure.failure()));
        }

        float value = ((ExpressionSample.Value) sample).value();

        if (!Float.isFinite(value)) {
            return new Endpoint(0f, error(request, key,
                    "Curve formula produced a non-finite value at " + request.sampleTime() + "s"));
        }

        return new Endpoint(value, null);
    }

    private ExpressionInputs inputs(CurveSampleRequest request, Keyframe key, int index) {
        float duration = request.context().snapshot().document().duration();
        float progress = duration <= 0f ? 0f : Math.clamp(request.sampleTime() / duration, 0f, 1f);
        float worldTime = (request.context().facts().gameTick()
                + request.context().facts().partialTick()) / 20f;

        return new ExpressionInputs(
                request.context(),
                new ExpressionSource.Channel(request.trackId(), key.keyId()),
                request.rigId(),
                request.sampleTime(),
                progress,
                worldTime,
                SpatialReadings.EMPTY,
                randomSeed(request, key, index),
                request.session(),
                this);
    }

    private static long randomSeed(CurveSampleRequest request, Keyframe key, int index) {
        long seed = request.context().contextId() * 31L;
        seed = seed * 31L + Float.floatToIntBits(request.sampleTime());
        seed = seed * 31L + key.keyId().value().hashCode();
        return seed * 31L + index;
    }

    private cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.@Nullable CompiledExpression compile(
            String source) {
        return compiler.compile(source).compiledExpression().orElse(null);
    }

    private ChannelSample expressionError(CurveSampleRequest request,
                                          Keyframe key,
                                          int index,
                                          ExpressionFailure failure) {
        var diagnostic = failure.innermostDiagnostic()
                .orElse(failure.diagnostic());
        String message = "Curve formula failed (" + diagnostic.code() + " at ["
                + diagnostic.start() + ", " + diagnostic.end() + ")): " + diagnostic.detail();

        return error(request, key, message);
    }

    private static ChannelSample finite(CurveSampleRequest request, double value, String path) {
        float result = (float) value;

        if (!Float.isFinite(result)) {
            return error(request, null, "Curve produced a non-finite sample at " + path + ": " + value);
        }

        return ChannelSample.value(result);
    }

    private static ChannelSample error(CurveSampleRequest request,
                                       @Nullable Keyframe key,
                                       String message) {
        Revision revision = request.context().snapshot().revision();
        String path = key == null ? "curve" : "curve.keys." + key.keyId();

        Diagnostic diagnostic = new Diagnostic(
                DiagnosticCode.EXPRESSION_INVALID,
                Severity.ERROR,
                LocalizedMessage.literal(message),
                new DiagnosticScope.Document(revision),
                new Diagnostic.Location(request.trackId(), path),
                null);

        return ChannelSample.error(diagnostic);
    }

    private static int floorIndex(List<Keyframe> keys, double time) {
        int low = 0;
        int high = keys.size() - 1;

        while (low < high) {
            int middle = (low + high + 1) >>> 1;

            if (keys.get(middle).time() <= time) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }

        return low;
    }

    private record Endpoint(float value, @Nullable ChannelSample error) {
    }
}
