/**
 * 表达式编译实现：词法分析、语法分析与 core 内部语法树。
 *
 * <p>本包实现 {@code api.animation.expression.ExpressionCompiler} 的第一版：只编译、不求值，
 * 只依赖 JDK 与表达式契约，不访问世界、文档模型、编辑器或客户端类。</p>
 *
 * <p>语法树 {@link cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionAst}
 * 与 {@link cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionNode} 是 core
 * 内部类型，不属于 api 契约；后续解释器从
 * {@link cn.anecansaitin.free_camera_api_tripod_v2.core.expression.CompiledExpressionImpl#ast()}
 * 读取它。api 侧仍只暴露源码与外部引用。</p>
 *
 * <p>编译失败只返回结构化诊断，不产生求值句柄；本版不做编译缓存（缓存若加入必须有明确上限）。</p>
 */
package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;
