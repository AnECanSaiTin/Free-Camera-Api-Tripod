package cn.anecansaitin.free_camera_api_tripod.modernui;

import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorUiHost;
import net.neoforged.fml.common.Mod;

/// 现代化界面附属 mod。
///
/// 主 mod 提供编辑器模型与界面后端接口（`api.editor`），本 mod 只负责用 Modern UI 的控件体系
/// 把编辑器画出来。依赖方向是单向的：本 mod 依赖主 mod，主 mod 不认识本 mod，
/// 因此不装本 mod 的客户端完全不受影响。
///
/// 客户端配置里的「现代化界面兼容」（主 mod 的 `ui.modern_ui_compat`，默认开）为关时，
/// 主 mod 不会询问任何后端，仍用内置界面。
@Mod(ModernUiAddon.MOD_ID)
public final class ModernUiAddon {
    /// 本 mod 的 id
    public static final String MOD_ID = "free_camera_api_tripod_modernui";

    public ModernUiAddon() {
        EditorUiHost.register(new ModernUiEditorBackend());
    }
}
