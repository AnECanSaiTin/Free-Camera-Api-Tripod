package cn.anecansaitin.free_camera_api_tripod.api.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Pathc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import org.jspecify.annotations.NullMarked;

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

    /// 变量表：表达式里按名字引用，值取自它绑定的曲线轨道
    List<Variable> variables();

    /// 把「位置通道」的取值换算成沿路径的弧长（绝对距离）
    float distanceToLength(float value);
}
