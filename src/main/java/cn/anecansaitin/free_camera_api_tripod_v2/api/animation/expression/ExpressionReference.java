package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 表达式里的一个外部引用：种类 + 名字 + 调用实参个数 + 源码区间。
///
/// ## 为什么带区间
///
/// 提交期校验发现“未定义变量 / 函数参数个数不符 / 样本名不存在”时，诊断必须能定位到源码
/// 片段，编辑器才能高亮。区间是 `[start, end)` 的半开区间（UTF-16 下标），由编译器给出。
///
/// ## 为什么函数带实参个数
///
/// 函数名是否已定义、形参个数是否匹配都由符号表决定；编译器只登记“这里调用了一个名为 X
/// 的函数、传了 N 个实参”，校验器拿符号表比对（主设计 §6.1：函数参数个数与名字作用域在
/// 提交期检查）。
///
/// @param kind          引用种类
/// @param name          被引用的名字，非空白
/// @param argumentCount 函数调用的实参个数；非函数引用必须为 `-1`
/// @param start         源码起始下标，非负
/// @param end           源码结束下标（不含），不小于 [start]
@NullMarked
public record ExpressionReference(
        ExpressionReferenceKind kind,
        String name,
        int argumentCount,
        int start,
        int end) {

    public ExpressionReference {
        Objects.requireNonNull(kind, "kind");

        if (name.isBlank()) {
            throw new IllegalArgumentException("ExpressionReference name must not be blank");
        }

        if (start < 0 || end < start) {
            throw new IllegalArgumentException(
                    "ExpressionReference span must satisfy 0 <= start <= end: [" + start + ", " + end + ")");
        }

        if (kind == ExpressionReferenceKind.FUNCTION) {
            if (argumentCount < 0) {
                throw new IllegalArgumentException(
                        "Function reference must carry a non-negative argument count: " + argumentCount);
            }
        } else if (argumentCount != -1) {
            throw new IllegalArgumentException(
                    "Non-function reference must use argumentCount = -1: " + argumentCount);
        }
    }

    /// 变量引用。
    public static ExpressionReference variable(String name, int start, int end) {
        return new ExpressionReference(ExpressionReferenceKind.VARIABLE, name, -1, start, end);
    }

    /// 函数调用引用。
    public static ExpressionReference function(String name, int argumentCount, int start, int end) {
        return new ExpressionReference(ExpressionReferenceKind.FUNCTION, name, argumentCount, start, end);
    }

    /// 样本名引用。
    public static ExpressionReference sample(String name, int start, int end) {
        return new ExpressionReference(ExpressionReferenceKind.SAMPLE, name, -1, start, end);
    }
}
