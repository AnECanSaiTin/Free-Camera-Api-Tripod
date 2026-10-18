package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Selection;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/// 会话视图状态：播放头、选择、时间轴、吸附、预览区间、轨道显隐、工具与每路覆盖
/// （主设计 §9.1、UI §6）。
///
/// 这些状态属于会话 / 视图，不写文档、不进撤销历史。`playhead` 与 `previewRange` 只影响
/// 编辑预览；`trackVisibility` 只过滤显示，不改变求值或播放。`rigOverrides` 是预览用的
/// 播放覆盖，不能改写文档默认值。
///
/// @param playhead      播放头，秒；有限且非负
/// @param selection     基于稳定 ID 的选择
/// @param timeline      时间轴视图参数
/// @param snapping      吸附设置
/// @param previewRange  编辑预览循环区间；空动画 / 不循环时为空
/// @param trackVisibility 轨道显隐 / solo
/// @param tool          当前编辑工具
/// @param activeRigId   当前编辑机位；不改变 cameraSource 或运行活动状态
/// @param rigOverrides  按机位的预览覆盖；不修改文档
@NullMarked
public record SessionViewState(
        float playhead,
        Selection selection,
        TimelineView timeline,
        SnappingSettings snapping,
        @Nullable PreviewRange previewRange,
        TrackVisibility trackVisibility,
        EditorToolState tool,
        @Nullable RigId activeRigId,
        Map<RigId, RigPlaybackOverrides> rigOverrides) {

    public SessionViewState {
        if (!Float.isFinite(playhead) || playhead < 0f) {
            throw new IllegalArgumentException(
                    "SessionViewState playhead must be finite and non-negative: " + playhead);
        }

        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(snapping, "snapping");
        Objects.requireNonNull(trackVisibility, "trackVisibility");
        Objects.requireNonNull(tool, "tool");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        rigOverrides = Map.copyOf(rigOverrides);
    }

    /// 初始视图状态。
    public static SessionViewState initial() {
        return new SessionViewState(
                0f,
                Selection.EMPTY,
                TimelineView.defaults(),
                SnappingSettings.disabled(),
                null,
                TrackVisibility.allVisible(),
                EditorToolState.SELECT,
                null,
                Map.of());
    }
}
