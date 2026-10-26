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
    /// 普通编辑、撤销、重做与换档都会触发本方法；需要区分“整份换档”时实现
    /// [onSessionSwitched]。本方法保持默认无实现，旧监听者无需改动。
    ///
    /// @param snapshot 新发布的快照（含新序号）；撤销恢复旧内容时序号也是新的
    void onDocumentChanged(AnimationSnapshot snapshot);

    /// 存储完成了一次**整份换档**（主设计 §3.3、§4.5）。
    ///
    /// 与 [onDocumentChanged] 在同一次通知周期内、文档与序号更新之后调用；此时同步提交
    /// 仍被拒绝，监听者只能清理会话状态或使用延迟提交。会话必须按本回调清理草稿、选择、
    /// 交互请求与视图状态，不能让旧文档的对象定位到新文档。
    ///
    /// 默认无实现：不关心换档的监听者只需实现 [onDocumentChanged]。
    ///
    /// @param snapshot 换档后的快照（序号继续单调递增）
    /// @param reason   换档原因
    default void onSessionSwitched(AnimationSnapshot snapshot, SessionSwitch reason) {
    }
}
