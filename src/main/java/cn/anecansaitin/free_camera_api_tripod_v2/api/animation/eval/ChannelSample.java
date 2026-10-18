package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 单个数值通道的采样结果：状态 + 值 + 诊断（主设计 §6.1）。
///
/// [SampleStatus#ABSENT] 表示作者没有编写该通道，不是 0；[SampleStatus#ERROR] 必须带诊断，
/// 且不得把 NaN / Infinity 伪装成合法数值。求值时参与插值的端点失败必须传播错误，
/// 不能跳过失败端点。
///
/// @param status     采样状态
/// @param value      有限数值；`ABSENT` / `ERROR` 时为 0（不参与合成）
/// @param diagnostic 错误或提示；`ERROR` 必须非空
@NullMarked
public record ChannelSample(
        SampleStatus status,
        float value,
        @Nullable Diagnostic diagnostic) {

    public ChannelSample {
        Objects.requireNonNull(status, "status");

        if (status == SampleStatus.ERROR && diagnostic == null) {
            throw new IllegalArgumentException("ChannelSample ERROR must carry a diagnostic");
        }

        if (status == SampleStatus.VALUE) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "ChannelSample VALUE must be finite: " + value);
            }
        } else if (value != 0f) {
            throw new IllegalArgumentException(
                    "ChannelSample " + status + " must use value 0: " + value);
        }
    }

    /// 未编写通道。
    public static ChannelSample absent() {
        return new ChannelSample(SampleStatus.ABSENT, 0f, null);
    }

    /// 有限数值。
    public static ChannelSample value(float value) {
        return new ChannelSample(SampleStatus.VALUE, value, null);
    }

    /// 求值失败；必须给出可定位诊断。
    public static ChannelSample error(Diagnostic diagnostic) {
        return new ChannelSample(SampleStatus.ERROR, 0f, Objects.requireNonNull(diagnostic));
    }

    /// 是否得到可用数值。
    public boolean isValue() {
        return status == SampleStatus.VALUE;
    }

    /// 是否求值失败。
    public boolean isError() {
        return status == SampleStatus.ERROR;
    }
}
