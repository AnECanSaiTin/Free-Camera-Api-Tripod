package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// 一次网络播放动作：开始（带来源与覆盖）或停止（主设计 §8.3）。
///
/// ## 与 [PlaybackRequest] 的分工
///
/// - 本类型是**线上协议**：带来源（随包文档 / 本地引用）与协议版本、规模上限一起传输；
/// - [PlaybackRequest] 是**本地执行**请求：已经持有文档，供 [PlaybackService] 使用。
///
/// 接收端先把 [PlaybackAction] 解码、校验，再由资源服务解析 [PlaybackSource.Local] 得到文档，
/// 用 [Start#toRequest] 生成执行请求。解码失败、来源解析失败、覆盖非法与文档校验失败
/// 都必须转成可报告的请求失败，不得静默构造默认锚点或空文档（§8.3）。
///
/// ## 覆盖三态
///
/// [Start#rigOverrides] 中未出现的 `RigId` 表示该机位完全继承文档；出现的条目用
/// [RigPlaybackOverrides] 表达“继承 / 显式禁用 / 替换”。`AimOverride.Disabled` 与
/// `ObstacleSettings.DISABLED` 是显式禁用，不能与“没有覆盖”混为一谈。
@NullMarked
public sealed interface PlaybackAction permits PlaybackAction.Start, PlaybackAction.Stop {

    /// 开始播放。
    ///
    /// @param source       文档来源
    /// @param rigOverrides 按机位的覆盖；空 map 表示全部继承文档
    /// @param speed        播放速度，有限且 `> 0`
    /// @param loop         是否循环
    /// @param startTime    开始时间，秒；有限且非负
    record Start(
            PlaybackSource source,
            Map<RigId, RigPlaybackOverrides> rigOverrides,
            float speed,
            boolean loop,
            float startTime) implements PlaybackAction {

        public Start {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(rigOverrides, "rigOverrides");

            if (!Float.isFinite(speed) || speed <= 0f) {
                throw new IllegalArgumentException(
                        "PlaybackAction.Start speed must be finite and positive: " + speed);
            }

            if (!Float.isFinite(startTime) || startTime < 0f) {
                throw new IllegalArgumentException(
                        "PlaybackAction.Start startTime must be finite and non-negative: " + startTime);
            }

            rigOverrides = Map.copyOf(rigOverrides);
        }

        /// 生成执行请求。
        ///
        /// @param resolvedAnimation 执行用文档：`Embedded` 必须是同一个随包文档实例；
        ///                          `Local` 由资源服务解析得到
        /// @return 播放请求
        public PlaybackRequest toRequest(CameraAnimation resolvedAnimation) {
            Objects.requireNonNull(resolvedAnimation, "resolvedAnimation");

            if (source instanceof PlaybackSource.Embedded embedded
                    && embedded.animation() != resolvedAnimation) {
                throw new IllegalArgumentException(
                        "Embedded playback must execute the exact embedded animation instance");
            }

            return new PlaybackRequest(resolvedAnimation, rigOverrides, speed, loop, startTime);
        }
    }

    /// 停止播放；不携带来源与覆盖。
    record Stop() implements PlaybackAction {
    }

    /// 开始分支。
    default Optional<Start> start() {
        return this instanceof Start start ? Optional.of(start) : Optional.empty();
    }

    /// 停止分支。
    default boolean stop() {
        return this instanceof Stop;
    }

    /// 覆盖涉及的机位（诊断与规模检查用；按传入顺序去重前的集合，只读）。
    default List<RigId> overriddenRigs() {
        return this instanceof Start start ? List.copyOf(start.rigOverrides().keySet()) : List.of();
    }
}
