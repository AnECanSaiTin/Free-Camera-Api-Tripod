package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 一次求值失败的来源、诊断与嵌套原因（主设计 §4.2、§6.1）。
///
/// ## 结构
///
/// - [source]：本层诊断所属的源码身份（入口通道 / 布局 / 变量 / 函数）；
/// - [diagnostic]：本层的错误码、技术细节与**本层源码**区间；
/// - [causes]：直接嵌套失败，按发生顺序由外到内；最后一个是最内层实际失败。
///
/// 这样上层可以从入口失败沿 [causes] 找到真正失败的源码，而不必猜测区间属于哪段文本，
/// 也不需要从自由文本 `detail` 反解析定位。调用链深度与预算失败可以由外层逐层携带。
///
/// ## 不可变
///
/// 失败链是值对象：容器防御性复制，`detail` 之外不持有可变对象，可安全进入缓存与诊断汇总。
///
/// @param source     本层源码身份
/// @param diagnostic 本层结构化诊断；区间相对 [source] 的源码文本
/// @param causes     直接嵌套失败，由外到内；可为空
@NullMarked
public record ExpressionFailure(
        ExpressionSource source,
        ExpressionDiagnostic diagnostic,
        List<ExpressionFailure> causes) {

    public ExpressionFailure {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(diagnostic, "diagnostic");
        Objects.requireNonNull(causes, "causes");
        causes = List.copyOf(causes);
    }

    /// 最内层失败：沿 `causes` 的最后一个元素递归到底。
    public ExpressionFailure innermost() {
        ExpressionFailure current = this;

        while (!current.causes().isEmpty()) {
            current = current.causes().getLast();
        }

        return current;
    }

    /// 本层诊断码（便捷访问）。
    public ExpressionDiagnosticCode code() {
        return diagnostic.code();
    }

    /// 最内层诊断；用于把真正失败映射回具体对象。
    public Optional<ExpressionDiagnostic> innermostDiagnostic() {
        return Optional.of(innermost().diagnostic());
    }
}
