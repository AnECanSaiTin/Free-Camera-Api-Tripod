package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 会话选择状态：全部用稳定 ID 表达（主设计 §5.3）。
///
/// 选择不进入文档历史；版本变化时在新文档中解析，对象不存在则清除。
/// 界面索引只用于当前快照展示，不得跨版本执行编辑。
///
/// @param rigs    选中的机位
/// @param tracks  选中的轨道（含机位通道、状态轨道与文档级轨道）
/// @param keys    选中的关键帧
/// @param markers 选中的标记
@NullMarked
public record Selection(
        List<RigId> rigs,
        List<TrackId> tracks,
        List<KeySelection> keys,
        List<MarkerId> markers) {

    public Selection {
        // 防御性复制：不保留调用方容器（主设计 §3.2）
        rigs = List.copyOf(rigs);
        tracks = List.copyOf(tracks);
        keys = List.copyOf(keys);
        markers = List.copyOf(markers);
    }

    /// 空选择。
    public static final Selection EMPTY = new Selection(List.of(), List.of(), List.of(), List.of());

    /// 选择是否为空。
    public boolean isEmpty() {
        return rigs.isEmpty() && tracks.isEmpty() && keys.isEmpty() && markers.isEmpty();
    }
}
