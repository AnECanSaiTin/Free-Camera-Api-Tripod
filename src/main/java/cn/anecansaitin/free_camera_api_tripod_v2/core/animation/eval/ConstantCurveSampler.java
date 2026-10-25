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
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 第一版曲线采样器：只支持常量关键帧。
///
/// 表达式求值器尚未实现，因此本版只读取 [Constant] 值源。采样区间任一端点是 [Formula]
/// 时返回 [ChannelSample#error] 并给出结构化诊断，**不会**退回另一端点或 0，也不会把公式
/// 静默当成常量。曲线区间之外的 wrap、段内 STEP / LINEAR / HERMITE 与边界夹取由
/// [CurveInterpolation] 负责。
///
/// ## 输入与副作用
///
/// 只读取 [CurveSampleRequest] 中显式绑定的求值上下文、轨道与时间：不访问世界、store、
/// 当前活动机位或客户端类，本帧事实也不会被重新读取。空曲线返回
/// [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SampleStatus#ABSENT]，
/// 而不是 0。
///
/// ## 有限性
///
/// 键时间由 `Keyframe` 保证有限非负，采样时间由 `CurveSampleRequest` 保证有限非负；
/// 本类仍检查 wrap 结果与最终数值，出现 `NaN` / `Infinity`（包括常量本身非法或 `float`
/// 溢出）时返回 `ERROR`。错误诊断使用绑定快照的发布版本作为文档作用域，定位到轨道 ID
/// 与 `curve.keys[...]` 字段路径。
///
/// 本类无状态，可安全复用；不替代后续带公式、`TrackRef` 与共享会话记账的完整采样器。
@NullMarked
public final class ConstantCurveSampler implements CurveSampler {

    /// 公式求值器尚未实现：公式端点不能参与采样。
    private static final DiagnosticCode EXPRESSION_EVALUATOR_UNAVAILABLE =
            new DiagnosticCode("free_camera_api_tripod:expression_evaluator_unavailable");

    /// 采样结果不是有限数值。
    private static final DiagnosticCode NON_FINITE_CURVE_SAMPLE =
            new DiagnosticCode("free_camera_api_tripod:non_finite_curve_sample");

    /// 缺失轨道。
    private static final String TRACK_FIELD_PATH = "curve";

    /// 创建无状态采样器。
    public ConstantCurveSampler() {
    }

    @Override
    public ChannelSample sample(CurveSampleRequest request) {
        Objects.requireNonNull(request, "request");

        EvaluationContext context = request.context();
        TrackId trackId = request.trackId();
        Revision revision = context.snapshot().revision();

        Optional<CurveTrack> found = context.tracks().findCurve(trackId);

        if (found.isEmpty()) {
            return ChannelSample.error(error(DiagnosticCode.BROKEN_REFERENCE,
                    "Curve track '" + trackId + "' is not present in the bound snapshot",
                    trackId, revision, TRACK_FIELD_PATH));
        }

        Curve curve = found.get().curve();

        if (curve.isEmpty()) {
            return ChannelSample.absent();
        }

        List<Keyframe> keys = curve.keys();
        Keyframe first = keys.getFirst();
        Keyframe last = keys.getLast();
        float sampleTime = request.sampleTime();

        double time = CurveInterpolation.wrapTime(
                sampleTime, first.time(), last.time(), curve.preMode(), curve.postMode());

        if (!Double.isFinite(time)) {
            return nonFinite(trackId, revision, sampleTime, time, TRACK_FIELD_PATH);
        }

        // 单键（段长为 0）按 CLAMP 处理：任何时间都取唯一端点。
        if (!(last.time() > first.time())) {
            return sampleKey(first, 0, sampleTime, trackId, revision);
        }

        if (time <= first.time()) {
            return sampleKey(first, 0, sampleTime, trackId, revision);
        }

        if (time >= last.time()) {
            return sampleKey(last, keys.size() - 1, sampleTime, trackId, revision);
        }

        int leftIndex = floorIndex(keys, time);
        Keyframe left = keys.get(leftIndex);
        Keyframe right = keys.get(leftIndex + 1);

        // 区间两端都必须是常量：任一端是公式都报错，不静默使用另一端或 0。
        Constant leftSource = constantSource(left);

        if (leftSource == null) {
            return formulaUnsupported(left, leftIndex, trackId, revision);
        }

        Constant rightSource = constantSource(right);

        if (rightSource == null) {
            return formulaUnsupported(right, leftIndex + 1, trackId, revision);
        }

        double normalized = CurveInterpolation.normalizedTime(time, left.time(), right.time());

        double computed = switch (left.interpolation()) {
            case STEP -> leftSource.value();
            case LINEAR -> CurveInterpolation.linear(normalized, leftSource.value(), rightSource.value());
            case HERMITE -> CurveInterpolation.hermite(
                    normalized,
                    (double) right.time() - (double) left.time(),
                    leftSource.value(),
                    rightSource.value(),
                    left.outSlope(),
                    right.inSlope(),
                    left.outLength(),
                    right.inLength());
        };

        return finite(trackId, revision, sampleTime, computed,
                "curve.keys[" + leftIndex + ".." + (leftIndex + 1) + "]");
    }

