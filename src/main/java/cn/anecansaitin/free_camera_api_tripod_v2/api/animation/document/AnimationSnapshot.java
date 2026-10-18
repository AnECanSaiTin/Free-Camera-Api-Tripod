package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一次发布的不可变快照：匹配的文档与发布序号（主设计 §3.3，不变量 I3）。
///
/// 实现以一个 `volatile` 引用保存当前快照；**一次读取**同时得到文档与序号，
/// 不存在“先读文档、再读序号”的撕裂读法（不变量 I3）。
///
/// @param revision 发布序号；同一存储实例内单调递增
/// @param document 已发布且通过强制校验的文档
@NullMarked
public record AnimationSnapshot(Revision revision, CameraAnimation document) {

    public AnimationSnapshot {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(document, "document");
    }
}
