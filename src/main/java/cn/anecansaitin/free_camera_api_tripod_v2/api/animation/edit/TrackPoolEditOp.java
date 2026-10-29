package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 全局轨道池文档的纯编辑操作。
///
/// 轨道池是唯一轨道存储；新增/替换/改名/删除都直接作用于文档级池。属性绑定、输出与播放范围
/// 各有独立操作，不通过“替换整个机位”隐式修改。应用器负责局部约束，发布边界再做统一校验。
@NullMarked
public sealed interface TrackPoolEditOp permits
        TrackPoolEditOp.AddTrack,
        TrackPoolEditOp.RemoveTrack,
        TrackPoolEditOp.RenameTrack,
        TrackPoolEditOp.ReplaceTrack,
        TrackPoolEditOp.SetRigOutput,
        TrackPoolEditOp.SetRigProperties,
        TrackPoolEditOp.SetCameraSourceBinding,
        TrackPoolEditOp.SetPlaybackRange,
        TrackPoolEditOp.AddRig,
        TrackPoolEditOp.RemoveRig,
        TrackPoolEditOp.RenameRig,
        TrackPoolEditOp.ReplaceDocument {

    /// 加入一条新轨道；TrackId 必须未使用。
    record AddTrack(AnimationTrack track) implements TrackPoolEditOp {
        public AddTrack {
            Objects.requireNonNull(track, "track");
        }
    }

    /// 从池中删除轨道；若仍被属性/来源引用，应用器拒绝。
    record RemoveTrack(TrackId trackId) implements TrackPoolEditOp {
        public RemoveTrack {
            Objects.requireNonNull(trackId, "trackId");
        }
    }

    /// 改显示名；轨道必须实现 `RenamableTrack`。
    record RenameTrack(TrackId trackId, String name) implements TrackPoolEditOp {
        public RenameTrack {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(name, "name");

            if (name.isBlank()) {
                throw new IllegalArgumentException("Track name must not be blank");
            }
        }
    }

    /// 整体替换轨道；替换对象必须保留 TrackId。
    record ReplaceTrack(TrackId trackId, AnimationTrack replacement) implements TrackPoolEditOp {
        public ReplaceTrack {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(replacement, "replacement");

            if (!trackId.equals(replacement.id())) {
                throw new IllegalArgumentException("Replacement track must preserve TrackId");
            }
        }
    }

    /// 修改机位静态输出配置。
    record SetRigOutput(RigId rigId, RigOutput output) implements TrackPoolEditOp {
        public SetRigOutput {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(output, "output");
        }
    }

    /// 修改机位属性绑定表。
    record SetRigProperties(RigId rigId, RigProperties properties) implements TrackPoolEditOp {
        public SetRigProperties {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(properties, "properties");
        }
    }

    /// 修改文档级相机来源绑定。
    record SetCameraSourceBinding(PropertyBinding<CameraSourceProperty> binding)
            implements TrackPoolEditOp {
        public SetCameraSourceBinding {
            Objects.requireNonNull(binding, "binding");
        }
    }

    /// 修改播放范围。
    record SetPlaybackRange(PlaybackRange range) implements TrackPoolEditOp {
        public SetPlaybackRange {
            Objects.requireNonNull(range, "range");
        }
    }

    /// 加入一台机位；RigId 必须未使用。
    record AddRig(PooledRig rig) implements TrackPoolEditOp {
        public AddRig {
            Objects.requireNonNull(rig, "rig");
        }
    }

    /// 删除一台机位；文档至少保留一台，且不得删除仍被相机来源引用的机位。
    record RemoveRig(RigId rigId) implements TrackPoolEditOp {
        public RemoveRig {
            Objects.requireNonNull(rigId, "rigId");
        }
    }

    /// 改机位显示名。
    record RenameRig(RigId rigId, String name) implements TrackPoolEditOp {
        public RenameRig {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(name, "name");

            if (name.isBlank()) {
                throw new IllegalArgumentException("Rig name must not be blank");
            }
        }
    }

    /// 便于构造新文档的操作。
    record ReplaceDocument(TrackPoolAnimation document) implements TrackPoolEditOp {
        public ReplaceDocument {
            Objects.requireNonNull(document, "document");
        }
    }
}
