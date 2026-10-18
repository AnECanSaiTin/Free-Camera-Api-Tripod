package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// 试算结果：状态、覆盖报告与诊断（主设计 §4.3）。
///
/// 状态与诊断必须一致：
///
/// - [TrialStatus#FAILED] 至少包含一条 `ERROR` 诊断；
/// - [TrialStatus#PASSED] / [TrialStatus#INCOMPLETE] 不得包含已确认的 `ERROR`；发现确定错误
///   时应返回 `FAILED`，不能用预算不足掩盖；
/// - `INCOMPLETE` 必须说明未完成原因。
///
/// 报告必须给出检查区间、采样策略、实际检查量、固定事实身份或说明；`PASSED` 只表示已检查
/// 点未发现问题，不升级为“全时间域安全”。
///
/// @param status      完成状态
/// @param report      本次试算的覆盖报告
/// @param diagnostics 诊断；不得保留调用方容器
@NullMarked
public record TrialResult(
        TrialStatus status,
        TrialReport report,
        List<Diagnostic> diagnostics) {

    public TrialResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(report, "report");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        diagnostics = List.copyOf(diagnostics);

        boolean hasError = hasError(diagnostics);

        if (status == TrialStatus.FAILED && !hasError) {
            throw new IllegalArgumentException("TrialResult FAILED requires at least one ERROR diagnostic");
        }

        if (status != TrialStatus.FAILED && hasError) {
            throw new IllegalArgumentException(
                    "TrialResult " + status + " must not carry an ERROR diagnostic; use FAILED");
        }

        if (status == TrialStatus.INCOMPLETE && report.incompleteReason() == null) {
            throw new IllegalArgumentException("TrialResult INCOMPLETE requires an incompleteReason");
        }

        if (status != TrialStatus.INCOMPLETE && report.incompleteReason() != null) {
            throw new IllegalArgumentException(
                    "TrialResult " + status + " must not carry an incompleteReason");
        }
    }

    /// 是否包含确定错误；与 [TrialStatus#FAILED] 的一致性由构造器保证。
    public boolean hasError() {
        return hasError(diagnostics);
    }

    private static boolean hasError(List<Diagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }

    /// 试算覆盖报告。
    ///
    /// @param rangeStart        检查区间起点，秒；有限且非负
    /// @param rangeEnd          检查区间终点，秒；有限、非负且不小于起点
    /// @param samplingStrategy  采样策略说明，非空白
    /// @param samplesChecked    实际检查的采样点数，非负
    /// @param fixedFactsIdentity 固定事实批次身份；无固定事实时为空
    /// @param fixedFactsNote    固定事实说明；无说明时为空
    /// @param incompleteReason  未完成原因；仅 `INCOMPLETE` 时非空
    public record TrialReport(
            float rangeStart,
            float rangeEnd,
            String samplingStrategy,
            int samplesChecked,
            @Nullable Long fixedFactsIdentity,
            @Nullable String fixedFactsNote,
            @Nullable LocalizedMessage incompleteReason) {

        public TrialReport {
            if (!Float.isFinite(rangeStart) || rangeStart < 0f) {
                throw new IllegalArgumentException(
                        "TrialReport rangeStart must be finite and non-negative: " + rangeStart);
            }

            if (!Float.isFinite(rangeEnd) || rangeEnd < rangeStart) {
                throw new IllegalArgumentException(
                        "TrialReport rangeEnd must be finite and >= rangeStart: " + rangeEnd);
            }

            Objects.requireNonNull(samplingStrategy, "samplingStrategy");

            if (samplingStrategy.isBlank()) {
                throw new IllegalArgumentException("TrialReport samplingStrategy must not be blank");
            }
            if (samplesChecked < 0) {
                throw new IllegalArgumentException(
                        "TrialReport samplesChecked must be non-negative: " + samplesChecked);
            }

            if (fixedFactsIdentity != null && fixedFactsIdentity < 0) {
                throw new IllegalArgumentException(
                        "TrialReport fixedFactsIdentity must be non-negative: " + fixedFactsIdentity);
            }

            if (fixedFactsNote != null && fixedFactsNote.isBlank()) {
                throw new IllegalArgumentException("TrialReport fixedFactsNote must not be blank");
            }
        }
    }
}
