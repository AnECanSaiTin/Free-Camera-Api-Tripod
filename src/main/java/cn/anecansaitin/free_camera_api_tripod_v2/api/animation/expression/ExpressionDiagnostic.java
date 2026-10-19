package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一条表达式诊断：稳定错误码 + 技术细节 + 源码区间（主设计 §4.2、§6.1）。
///
/// ## 为什么不是 `edit.Diagnostic`
///
/// 表达式层保持独立（设计 §2.1），不依赖文档作用域、发布版本与本地化消息。
/// 这里只给“哪段源码、什么原因”的**技术事实**；统一文档校验再把它包装成带作用域、
/// 对象定位与本地化文案的 `Diagnostic`。
///
/// `detail` 是技术细节（例如“除数为 0”“深度 65 超过 64”），允许为空串，**不面向最终用户**；
/// 面向用户的文案由上层按 [code] 与细节生成。
///
/// @param code   稳定错误码
/// @param detail 技术细节，可为空串，不为 null
/// @param start  源码起始下标，非负
/// @param end    源码结束下标（不含），不小于 [start]
@NullMarked
public record ExpressionDiagnostic(
        ExpressionDiagnosticCode code,
        String detail,
        int start,
        int end) {

    public ExpressionDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");

        if (start < 0 || end < start) {
            throw new IllegalArgumentException(
                    "ExpressionDiagnostic span must satisfy 0 <= start <= end: [" + start + ", " + end + ")");
        }
    }

    /// 覆盖整个表达式的诊断（编译期或全局运行期失败的常用形态）。
    public static ExpressionDiagnostic overSource(ExpressionDiagnosticCode code, String detail, int sourceLength) {
        return new ExpressionDiagnostic(code, detail, 0, Math.max(0, sourceLength));
    }
}
