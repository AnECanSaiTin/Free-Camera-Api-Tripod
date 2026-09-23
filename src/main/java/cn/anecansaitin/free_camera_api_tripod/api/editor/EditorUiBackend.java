package cn.anecansaitin.free_camera_api_tripod.api.editor;

import org.jspecify.annotations.NullMarked;

/// 编辑器界面后端：用别的界面框架（例如 Modern UI）渲染编辑器。
///
/// 由附属 mod 实现并注册到 [EditorUiHost]；主 mod 打开编辑器时按优先级询问，
/// 没有后端愿意接管时回退到内置界面。
@NullMarked
public interface EditorUiBackend {
    /// 优先级，数值大的先被询问
    default int priority() {
        return 0;
    }

    /// 打开编辑器界面。
    ///
    /// 返回 true 表示已接管；返回 false 表示不接管，主 mod 会继续询问下一个后端，
    /// 若都没有接管则回退内置界面。
    boolean openEditor(EditorSession session);
}
