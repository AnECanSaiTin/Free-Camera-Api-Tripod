package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.EvaluatedRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrame;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback.ObstacleResolver;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 轨道池帧合成：把求值帧、机位静态配置、宿主基础姿态与锚点组合成可交付的 [RigPose]。
///
/// 这是无客户端依赖的最后一层组装：不读取 store、不查询世界、不绘制。避障端口可选；提供时
/// 仅对启用避障的机位调用。
@NullMarked
public final class TrackPoolFrameComposer {

    /// 合成一帧。
    ///
    /// @param frame      已准备帧
    /// @param rigs       机位静态配置，按 RigId
    /// @param basePoses  宿主基础姿态，按 RigId
    /// @param defaultRects 默认矩形，按 RigId
    /// @param anchors    锚点姿态，按 RigId；世界空间可为空
    /// @param obstacleResolver 可选避障端口
    /// @return 与帧内机位顺序一致的姿态
    public List<RigPose> compose(TrackPoolFrame frame,
                                 Map<RigId, PooledRig> rigs,
                                 Map<RigId, WorldPose> basePoses,
                                 Map<RigId, ResolvedRect> defaultRects,
                                 Map<RigId, WorldPose> anchors,
                                 @Nullable ObstacleResolver obstacleResolver) {
        return compose(frame, rigs, basePoses, defaultRects, anchors, obstacleResolver, false);
    }

    /// 只合成输出已启用的机位；默认关闭的机位不产生姿态。
    public List<RigPose> composeVisible(TrackPoolFrame frame,
                                        Map<RigId, PooledRig> rigs,
                                        Map<RigId, WorldPose> basePoses,
                                        Map<RigId, ResolvedRect> defaultRects,
                                        Map<RigId, WorldPose> anchors,
                                        @Nullable ObstacleResolver obstacleResolver) {
        return compose(frame, rigs, basePoses, defaultRects, anchors, obstacleResolver, true);
    }

    private List<RigPose> compose(TrackPoolFrame frame,
                                  Map<RigId, PooledRig> rigs,
                                  Map<RigId, WorldPose> basePoses,
                                  Map<RigId, ResolvedRect> defaultRects,
                                  Map<RigId, WorldPose> anchors,
                                  @Nullable ObstacleResolver obstacleResolver,
                                  boolean visibleOnly) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(rigs, "rigs");
        Objects.requireNonNull(basePoses, "basePoses");
        Objects.requireNonNull(defaultRects, "defaultRects");
        Objects.requireNonNull(anchors, "anchors");

        TrackPoolPoseBuilder builder = new TrackPoolPoseBuilder();
        TrackPoolObstacleCorrector corrector = new TrackPoolObstacleCorrector();
        List<RigPose> result = new ArrayList<>();

        for (EvaluatedRig evaluated : frame.rigs()) {
            if (visibleOnly && !evaluated.output().enabled()) {
                continue;
            }

            PooledRig rig = require(rigs, evaluated.id(), "rig");
            WorldPose base = require(basePoses, evaluated.id(), "base pose");
            ResolvedRect rect = require(defaultRects, evaluated.id(), "default rect");
            WorldPose anchor = anchors.get(evaluated.id());
            RigPose pose = builder.build(evaluated, rig, base, rect, anchor);

            if (obstacleResolver != null) {
                Vec3 origin = anchor == null ? base.position() : anchor.position();
                pose = corrector.apply(pose, evaluated, origin, obstacleResolver);
            }

            result.add(pose);
        }

        return List.copyOf(result);
    }

    private static <T> T require(Map<RigId, T> map, RigId id, String name) {
        T value = map.get(id);

        if (value == null) {
            throw new IllegalArgumentException("Missing " + name + " for rig: " + id);
        }

        return value;
    }
}
