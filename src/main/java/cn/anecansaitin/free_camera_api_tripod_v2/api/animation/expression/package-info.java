/**
 * 表达式编译契约（主设计 §2.1、§6.1）。
 *
 * <h2>内容</h2>
 *
 * <ul>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionCompiler}：
 *       源码 → 不透明编译句柄或结构化诊断；实现按源码文本做有界 LRU 缓存。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult}：
 *       成功 / 失败两分支，失败不产生可求值句柄。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompiledExpression}：
 *       只暴露源码与外部引用，不公开语法树。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReferences}、
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReference}：
 *       供提交期 L3 校验（名字/实参/样本绑定）与依赖图使用。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic}、
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode}：
 *       本层的轻量结构化诊断，由上层包装成 {@code edit.Diagnostic}。</li>
 * </ul>
 *
 * <h2>与求值层的分工</h2>
 *
 * <p>求值输入涉及快照、帧事实、机位空间上下文与曲线采样器，因此求值入口在
 * {@code api.animation.eval}（{@code ExpressionEvaluator} / {@code ExpressionInputs}）。
 * 本包**不反向依赖** {@code eval}、{@code block}、{@code edit} 或 {@code playback}
 * （主设计 §2.1 的依赖方向），只依赖 JDK 与注解。
 *
 * <h2>不在本包的内容</h2>
 *
 * <ul>
 *   <li>值源、曲线、关键帧与通道语义键在 {@code api.animation.field}；</li>
 *   <li>符号表条目（{@code Variable} / {@code CustomFunction}）在对应模型包；</li>
 *   <li>语法树、解释器与常量折叠实现**不是稳定扩展接口**（主设计 §6.1），
 *       实现类不进入本包公开契约。</li>
 * </ul>
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;
