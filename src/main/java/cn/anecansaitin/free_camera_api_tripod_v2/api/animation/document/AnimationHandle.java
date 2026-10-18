package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import org.jspecify.annotations.NullMarked;

/// 只读文档句柄：任何消费者都只从这里读取已发布快照（主设计 §3.3）。
///
/// 本接口**不提供发布方法**：所有变更必须经过 [AnimationDocumentStore]，
/// 否则“校验 → 发布 → 历史 → 通知”的顺序无法保证。文档、历史与发布序号更新完成后
/// 才通知监听者（[DocumentListener]）。
@NullMarked
public interface AnimationHandle {

    /// 当前已发布快照；每次调用返回同一时刻的文档与序号对。
    AnimationSnapshot snapshot();
}
