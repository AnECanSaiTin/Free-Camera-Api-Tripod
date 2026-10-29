package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.EvaluatedRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback.ObstacleResolver;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 轨道池避障修正：把已组装姿态交给宿主碰撞端口，仅在避障属性启用时修正位置。
///
/// 未启用/未编写避障不调用解析器；启用但 margin 缺失或解析失败属于错误，不退回未修正姿态。
@NullMarked
public final class TrackPoolObstacleCorrector {

    /// 应用避障。
    ///
    /// @param pose      已组装姿态
    /// @param evaluated 属性求值结果
    /// @param origin    射线起点（通常为锚点或基础位置）
    /// @param resolver  宿主碰撞端口
    /// @return 修正后的姿态；未启用时原样返回
    public RigPose apply(RigPose pose,
                         EvaluatedRig evaluated,
                         Vec3 origin,
                         ObstacleResolver resolver) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(evaluated, "evaluated");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(resolver, "resolver");

        if (!evaluated.obstacleEnabled().isValue() || !evaluated.obstacleEnabled().value()) {
            return pose;
        }

        if (!evaluated.obstacleMargin().isValue()) {
            throw new IllegalArgumentException("Obstacle is enabled but margin is absent");
        }

        double margin = evaluated.obstacleMargin().value();

        if (margin < 0) {
            throw new IllegalArgumentException("Obstacle margin must be non-negative: " + margin);
        }

        ObstacleResolver.Result result = resolver.resolve(new ObstacleResolver.Request(
                evaluated.id(), origin, pose.pose().position(), margin));

        return switch (result) {
            case ObstacleResolver.Result.Adjusted adjusted -> new RigPose(
                    pose.rigId(),
                    new WorldPose(adjusted.position(), pose.pose().rotation(), pose.pose().fov()),
                    pose.rect(), pose.zOrder(), pose.resolutionScale(), pose.updateEveryFrames());
            case ObstacleResolver.Result.Unavailable unavailable -> throw new IllegalArgumentException(
                    "Obstacle resolver is unavailable: " + unavailable.reason());
        };
    }
}
