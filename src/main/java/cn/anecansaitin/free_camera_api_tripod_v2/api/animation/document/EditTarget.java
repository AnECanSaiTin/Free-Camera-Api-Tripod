package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.AudioRefId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.OverlayId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.PathNodeId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 编辑事务 / 草稿的稳定目标（主设计 §5.1、§5.3）。
///
/// 目标保存**稳定 ID**，不保存对象、下标或依赖当前快照的临时引用；面板重建、活动机位切换
/// 或后端切换都不得丢失草稿（主设计 §5.2）。基版本变化时事务标记为过期，禁止自动覆盖。
@NullMarked
public sealed interface EditTarget permits EditTarget.Document,
        EditTarget.Rig, EditTarget.Track, EditTarget.Key, EditTarget.Marker, EditTarget.Sample,
        EditTarget.Audio, EditTarget.Overlay, EditTarget.Variable, EditTarget.Function,
        EditTarget.PathNode, EditTarget.Field {

    /// 文档级字段（名称、`dataVersion` 等）。
    record Document() implements EditTarget {
    }

    /// 某台机位。
    record Rig(RigId rigId) implements EditTarget {
        public Rig {
            Objects.requireNonNull(rigId, "rigId");
        }
    }

    /// 某条轨道（机位通道、状态轨道或文档级轨道）。
    record Track(TrackId trackId) implements EditTarget {
        public Track {
            Objects.requireNonNull(trackId, "trackId");
        }
    }

    /// 某条轨道上的某个键。
    record Key(TrackId trackId, KeyId keyId) implements EditTarget {
        public Key {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(keyId, "keyId");
        }
    }

    /// 文档级标记。
    record Marker(MarkerId markerId) implements EditTarget {
        public Marker {
            Objects.requireNonNull(markerId, "markerId");
        }
    }

    /// 世界采样声明。
    record Sample(SampleId sampleId) implements EditTarget {
        public Sample {
            Objects.requireNonNull(sampleId, "sampleId");
        }
    }

    /// 参考音轨引用。
    record Audio(AudioRefId audioRefId) implements EditTarget {
        public Audio {
            Objects.requireNonNull(audioRefId, "audioRefId");
        }
    }

    /// 成片叠加元素。
    record Overlay(OverlayId overlayId) implements EditTarget {
        public Overlay {
            Objects.requireNonNull(overlayId, "overlayId");
        }
    }

    /// 符号表变量（名字即稳定目标；改名是显式编辑操作）。
    record Variable(String name) implements EditTarget {
        public Variable {
            if (name.isBlank()) {
                throw new IllegalArgumentException("EditTarget.Variable name must not be blank");
            }
        }
    }

    /// 自定义函数（名字即稳定目标）。
    record Function(String name) implements EditTarget {
        public Function {
            if (name.isBlank()) {
                throw new IllegalArgumentException("EditTarget.Function name must not be blank");
            }
        }
    }

    /// 某台机位路径上的某个节点。
    record PathNode(RigId rigId, PathNodeId nodeId) implements EditTarget {
        public PathNode {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(nodeId, "nodeId");
        }
    }

    /// 对象上的一个类型化字段，用于区分同一对象上的不同草稿字段。
    ///
    /// 不把字段目标压成裸字符串或下标：`owner` 保留对象种类，`fieldPath` 只在该对象内解释。
    /// 字段目标不能再嵌套字段目标；字段路径非空白。
    ///
    /// @param owner     拥有该字段的稳定目标
    /// @param fieldPath 对象内字段路径，例如 `view.rect.width`
    record Field(EditTarget owner, String fieldPath) implements EditTarget {
        public Field {
            Objects.requireNonNull(owner, "owner");

            if (owner instanceof Field) {
                throw new IllegalArgumentException("EditTarget.Field cannot own another Field target");
            }

            if (fieldPath.isBlank()) {
                throw new IllegalArgumentException("EditTarget.Field fieldPath must not be blank");
            }
        }
    }
}
