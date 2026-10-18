package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;

/// 稳定诊断错误码（主设计 §4.1、§4.2）。
///
/// 错误码用于程序判定与测试断言，**不随本地化文案变化**；面向用户的文字在
/// [Diagnostic#message] 里。内置码建议使用 `free_camera_api_tripod:` 前缀，
/// 第三方校验器使用自己的命名空间，避免冲突。
///
/// @param value 持久化 / 诊断用的稳定字符串，非空白
@NullMarked
public record DiagnosticCode(String value) implements Comparable<DiagnosticCode> {

    /// 过期基版本：提交必须拒绝，不得自动覆盖当前文档。
    public static final DiagnosticCode STALE_REVISION = new DiagnosticCode("free_camera_api_tripod:stale_revision");

    /// 编辑目标不存在（或已过期）。
    public static final DiagnosticCode TARGET_NOT_FOUND =
            new DiagnosticCode("free_camera_api_tripod:target_not_found");

    /// 同一轨道内键时间重复。
    public static final DiagnosticCode DUPLICATE_KEY_TIME =
            new DiagnosticCode("free_camera_api_tripod:duplicate_key_time");

    /// 键时间非法（非有限或负数）。
    public static final DiagnosticCode ILLEGAL_KEY_TIME =
            new DiagnosticCode("free_camera_api_tripod:illegal_key_time");

    /// 引用完整性失败（轨道 / 机位 / 样本 / 符号等）。
    public static final DiagnosticCode BROKEN_REFERENCE =
            new DiagnosticCode("free_camera_api_tripod:broken_reference");

    public DiagnosticCode {
        if (value.isBlank()) {
            throw new IllegalArgumentException("DiagnosticCode must not be blank");
        }
    }

    @Override
    public int compareTo(DiagnosticCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
