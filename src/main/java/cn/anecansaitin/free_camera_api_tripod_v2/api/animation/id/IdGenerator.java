package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id;

import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/// 为新建立的文档对象取一个未被占用的稳定 ID。
///
/// 由宿主注入：ID 生成需要随机源或计数器，属于宿主职责，纯内核不持有全局状态。
/// 需要它的操作是**复制**类操作（复制关键帧、复制机位、复制叠加元素等）——
/// 复制必须生成新 ID，而移动、排序、重命名一律保留原 ID（主设计 §3.1）。
///
/// 实现必须返回当前文档中尚未被同类型对象占用的取值；调用方在构造候选后仍要经过
/// L2 结构校验（唯一性），因此生成器不是唯一性保证者，校验器才是。
@NullMarked
public interface IdGenerator {

    /// 为指定 ID 类型生成一个新取值。
    ///
    /// @param type 目标 ID 类型，见 {@link DocumentObjectId} 的实现
    /// @return 新的 ID 取值，非空且非空白
    String nextId(Class<? extends DocumentObjectId> type);

    /// 默认实现：按 `前缀-UUID` 生成，便于人工阅读存档时定位对象。
    ///
    /// UUID 只用于避免碰撞，不代表任何顺序语义；ID 的大小顺序**不可**用于排序，
    /// 文档顺序由集合自身顺序表达（主设计 §3.1：机位顺序、通道顺序与轨道顺序均持久化）。
    static IdGenerator random() {
        return type -> {
            String prefix = switch (type.getSimpleName()) {
                case "RigId" -> "rig";
                case "TrackId" -> "track";
                case "KeyId" -> "key";
                case "PathNodeId" -> "node";
                case "MarkerId" -> "marker";
                case "SampleId" -> "sample";
                case "AudioRefId" -> "audio";
                case "OverlayId" -> "overlay";
                default -> "id";
            };

            return prefix + "-" + UUID.randomUUID();
        };
    }
}
