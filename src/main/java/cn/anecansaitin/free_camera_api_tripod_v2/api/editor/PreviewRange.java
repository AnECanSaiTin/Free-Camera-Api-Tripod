package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import org.jspecify.annotations.NullMarked;

/// 编辑预览循环区间 `[in, out]`（主设计 §9.1、UI §6.3）。
///
/// 仅影响显式编辑预览，不改变正式播放请求或文档时长；区间边界有限、非负且
/// `0 <= in < out`。空动画禁用区间循环；普通定位不发事件。
///
/// @param in  动画时间，秒
/// @param out 动画时间，秒
@NullMarked
public record PreviewRange(float in, float out) {

    public PreviewRange {
        if (!Float.isFinite(in) || !Float.isFinite(out) || in < 0f || in >= out) {
            throw new IllegalArgumentException(
                    "PreviewRange must satisfy 0 <= in < out: " + in + ".." + out);
        }
    }
}
