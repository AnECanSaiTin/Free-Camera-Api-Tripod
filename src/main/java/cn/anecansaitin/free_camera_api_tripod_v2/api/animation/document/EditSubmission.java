package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一次文档提交：纯编辑操作 + 基版本 + 不可变动作标签（主设计 §3.3、§5.1）。
///
/// 动作标签跟随提交进入撤销历史，不需要为每种 [EditOp] 复制标签字段；拒绝提交时标签
/// 不进入历史。标签是不可变 [LocalizedMessage]，宿主显示时再渲染。
///
/// @param operation       纯编辑操作；不得修改输入文档、不得执行 IO
/// @param expectedRevision 调用方读取快照时的发布序号
/// @param label           动作标签；只随成功提交进入历史
@NullMarked
public record EditSubmission(
        EditOp operation,
        Revision expectedRevision,
        LocalizedMessage label) {

    public EditSubmission {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        Objects.requireNonNull(label, "label");
    }
}
