package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/// 文档存储：所有已发布变更的唯一入口（主设计 §3.3、D8）。
///
/// 提交顺序固定：
///
/// ```text
/// 检查 expectedRevision
/// → 执行纯编辑操作（EditOp）
/// → 校验候选（强制文档校验）
/// → 更新历史与快照
/// → 通知监听者
/// ```
///
/// 约束：
///
/// - 发布必须在宿主指定线程执行；纯内核不依赖客户端线程实现。
/// - 过期提交返回 `STALE_REVISION`，**不得自动覆盖**当前文档。
/// - 文档、历史与发布序号更新完成后才通知监听者。
/// - **监听通知期间禁止重入同步提交**：同步 [submit] 必须返回带
///   [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode#REENTRANT_SUBMIT]
///   的拒绝，不得静默排队或修改文档；需要延后执行的写入使用 [submitAfterNotifications]。
/// - 延迟提交在通知结束后执行，并重新检查基版本；过期同样拒绝。
/// - 初始化、编辑、换档、网络载入与历史恢复均通过本接口。
///
/// 本接口同时是 [AnimationHandle]：读取方只拿快照，写方法只在本接口。
@NullMarked
public interface AnimationDocumentStore extends AnimationHandle {

    /// 同步提交一个纯编辑操作。
    ///
    /// @param submission 操作、基版本与动作标签
    /// @return 成功发布的快照或结构化拒绝诊断
    EditResult submit(EditSubmission submission);

    /// 延迟提交：在当前监听通知结束后执行，并重新检查基版本。
    ///
    /// 通知期间调用本方法不会重入发布；请求进入队列，待通知结束后按提交顺序处理。
    /// 结果通过 [completion] 回调一次；回调在存储线程执行，不得假定在调用线程。
    /// 回调前必须重新检查 `expectedRevision`，过期返回 `STALE_REVISION`，不得自动覆盖。
    ///
    /// @param submission 操作、基版本与动作标签
    /// @param completion 结果回调；恰好调用一次
    /// @return 请求标识，非负；只用于日志 / 取消之外的关联，不进入文档
    long submitAfterNotifications(EditSubmission submission, Consumer<EditResult> completion);

    /// 整份换档。
    ///
    /// 换档清空历史、选择、交互请求与草稿；**发布序号继续由同一存储实例单调递增，
    /// 不回退、不重置为初始值**。载入与恢复仍须走正常解码和校验（主设计 §8.1、§8.4）。
    /// 换档成功后，旧文档的 `expectedRevision` 永久失效：旧请求必须被拒绝，
    /// 不能因为 ID 相同而定位到新文档对象。
    ///
    /// @param document         新文档；须先完成解码与强制校验
    /// @param expectedRevision 当前发布序号
    /// @param reason           换档原因
    EditResult switchDocument(CameraAnimation document, Revision expectedRevision, SessionSwitch reason);

    /// 撤销一步，恢复旧文档并生成新的发布序号。
    EditResult undo(Revision expectedRevision);

    /// 重做一步，生成新的发布序号。
    EditResult redo(Revision expectedRevision);

    /// 撤销 / 重做历史的只读视图。
    UndoHistory history();

    /// 注册发布监听者；通知顺序按注册顺序稳定。
    void addListener(DocumentListener listener);

    /// 移除发布监听者。
    void removeListener(DocumentListener listener);
}
