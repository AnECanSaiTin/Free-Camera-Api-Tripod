package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult;
import org.jspecify.annotations.NullMarked;

/// 文档存储：所有已发布变更的唯一入口（主设计 §3.3、D8）。
///
/// 提交顺序固定：
///
/// ```text
/// 检查 expectedRevision
/// → 执行纯编辑操作（[EditOp]）
/// → 校验候选（强制文档校验）
/// → 更新历史与快照
/// → 通知监听者
/// ```
///
/// 约束：
///
/// - 发布必须在宿主指定线程执行；纯内核不依赖客户端线程实现。
/// - 过期提交返回 `STALE_REVISION`，**不得自动覆盖**当前文档。
/// - 文档、历史与发布序号更新完成后才通知监听者；通知期间禁止重入发布，后续请求排队。
/// - 初始化、编辑、换档、网络载入与历史恢复均通过本接口。
///
/// 本接口同时是 [AnimationHandle]：读取方只拿快照，写方法只在本接口。
@NullMarked
public interface AnimationDocumentStore extends AnimationHandle {

    /// 提交一个纯编辑操作。
    ///
    /// @param op               操作；不得修改输入文档，不得执行 IO
    /// @param expectedRevision 调用方读取快照时的发布序号
    /// @return 成功发布的快照或结构化拒绝诊断
    EditResult submit(EditOp op, Revision expectedRevision);

    /// 整份换档。
    ///
    /// 换档清空历史、选择、交互请求与草稿，并重新建立发布序号序列；
    /// 载入与恢复仍须走正常解码和校验（主设计 §8.1、§8.4）。
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
