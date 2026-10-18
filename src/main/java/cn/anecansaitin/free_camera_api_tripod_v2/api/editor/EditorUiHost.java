package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import org.jspecify.annotations.NullMarked;

/// 编辑器 UI 宿主：创建或恢复会话后按选择器打开界面（主设计 §9.3）。
///
/// 打开流程统一为“创建 / 恢复会话 → 交给宿主”；宿主负责后端选择、拒绝接管处理和内置回退。
/// 关闭屏幕与关闭会话的生命周期必须区分；拒绝接管的后端不得关闭会话或遗留令牌。
@NullMarked
public interface EditorUiHost {

    /// 按选择器打开会话界面。
    ///
    /// @param session  已创建 / 恢复的会话
    /// @param selector 自动或指定后端
    void open(EditorSession session, EditorBackendSelector selector);

    /// 打开内置默认界面；第三方包装后可转发。
    void openDefault(EditorSession session);
}