    /// 只取单个键（单键曲线或夹取到首 / 末键）；该键是公式时仍然报错。
    private static ChannelSample sampleKey(Keyframe key,
                                           int index,
                                           float sampleTime,
                                           TrackId trackId,
                                           Revision revision) {
        Constant source = constantSource(key);

        if (source == null) {
            return formulaUnsupported(key, index, trackId, revision);
        }

        return finite(trackId, revision, sampleTime, source.value(),
                "curve.keys[" + index + "]");
    }

    /// 取常量值源；公式返回空。`NumberSource` 是 sealed（仅 Constant / Formula），
    /// 因此空只可能表示公式。
    private static @Nullable Constant constantSource(Keyframe key) {
        return key.value() instanceof Constant constant ? constant : null;
    }

    /// 公式端点的结构化错误：明确说明表达式求值器尚未实现。
    private static ChannelSample formulaUnsupported(Keyframe key,
                                                    int index,
                                                    TrackId trackId,
                                                    Revision revision) {
        Formula formula = (Formula) key.value();
        String message = "Keyframe '" + key.keyId() + "' at " + key.time() + "s uses formula '"
                + formula.expression() + "'; the expression evaluator is not implemented yet, "
                + "so formula keys cannot be sampled by the constant-only sampler";

        return ChannelSample.error(error(EXPRESSION_EVALUATOR_UNAVAILABLE, message,
                trackId, revision, "curve.keys[" + index + "].value"));
    }

    /// 找最后一个时间小于等于 `time` 的键下标；调用方保证 `time` 落在首末键之间。
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

    /// 检查并返回有限数值；`float` 溢出同样按非有限处理。
    private static ChannelSample finite(TrackId trackId,
                                        Revision revision,
                                        float sampleTime,
                                        double computed,
                                        String fieldPath) {
        float value = (float) computed;

        if (!Float.isFinite(value)) {
            return nonFinite(trackId, revision, sampleTime, computed, fieldPath);
        }

        return ChannelSample.value(value);
    }

    /// 非有限结果的 `ERROR`：保留实际计算结果，便于定位 NaN / Infinity 来源。
    private static ChannelSample nonFinite(TrackId trackId,
                                           Revision revision,
                                           float sampleTime,
                                           double computed,
                                           String fieldPath) {
        return ChannelSample.error(error(NON_FINITE_CURVE_SAMPLE,
                "Curve '" + trackId + "' produced a non-finite sample at " + sampleTime
                        + "s: " + computed,
                trackId, revision, fieldPath));
    }

    /// 构造绑定快照版本的文档作用域诊断。
    private static Diagnostic error(DiagnosticCode code,
                                    String message,
                                    TrackId trackId,
                                    Revision revision,
                                    String fieldPath) {
        return new Diagnostic(code, Severity.ERROR, LocalizedMessage.literal(message),
                new DiagnosticScope.Document(revision),
                new Diagnostic.Location(trackId, fieldPath), null);
    }
}
