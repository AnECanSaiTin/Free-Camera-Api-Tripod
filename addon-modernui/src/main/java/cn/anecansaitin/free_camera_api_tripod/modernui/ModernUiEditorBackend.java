package cn.anecansaitin.free_camera_api_tripod.modernui;

import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorSession;
import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorUiBackend;

/// 用 Modern UI 渲染编辑器的界面后端。
///
/// 注册到主 mod 的 {@link cn.anecansaitin.free_camera_api_tripod.api.editor.EditorUiHost} 后，
/// 打开编辑器时主 mod 会询问本后端，由它接管界面；主 mod 自己不认识 Modern UI。
public final class ModernUiEditorBackend implements EditorUiBackend {
    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean openEditor(EditorSession session) {
        ModernUiEditorScreen.open(session);
        return true;
    }
}
