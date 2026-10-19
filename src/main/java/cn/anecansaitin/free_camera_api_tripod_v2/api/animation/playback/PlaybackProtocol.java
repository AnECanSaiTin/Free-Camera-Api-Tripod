package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

/// 网络播放请求的协议版本（主设计 §8.3）。
///
/// 版本随请求一起传输；接收端只接受 [CURRENT_VERSION]。改变字段形状、默认值语义或
/// 覆盖三态编码时必须提升版本，不能让同一份字节在版本间被解释成不同请求。
///
/// 规模上限**不在这里给默认值**：由宿主按平台与配置传入 [PlaybackLimits]，
/// 避免把某一端的经验值写成协议常量。
@NullMarked
public final class PlaybackProtocol {

    /// 当前协议版本。
    public static final int CURRENT_VERSION = 1;

    private PlaybackProtocol() {
    }
}
