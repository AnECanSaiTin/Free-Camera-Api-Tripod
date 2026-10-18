package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 一帧准备结果：时钟、捕获快照、相机来源、活动机位姿态、待执行事件与帧事实
/// （主设计 §7.2）。
///
/// 准备阶段先在**同一事实快照**上纯求值，全部当前活动机位通过检查后才进入执行阶段；
/// 任一路失败时整帧不执行、不应用部分新姿态、不执行本帧事件。
///
/// `snapshot` 是本帧姿态、层级、质量请求与事件解析共同绑定的不可变文档快照。执行阶段
/// 必须使用该快照解析事件载荷，**不得重新读取当前 store**：同帧可能已发布 S2，否则会出现
/// S1 姿态配 S2 事件。`rigs` 即本帧活动集合（顺序为文档机位顺序）；矩形与姿态都在这里，
/// 渲染宿主只消费、不重新求值。
///
/// `events` 的顺序固定为：
///
/// 1. 跨区间 / 周期按推进顺序；
/// 2. 同一区间内按事件时间递增；
/// 3. 同一发生时刻按文档轨道顺序。
///
/// 执行器原样消费该序列，不再全局按轨道排序。定位、暂停、拖动与试算不产生事件。
///
/// @param clock       本帧共享时钟
/// @param snapshot    捕获的不可变文档快照；事件载荷解析必须绑定它
/// @param source      生效的玩家相机来源
/// @param rigs        本帧活动机位姿态、矩形、层级与质量请求
/// @param events      待执行文档事件，顺序见上
/// @param facts       本帧冻结的世界与空间事实
/// @param diagnostics 诊断；存在 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity#ERROR]
///                    时整帧不得执行
@NullMarked
public record PreparedFrame(
        PlaybackClock clock,
        AnimationSnapshot snapshot,
        CameraSource source,
        List<RigPose> rigs,
        List<Event> events,
        FrameFacts facts,
        List<Diagnostic> diagnostics) {

    public PreparedFrame {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(snapshot, "snapshot");
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

    /// 一个待执行文档事件：事件数据在轨道里，执行器按 `TrackId` 从 [snapshot] 解析。
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
