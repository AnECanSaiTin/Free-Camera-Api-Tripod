package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/// 文档解码的两阶段结果（主设计 §4.2、§8.1、§8.2）。
///
/// ## 为什么区分“结构失败”与“校验失败”
///
/// 主设计 §4.2 把校验分成 L1–L4 四层，并要求“前一层失败时不继续依赖其结果的校验”；
/// §8.1 要求“不得将部分解码结果作为合法文档发布”。对应到解码：
///
/// 1. **结构阶段**：Codec 是否能把字节读成对象。失败时**没有候选文档**，
///    因此只能给出文本错误（文件 / 字段路径由存储层补充）。
/// 2. **校验阶段**：结构已成立，候选文档交由统一校验器检查。
///    失败时**有结构化诊断**（错误码、层级、对象定位、可选修复），可以展示给作者。
///
/// 两者分开，是为了不让“文件读不进来”和“文件读进来了但内容不合法”混成同一条消息：
/// 前者无法定位到对象，后者可以（主设计 §8.2 的诊断模式）。
@NullMarked
public sealed interface DocumentDecodeResult
        permits DocumentDecodeResult.Accepted, DocumentDecodeResult.StructureFailed,
        DocumentDecodeResult.ValidationFailed {

    /// 结构解码与统一校验均通过。
    ///
    /// `diagnostics` 只可能包含非 `ERROR` 级别的诊断（警告 / 提示）；
    /// 任一 `ERROR` 诊断都必须走 [ValidationFailed]。
    ///
    /// @param document    候选文档；已通过结构约束与统一校验
    /// @param diagnostics 非错误级诊断；可为空
    record Accepted(CameraAnimation document, List<Diagnostic> diagnostics)

            implements DocumentDecodeResult {

        public Accepted {
            diagnostics = List.copyOf(diagnostics);

            // 结构防御（复核 R34.2）：Accepted 不得携带 ERROR。
            // 正常解码入口已按 Severity 分流，这里是公开构造器的兜底，
            // 防止调用方手工拼出“接受但含错误”的矛盾结果。
            if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR)) {
                throw new IllegalArgumentException(
                        "Accepted must not carry ERROR diagnostics; use ValidationFailed");
            }
        }
    }

    /// 结构阶段失败：Codec 无法把输入读成文档。
    ///
    /// 没有候选文档可校验，因此只有消息。消息来自 DFU 的错误（通常含字段路径），
    /// 存储层可以把它包装成文件诊断。
    ///
    /// @param message 结构错误消息
    record StructureFailed(String message) implements DocumentDecodeResult {
    }

    /// 校验阶段失败：结构已成立，但统一校验报告了至少一条 `ERROR`。
    ///
    /// @param diagnostics 校验诊断；至少包含一条 `ERROR`
    record ValidationFailed(List<Diagnostic> diagnostics) implements DocumentDecodeResult {

        public ValidationFailed {
            diagnostics = List.copyOf(diagnostics);

            // 结构防御（复核 R34.2）：校验失败必须至少有一条 ERROR。
            if (diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == Severity.ERROR)) {
                throw new IllegalArgumentException(
                        "ValidationFailed requires at least one ERROR diagnostic");
            }
        }
    }

    /// 是否可用于发布（打开 / 换档 / 收包应用）。
    default boolean accepted() {
        return this instanceof Accepted;
    }

    /// 通过时的候选文档。
    default Optional<CameraAnimation> candidate() {
        return this instanceof Accepted accepted
                ? Optional.of(accepted.document())
                : Optional.empty();
    }

    /// 诊断列表：`Accepted` 给出非错误级诊断，`ValidationFailed` 给出全部校验诊断，
    /// `StructureFailed` 没有结构化诊断（返回空列表）。
    default List<Diagnostic> diagnostics() {
        return switch (this) {
            case Accepted accepted -> accepted.diagnostics();
            case ValidationFailed failed -> failed.diagnostics();
            case StructureFailed ignored -> List.of();
        };
    }

    /// 结构失败消息；其它分支返回空。
    default Optional<String> structureError() {
        return this instanceof StructureFailed failed
                ? Optional.of(failed.message())
                : Optional.empty();
    }
}
