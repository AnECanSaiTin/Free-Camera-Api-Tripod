package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditSubmission;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTransaction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.PreviewState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackState;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 编辑器会话：提交、草稿 / 预览、选择、播放控制、交互请求与关闭（主设计 §5、§9）。
///
/// 会话绑定一份已发布快照；文档存储与播放内核由宿主注入，不由屏幕构造私有副本。界面后端
/// 切换复用会话；关闭屏幕与关闭会话的生命周期必须区分。面板只读取状态与提交意图，不直接
/// 修改文档。
///
/// 交互请求过期、已关闭或选项无效时必须拒绝，不能执行旧回调。所有方法限宿主主线程。
@NullMarked
public interface EditorSession {

    /// 当前已发布快照。
    AnimationSnapshot snapshot();

    /// 同步提交一个完整操作 / 事务结果。
    EditResult submit(EditSubmission submission);

    /// 开始一个基于稳定目标的事务。
    EditTransaction beginEdit(EditTarget target, LocalizedMessage label);

    /// 当前预览候选与诊断。
    PreviewState preview();

    /// 提交事务；成功仅产生一个撤销项。
    EditResult commit(EditTransaction transaction);

    /// 取消事务，丢弃候选并恢复已发布快照预览。
    void cancel(EditTransaction transaction);

    /// 撤销一步。
    EditResult undo();

    /// 重做一步。
    EditResult redo();

    /// 当前播放状态（正式播放 / 预览播放共用状态语义）。
    PlaybackState playbackState();

    /// 当前播放时钟。
    PlaybackClock playbackClock();

    /// 定位播放头；不发事件、不修改文档。
    void seek(float time);

    /// 显式开始播放。
    void play();

    /// 暂停推进；世界不冻结。
    void pause();

    /// 当前会话视图状态。
    SessionViewState viewState();

    /// 替换会话视图状态；不写文档、不进撤销历史。
    void updateViewState(SessionViewState state);

    /// 当前待处理交互请求；顺序稳定。
    List<InteractionRequest> interactions();

    /// 选择一个交互选项；过期 / 已关闭 / 无效选项必须拒绝。
    void choose(InteractionId requestId, String optionId);

    /// 取消一个交互请求。
    void cancelInteraction(InteractionId requestId);

    /// 关闭会话并释放会话资源；不隐式关闭共享文档存储之外的对象。
    void close();
}
