package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsPlan;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 从快照、动画时间与播放覆盖生成逐机位空间计划。
///
/// 这是 A4 帧准备的前半段：先按时间求值文档注视轨道，再应用 `AimOverride` 三态，
/// 最后把“锚点需求 / 目标需求 / 生效注视”写进 [FrameFactsPlan]。它不解析世界坐标、
/// 不读当前 store、不决定哪些机位实际参与渲染；调用方必须显式给出需要事实的机位集合。
///
/// 局部空间没有生效锚点（文档默认与请求覆盖都为空）时直接拒绝：这是开始播放前的缺输入，
/// 不能悄悄退回世界空间或伪造坐标。
@NullMarked
public final class FrameFactsPlanBuilder {

    private FrameFactsPlanBuilder() {
    }

    /// 生成计划。
    ///
    /// @param snapshot      当前快照；机位与注视轨道从同一快照读取
    /// @param animationTime 动画时间，秒；有限非负
    /// @param overrides     按机位覆盖；未知机位拒绝
    /// @param rigs          本帧需要空间事实的机位；未知机位拒绝
    public static FrameFactsPlan build(AnimationSnapshot snapshot,
                                       float animationTime,
                                       Map<RigId, RigPlaybackOverrides> overrides,
                                       Collection<RigId> rigs) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(overrides, "overrides");
        Objects.requireNonNull(rigs, "rigs");

        if (!Float.isFinite(animationTime) || animationTime < 0f) {
            throw new IllegalArgumentException(
                    "animationTime must be finite and non-negative: " + animationTime);
        }

        for (RigId rigId : overrides.keySet()) {
            requireRig(snapshot, rigId, "override");
        }

        Set<RigId> requested = new LinkedHashSet<>(rigs);

        for (RigId rigId : requested) {
            requireRig(snapshot, rigId, "plan");
        }

        if (requested.isEmpty()) {
            return FrameFactsPlan.empty();
        }

        Map<RigId, FrameFactsPlan.RigDemand> demands = new java.util.LinkedHashMap<>();

        for (RigId rigId : requested) {
            CameraRig rig = requireRig(snapshot, rigId, "plan");
            RigPlaybackOverrides override = overrides.getOrDefault(rigId, RigPlaybackOverrides.INHERIT);
            AimState aim = effectiveAim(rig, animationTime, override.aim());

            FrameFactsPlan.SpatialDemand<Anchor> anchor = anchorDemand(rig, override.anchor());
            FrameFactsPlan.SpatialDemand<AimTarget> target = aim.targetOptional()
                    .<FrameFactsPlan.SpatialDemand<AimTarget>>map(FrameFactsPlan.SpatialDemand::required)
                    .orElseGet(FrameFactsPlan.SpatialDemand::notRequired);

            demands.put(rigId, new FrameFactsPlan.RigDemand(anchor, target, aim));
        }

        return new FrameFactsPlan(demands);
    }

    private static AimState effectiveAim(CameraRig rig, float animationTime, AimOverride override) {
        return switch (override) {
            case AimOverride.Inherit ignored -> rig.aim().at(animationTime);
            case AimOverride.Disabled ignored -> AimState.off();
            case AimOverride.Replace replace -> {
                if (replace.target().mask().z()) {
                    throw new IllegalArgumentException(
                            "Playback aim override must not enable Roll (z mask)");
                }

                yield AimState.target(replace.target());
            }
        };
    }

    private static FrameFactsPlan.SpatialDemand<Anchor> anchorDemand(CameraRig rig,
                                                                    @Nullable Anchor overrideAnchor) {
        if (!(rig.space() instanceof Space.Local local)) {
            return FrameFactsPlan.SpatialDemand.notRequired();
        }

        Anchor anchor = overrideAnchor != null ? overrideAnchor : local.defaultAnchor();

        if (anchor == null) {
            throw new IllegalArgumentException(
                    "Local rig " + rig.id() + " requires an anchor from the document or playback request");
        }

        return FrameFactsPlan.SpatialDemand.required(anchor);
    }

    private static CameraRig requireRig(AnimationSnapshot snapshot, RigId rigId, String role) {
        return snapshot.document().rig(rigId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "FrameFactsPlan " + role + " references a rig missing from the snapshot: " + rigId));
    }
}
