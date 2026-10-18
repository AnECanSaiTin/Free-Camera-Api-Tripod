package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import org.jspecify.annotations.NullMarked;

/// 整份换档的原因（主设计 §3.3、§4.5、§8.4）。
///
/// 换档会清空历史、选择、交互请求与草稿，并提示用户；发布序号重新从初始值建立新序列。
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
