package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// 事件执行器注册表：轨道类型 ID → 执行器（主设计 §7.4）。
///
/// 宿主在运行前冻结注册；同一类型只能有一个执行器，重复注册必须拒绝。内建
/// `curve` / `aim` / `active` / `camera_source` 不是 Tick 事件类型，不能被注册为事件执行器。
/// 执行器只处理对应类型的事件，不得绕过帧快照或读取当前 store。
@NullMarked
public record EventExecutorRegistry(List<Entry> entries) {

    public EventExecutorRegistry {
        // 防御性复制：不保留调用方容器（主设计 §3.2）
        entries = List.copyOf(entries);

        Set<Identifier> seen = new HashSet<>();
        for (Entry entry : entries) {
            if (!seen.add(entry.trackType())) {
                throw new IllegalArgumentException(
                        "Duplicate event executor for track type: " + entry.trackType());
            }
        }
    }

    /// 按轨道类型查找执行器。
    public Optional<EventExecutor> find(Identifier trackType) {
        Objects.requireNonNull(trackType, "trackType");

        return entries.stream()
                .filter(entry -> entry.trackType().equals(trackType))
                .map(Entry::executor)
                .findFirst();
    }

    /// 一条注册：轨道类型与执行器。
    ///
    /// @param trackType 扩展轨道类型 ID；不得为内建保留 ID
    /// @param executor  执行器
    public record Entry(Identifier trackType, EventExecutor executor) {

        public Entry {
            Objects.requireNonNull(trackType, "trackType");
            Objects.requireNonNull(executor, "executor");

            if (trackType.equals(CurveTrack.TYPE)
                    || trackType.equals(AimTrack.TYPE)
                    || trackType.equals(ActiveTrack.TYPE)
                    || trackType.equals(CameraSourceTrack.TYPE)) {
                throw new IllegalArgumentException(
                        "Built-in track type cannot register an event executor: " + trackType);
            }
        }
    }
}
