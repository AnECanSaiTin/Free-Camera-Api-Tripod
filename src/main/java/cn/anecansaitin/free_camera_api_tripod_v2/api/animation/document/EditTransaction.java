package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一次编辑事务：基版本 + 稳定目标 + 动作标签（主设计 §5.1、§5.2）。
///
/// 会话负责事务；面板只读取状态和提交意图。输入草稿允许暂时非法，不能解析的文本不进入
/// 文档候选；可构造候选的数据进入独立预览，不发布到正式句柄。
///
/// `transactionId` 由会话生成，只用于在 commit / cancel 时区分同一目标上的多个事务，
/// **不是**文档对象 ID，不参与持久化。
///
/// 动作标签使用 [LocalizedMessage]（不可变消息描述），不保存可变 `Component`。
///
/// @param transactionId 会话内唯一的事务标识，非负
/// @param baseRevision  事务开始时的发布序号
/// @param target        稳定编辑目标
/// @param label         不可变、可本地化的动作标签
@NullMarked
public record EditTransaction(
        long transactionId,
        Revision baseRevision,
        EditTarget target,
        LocalizedMessage label) {

    public EditTransaction {
        if (transactionId < 0) {
            throw new IllegalArgumentException("EditTransaction id must be non-negative: " + transactionId);
        }

        Objects.requireNonNull(baseRevision, "baseRevision");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(label, "label");
    }
}
