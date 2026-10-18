package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 可替换的编辑器 UI 后端描述（主设计 §9.3、§9.4）。
///
/// 内置后端具有稳定 ID 和最低优先级；`auto` 按优先级询问，同优先级按 ID 稳定排序。
/// 本接口不持有屏幕或客户端渲染类型；实际挂载由客户端 `EditorUiHost` 实现完成。
@NullMarked
public interface EditorUiBackend {

    /// 稳定后端 ID。
    Identifier id();

    /// 自动选择优先级；数值越大越优先。
    int priority();

    /// 当前会话是否可由此后端接管；拒绝时不得关闭会话或遗留接管令牌。
    boolean canOpen(EditorSession session);

    /// 实际打开 / 挂载该后端界面。
    ///
    /// 只在 [canOpen] 为真或调用方明确指定该后端后调用。实现不得关闭会话；打开失败必须
    /// 报告给宿主，指定后端失败时不自动切换。该方法不依赖客户端 UI 类型。
    void open(EditorSession session);
}
