package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一次成功编译得到的语法树根（core 内部类型，不属于 api 契约）。
///
/// 只包一个根节点：编译期不做常量折叠、不绑定符号表、不读取采样声明。后续求值器从
/// [CompiledExpressionImpl#ast()] 取得本对象，再按 [ExpressionNode] 的子类型分派。
///
/// @param root 根节点；非 null
@NullMarked
public record ExpressionAst(ExpressionNode root) {

    public ExpressionAst {
        Objects.requireNonNull(root, "root");
    }

    /// 根节点源码起始下标。
    public int start() {
        return root.start();
    }

    /// 根节点源码结束下标（不含）。
    public int end() {
        return root.end();
    }
}
