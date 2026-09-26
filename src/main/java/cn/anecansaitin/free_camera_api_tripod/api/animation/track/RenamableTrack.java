package cn.anecansaitin.free_camera_api_tripod.api.animation.track;

import org.jspecify.annotations.NullMarked;

/// 可以改标识的轨道，供编辑器重命名。
///
/// 与 {@link TickTrack}、{@link JsonTrack} 一样是按能力拆出来的可选接口：
/// 曲线轨道的标识与相机属性绑定，改名就找不到它对应的通道了，因此不实现本接口，
/// 编辑器也只对实现了本接口的轨道开放重命名。
@NullMarked
public interface RenamableTrack {
    /// 换一个轨道标识；调用方负责保证不与同动画里的其它轨道重名
    void rename(String id);
}
