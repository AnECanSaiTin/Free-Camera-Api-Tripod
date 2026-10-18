package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 一帧准备结果：时钟、相机来源、活动机位姿态、待执行事件与帧事实（主设计 §7.2）。
///
/// 准备阶段先在**同一事实快照**上纯求值，全部当前活动机位通过检查后才进入执行阶段；
/// 任一路失败时整帧不执行、不应用部分新姿态、不执行本帧事件。
///
/// `rigs` 即本帧活动集合（顺序为文档机位顺序）；矩形与姿态都在这里，渲染宿主只消费、
/// 不重新求值。`events` 只在这一帧执行一次；定位、暂停、拖动与试算不产生事件。
///
/// @param clock       本帧共享时钟
/// @param source      生效的玩家相机来源
/// @param rigs        本帧活动机位姿态与矩形
/// @param events      待执行文档事件；按文档轨道顺序
/// @param facts       本帧冻结的世界与空间事实
/// @param diagnostics 诊断；存在 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity#ERROR]
///                    时整帧不得执行
@NullMarked
public record PreparedFrame(
        PlaybackClock clock,
        CameraSource source,
        List<RigPose> rigs,
        List<Event> events,
        FrameFacts facts,
        List<Diagnostic> diagnostics) {

    public PreparedFrame {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(facts, "facts");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        rigs = List.copyOf(rigs);
        events = List.copyOf(events);
        diagnostics = List.copyOf(diagnostics);
    }

    /// 是否没有错误级诊断，可进入执行阶段。
    public boolean isExecutable() {
        return diagnostics.stream().noneMatch(diagnostic ->
                diagnostic.severity() == cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity.ERROR);
    }

    /// 一个待执行文档事件：事件数据在轨道里，执行器按 `TrackId` 解析。
    ///
    /// @param trackId 事件轨道
    /// @param time    事件发生时间，秒；有限且非负
    public record Event(TrackId trackId, float time) {

        public Event {
            Objects.requireNonNull(trackId, "trackId");

            if (!Float.isFinite(time) || time < 0f) {
                throw new IllegalArgumentException(
                        "PreparedFrame.Event time must be finite and non-negative: " + time);
            }
        }
    }
}
