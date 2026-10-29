package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.EvaluatedRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.core.math.CoreRotationMath;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 把一帧属性求值结果应用到宿主基础姿态与默认矩形。
///
/// 世界空间直接使用基础姿态与世界坐标；局部空间先以锚点姿态解释位置/旋转，再合成回世界。
/// 未接管的属性继续沿用基础姿态。错误结果应在帧准备阶段被拒绝，本类只做最后组装与宽高
/// 正数检查。
@NullMarked
public final class TrackPoolPoseBuilder {

    /// 世界空间组装。
    public RigPose build(EvaluatedRig evaluated, WorldPose basePose, ResolvedRect defaultRect) {
        return build(evaluated, null, basePose, defaultRect, null);
    }

    /// 按机位空间组装；局部空间必须提供锚点姿态。
    ///
    /// @param evaluated   已求值属性
    /// @param rig         机位静态配置；为空时按世界空间处理
    /// @param basePose    宿主基础姿态
    /// @param defaultRect 未接管分量的默认矩形
    /// @param anchorPose  锚点世界姿态；世界空间忽略，局部空间必须非空
    public RigPose build(EvaluatedRig evaluated,
                         @Nullable PooledRig rig,
                         WorldPose basePose,
                         ResolvedRect defaultRect,
                         @Nullable WorldPose anchorPose) {
        Objects.requireNonNull(evaluated, "evaluated");
        Objects.requireNonNull(basePose, "basePose");
        Objects.requireNonNull(defaultRect, "defaultRect");

        Space space = rig == null ? new Space.World() : rig.space();
        Rotation localRotation = evaluated.rotation().isValue()
                ? evaluated.rotation().value().toEuler()
                : basePose.rotation();
        float fov = evaluated.fov().isValue() ? evaluated.fov().value() : basePose.fov();
        ResolvedRect rect = rect(evaluated.rect(), defaultRect);

        Vec3 worldPosition;
        Rotation worldRotation;

        if (space instanceof Space.Local local) {
            WorldPose anchor = anchorPose;

            if (anchor == null) {
                throw new IllegalArgumentException("Local space requires an anchor pose");
            }

            Rotation anchorRotation = local.followMask().any()
                    ? anchor.rotation().masked(local.followMask())
                    : Rotation.NONE;
            boolean positionBound = evaluated.x().isValue()
                    || evaluated.y().isValue()
                    || evaluated.z().isValue();

            if (positionBound) {
                Vec3 baseLocal = CoreRotationMath.rotateInverse(anchorRotation,
                        basePose.position().subtract(anchor.position()));
                double x = valueOr(evaluated.x(), baseLocal.x());
                double y = valueOr(evaluated.y(), baseLocal.y());
                double z = valueOr(evaluated.z(), baseLocal.z());
                worldPosition = anchor.position().add(
                        CoreRotationMath.rotate(anchorRotation, new Vec3(x, y, z)));
            } else {
                worldPosition = basePose.position();
            }

            worldRotation = evaluated.rotation().isValue()
                    ? CoreRotationMath.compose(anchorRotation, localRotation)
                    : basePose.rotation();
        } else {
            double x = valueOr(evaluated.x(), basePose.position().x());
            double y = valueOr(evaluated.y(), basePose.position().y());
            double z = valueOr(evaluated.z(), basePose.position().z());
            worldPosition = new Vec3(x, y, z);
            worldRotation = localRotation;
        }

        return new RigPose(evaluated.id(),
                new WorldPose(worldPosition, worldRotation, fov),
                rect,
                evaluated.output().zOrder(),
                evaluated.output().resolutionScale(),
                evaluated.output().updateEveryFrames());
    }

    private static double valueOr(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample sample,
                                  double fallback) {
        return sample.isValue() ? sample.value() : fallback;
    }

    private static ResolvedRect rect(EvaluatedRig.EvaluatedRect evaluated, ResolvedRect fallback) {
        boolean sameKind = switch (evaluated.kind()) {
            case NORMALIZED -> fallback instanceof ResolvedRect.Normalized;
            case ANCHORED -> fallback instanceof ResolvedRect.Anchored;
        };

        if (!sameKind) {
            throw new IllegalArgumentException(
                    "Default rect branch must match the evaluated rect branch: "
                            + evaluated.kind());
        }

        double x = valueOr(evaluated.x(), fallbackValue(fallback, Component.X));
        double y = valueOr(evaluated.y(), fallbackValue(fallback, Component.Y));
        double width = valueOr(evaluated.width(), fallbackValue(fallback, Component.WIDTH));
        double height = valueOr(evaluated.height(), fallbackValue(fallback, Component.HEIGHT));

        return switch (evaluated.kind()) {
            case NORMALIZED -> new ResolvedRect.Normalized(x, y, width, height);
            case ANCHORED -> new ResolvedRect.Anchored(
                    Objects.requireNonNull(evaluated.corner(), "corner"), x, y, width, height);
        };
    }

    private static double fallbackValue(ResolvedRect rect, Component component) {
        return switch (rect) {
            case ResolvedRect.Normalized normalized -> switch (component) {
                case X -> normalized.x();
                case Y -> normalized.y();
                case WIDTH -> normalized.width();
                case HEIGHT -> normalized.height();
            };
            case ResolvedRect.Anchored anchored -> switch (component) {
                case X -> anchored.x();
                case Y -> anchored.y();
                case WIDTH -> anchored.width();
                case HEIGHT -> anchored.height();
            };
        };
    }

    /// 四元数转 YXZ 欧拉角，弧度。
    static Rotation toEuler(Quaternion quaternion) {
        return quaternion.toEuler();
    }

    private enum Component {
        X,
        Y,
        WIDTH,
        HEIGHT
    }
}
