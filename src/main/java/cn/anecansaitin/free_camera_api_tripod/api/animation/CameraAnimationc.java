package cn.anecansaitin.free_camera_api_tripod.api.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.SymbolTablec;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Pathc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 相机动画的只读视图。
///
/// 播放器、渲染与信息面板这类只读取动画内容的地方拿这个接口即可，
/// 增删通道、切换模式、换路径由 {@link CameraAnimation} 提供。
@NullMarked
public interface CameraAnimationc {
    String name();

    /// 动画总时长：曲线通道与扩展轨道里最靠后的键的时间
    float duration();

    /// 位置的运动模式，见 {@link CameraAnimation.MotionMode}
    CameraAnimation.MotionMode motionMode();

    /// 路径模式下「位置通道」的取值口径，见 {@link CameraAnimation.DistanceMode}
    CameraAnimation.DistanceMode distanceMode();

    /// 当前绑定的路径
    Pathc path();

    /// 全部轨道，顺序即时间轴上的显示顺序（曲线通道与扩展轨道共用一份有序表）
    List<? extends AnimationTrack> tracks();

    /// 曲线轨道
    List<CurveTrack> curveTracks();

    /// 扩展轨道（指令、事件、特效等非曲线轨道）
    List<? extends AnimationTrack> extensionTracks();

    /// 按 id 取曲线轨道的底层曲线；该 id 不是曲线轨道时返回 null。
    ///
    /// 曲线只存一份（就在 {@link CurveTrack} 里），这是唯一的按名入口——求值层与播放器都走它，
    /// 不必再从轨道表里过滤一遍
    @Nullable Curve curve(String id);

    /// 符号表（只读）：动画里定义的变量与自定义函数，见 {@link SymbolTablec}
    SymbolTablec symbols();

    /// 把「位置通道」的取值换算成沿路径的弧长（绝对距离）
    float distanceToLength(float value);
}
