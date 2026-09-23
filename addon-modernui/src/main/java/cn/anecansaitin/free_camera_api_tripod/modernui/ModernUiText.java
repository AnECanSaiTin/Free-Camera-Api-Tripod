package cn.anecansaitin.free_camera_api_tripod.modernui;

import net.minecraft.network.chat.Component;

/// 文案取用。
///
/// 编辑器的语言键由主 mod 提供（键落在主 mod 的命名空间下），附属 mod 只负责拼接前缀取用，
/// 因此这里不需要再维护一份语言文件。
final class ModernUiText {
    private static final String PREFIX = "free_camera_api_tripod.editor.";

    private ModernUiText() {
    }

    /// 取译文文本
    static String str(String key) {
        return of(key).getString();
    }

    /// 取可翻译组件
    static Component of(String key, Object... arguments) {
        return Component.translatable(PREFIX + key, arguments);
    }
}
