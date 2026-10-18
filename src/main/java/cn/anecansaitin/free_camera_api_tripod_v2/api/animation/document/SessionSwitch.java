package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import org.jspecify.annotations.NullMarked;

/// 整份换档的原因（主设计 §3.3、§4.5、§8.4）。
///
/// 换档会清空历史、选择、交互请求与草稿，并提示用户；**发布序号仍由同一存储实例单调递增**，
/// 不重置为初始值。旧文档的请求在换档后因基版本不匹配被拒绝，不能借相同 ID 定位到新文档对象。
/// 播放状态、播放头与预览设置属于会话，不属于文档历史。
@NullMarked
public enum SessionSwitch {

    /// 初始化：建立首个文档。
    INITIAL,

    /// 本地资源载入。
    LOAD,

    /// 网络请求载入。
    NETWORK,

    /// 从恢复槽恢复（仍须走正常解码与校验）。
    RESTORE,

    /// 用户显式切换到另一份文档。
    SWITCH
}
