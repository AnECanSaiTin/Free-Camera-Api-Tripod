package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 试算结果：状态、实际覆盖量与诊断（主设计 §4.3）。
///
/// 结果必须报告采样范围、环境参数与完成状态；`PASSED` 不能升级为“全时间域安全”的承诺，
/// `INCOMPLETE` 也不能被隐藏成通过。世界 / 非确定依赖须在 [fixedFactsNote] 中说明固化的事实与时间。
///
/// @param status        完成状态
/// @param samplesChecked 实际检查的采样点数，非负
/// @param diagnostics   诊断；`FAILED` 时至少包含一条错误
/// @param fixedFactsNote 本次试算使用的固定世界事实说明；无世界依赖时为空
@NullMarked
public record TrialResult(
        TrialStatus status,
        int samplesChecked,
        List<Diagnostic> diagnostics,
        @Nullable String fixedFactsNote) {

    public TrialResult {
        if (samplesChecked < 0) {
            throw new IllegalArgumentException("TrialResult samplesChecked must be non-negative: " + samplesChecked);
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        diagnostics = List.copyOf(diagnostics);
    }

    /// 是否没有确定错误（`PASSED` 或 `INCOMPLETE`）。
    public boolean hasError() {
        return status == TrialStatus.FAILED;
    }
}
