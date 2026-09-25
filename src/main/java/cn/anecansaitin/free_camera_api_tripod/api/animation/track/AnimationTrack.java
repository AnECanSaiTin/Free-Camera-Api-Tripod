package cn.anecansaitin.free_camera_api_tripod.api.animation.track;

import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// 动画轨道抽象。
///
/// 新增轨道类型时：实现本接口 -> 通过 {@link TrackTypeRegistry} 注册 -> 加入
/// {@link CameraAnimation}。
@SuppressWarnings("unused")
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

    /// 该时刻的轨道取值；对不产生浮点值的轨道返回 {@link Float#NaN}
    default float valueAt(float time) {
        return Float.NaN;
    }

    /// 曲线轨道暴露底层曲线供图表编辑器使用，其他轨道返回 null
    default @Nullable Curve curve() {
        return null;// todo 是否是必须的，是否可以用泛型
    }
}
