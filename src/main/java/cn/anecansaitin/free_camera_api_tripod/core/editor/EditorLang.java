package cn.anecansaitin.free_camera_api_tripod.core.editor;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/// 编辑器界面文本，统一加 "free_camera_api_tripod.editor." 前缀，便于语言文件维护。
public final class EditorLang {
    private static final String PREFIX = "free_camera_api_tripod.editor.";

    private EditorLang() {
    }

    public static MutableComponent t(String key) {
        return Component.translatable(PREFIX + key);
    }

    public static MutableComponent t(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }
}
