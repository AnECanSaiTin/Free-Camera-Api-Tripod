package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

/// 不可变消息描述到宿主显示组件的无状态渲染适配（主设计 §3.2、§4.1）。
///
/// 每次调用都从 [LocalizedMessage] 重新生成独立的 `Component`：保留翻译键、fallback、参数
/// 顺序与嵌套样式；不先把消息转成纯字符串，以免丢失本地化语义；不把渲染结果写回契约或
/// 作为可共享的可变状态。语言切换、显示缓存与具体样式实现由宿主负责，缓存不得破坏
/// “每次独立输出”的边界。
@NullMarked
@FunctionalInterface
public interface LocalizedMessageRenderer {

    /// 渲染一条不可变消息。
    ///
    /// @param message 不可变消息描述
    /// @return 新生成的显示组件；调用方可修改自己的副本，不影响消息或后续渲染
    Component render(LocalizedMessage message);
}
