package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import org.jspecify.annotations.NullMarked;

/// 文档发布监听者（主设计 §3.3、§4.5）。
///
/// 通知发生在文档、历史与发布序号都更新之后。通知期间**禁止重入同步提交**：
/// [AnimationDocumentStore#submit] 必须返回 `REENTRANT_SUBMIT` 拒绝，不得静默排队；
/// 需要继续写入时使用 [AnimationDocumentStore#submitAfterNotifications]，由存储在通知结束后
/// 重新检查基版本并执行。监听者可能收到自己触发的通知，必须按快照身份重新读取，不能缓存旧文档。
@NullMarked
@FunctionalInterface
public interface DocumentListener {

    /// 一次发布已完成。
    ///
    /// @param snapshot 新发布的快照（含新序号）；撤销恢复旧内容时序号也是新的
    void onDocumentChanged(AnimationSnapshot snapshot);
}
