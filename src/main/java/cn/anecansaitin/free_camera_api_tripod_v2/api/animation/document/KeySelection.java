package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一个关键帧选择：轨道 + 键的稳定 ID（主设计 §5.3）。
///
/// 选择保存稳定 ID，不保存轨道对象或集合下标；版本变化时在新文档中解析选择，
/// 对象不存在则清除。移动、排序和重命名保留选择；整份换档清空选择。
///
/// @param trackId 键所属轨道
/// @param keyId   键身份
@NullMarked
public record KeySelection(TrackId trackId, KeyId keyId) {

    public KeySelection {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(keyId, "keyId");
    }
}
