package cn.anecansaitin.free_camera_api_tripod.api.animation.track;

import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// 动画轨道抽象：时间轴、曲线图、关键帧面板都只认这个接口。
///
/// 新增轨道类型时：实现本接口 -> 通过 {@link TrackTypeRegistry} 注册 -> 加入
/// {@link CameraAnimation}。
///
/// 接口只保留所有轨道都成立的成员。曲线、指令、事件这类专属能力不放在这里，而是按能力另开接口或由实现自曝：
/// 需要"播放经过就触发"就再实现 {@link TickTrack}；需要交出底层曲线就在自己的实现里提供（见 {@link CurveTrack#curve()}），
/// 由调用方按能力判断，而不是让每条轨道都去回答一个对自己没有意义的问题。
public interface AnimationTrack {
    /// 轨道类型 id，见 {@link TrackTypeRegistry}
    Identifier type();

    /// 轨道唯一标识（曲线轨道为属性名，例如 "fov"）
    String id();

    /// 显示名称
    Component label();

    /// 显示颜色（ARGB）
    int color();

    /// 该轨道最后一个键的时间
    float duration();

    int keyCount();

    @Nullable
    TrackKey key(int index);

    /// 在指定时间插入一个键（取值取该时刻的当前值），返回键索引，失败返回 -1
    int addKey(float time);

    boolean removeKey(int index);

    /// 将键移动到新的时间，返回新的索引，失败返回 -1
    int moveKey(int index, float newTime);
}
