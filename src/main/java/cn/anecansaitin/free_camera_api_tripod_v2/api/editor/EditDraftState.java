package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTransaction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 一个编辑事务的不可变草稿视图（主设计 §5.1、UI §6.2）。
///
/// [fieldTexts] 保留面板输入原文（包括暂时不能解析的文本）；[candidate] 是会话根据草稿生成的
/// 完整操作候选，无法解析 / 校验失败时为空。诊断与原文不能被面板丢弃，[commit] 使用该事务
/// 已保存的候选；没有候选或存在错误时提交必须拒绝，不能绕过预览直接提交。
///
/// @param transaction 草稿所属事务（基版本、目标、标签）
/// @param fieldTexts  字段路径 → 输入原文；只读副本
/// @param candidate   会话生成的完整操作候选；不完整时为空
/// @param diagnostics 草稿诊断；保留不可解析文本的原因
@NullMarked
public record EditDraftState(
        EditTransaction transaction,
        Map<String, String> fieldTexts,
        @Nullable EditOp candidate,
        List<Diagnostic> diagnostics) {

    public EditDraftState {
        Objects.requireNonNull(transaction, "transaction");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        fieldTexts = Map.copyOf(fieldTexts);
        diagnostics = List.copyOf(diagnostics);

        for (String fieldPath : fieldTexts.keySet()) {
            if (fieldPath.isBlank()) {
                throw new IllegalArgumentException("EditDraftState field path must not be blank");
            }
        }
    }

    /// 是否已有可提交候选：候选非空且没有 ERROR 诊断。
    public boolean hasCandidate() {
        return candidate != null
                && diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }
}
