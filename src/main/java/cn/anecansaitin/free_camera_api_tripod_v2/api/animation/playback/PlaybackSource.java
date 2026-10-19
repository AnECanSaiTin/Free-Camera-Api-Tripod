package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 播放请求的**数据来源**（主设计 §8.3、D12）。
///
/// 网络只传“来源”本身，不传选择器字符串：
///
/// - [Embedded]：整份文档随包发送。服务端或接收端按随包文档解码，未知类型/版本/规模错误
///   变成可报告的请求失败（§8.3）。
/// - [Local]：只传本地资源引用；资源解析在**接收端**按资源服务完成，客户端不会收到
///   服务端文件路径，也不把资源名当路径使用。
///
/// 具体锚点选择与玩家解析在服务端完成；来源只决定“播哪份文档”，不携带锚点选择器。
@NullMarked
public sealed interface PlaybackSource permits PlaybackSource.Embedded, PlaybackSource.Local {

    /// 随包文档。
    ///
    /// @param animation 完整文档；必须已通过文档校验
    record Embedded(CameraAnimation animation) implements PlaybackSource {
        public Embedded {
            Objects.requireNonNull(animation, "animation");
        }
    }

    /// 本地资源引用；由接收端的资源服务解析成文档。
    ///
    /// @param resourceId 相对资源标识
    record Local(ResourceId resourceId) implements PlaybackSource {
        public Local {
            Objects.requireNonNull(resourceId, "resourceId");
        }
    }
}
