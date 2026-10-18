package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 统一预览状态（主设计 §5.1、§5.2）。
///
/// 时间轴、曲线、变量列与视口读取**同一个**预览候选与求值环境；预览使用与正式播放相同的
/// 采样与姿态合成规则。候选不完整、结构不合法或本帧求值失败时显示诊断，且不应用该帧预览姿态。
///
/// 本类型只描述会话预览状态，不进入文档、历史或正式发布；基版本变化时 [stale] 置真，
/// 禁止自动覆盖正式文档。
///
/// @param candidate   可构造的预览候选；草稿未完成或不可解析时为空
/// @param diagnostics 当前候选 / 本帧的诊断；顺序稳定
/// @param stale       基版本是否已变化（过期预览不得提交）
@NullMarked
public record PreviewState(
        @Nullable CameraAnimation candidate,
        List<Diagnostic> diagnostics,
        boolean stale) {

    public PreviewState {
        // 防御性复制：不保留调用方容器（主设计 §3.2）
        diagnostics = List.copyOf(diagnostics);
    }

    /// 没有任何候选或诊断的初始预览状态。
    public static final PreviewState EMPTY = new PreviewState(null, List.of(), false);

    /// 是否已有可构造的预览候选。
    public boolean hasCandidate() {
        return candidate != null;
    }
}
