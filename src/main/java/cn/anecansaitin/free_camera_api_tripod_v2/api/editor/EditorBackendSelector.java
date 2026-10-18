package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 编辑器界面后端选择（主设计 §9.3）。
///
/// [backendId] 为空表示 `auto`；非空表示指定已注册后端。指定后端拒绝接管时不得自动切换，
/// 未注册 ID 才警告并打开内置实现。
///
/// @param backendId 指定后端稳定 ID；空表示自动选择
@NullMarked
public record EditorBackendSelector(@Nullable Identifier backendId) {

    /// 自动选择。
    public static EditorBackendSelector auto() {
        return new EditorBackendSelector(null);
    }

    /// 指定后端。
    public static EditorBackendSelector of(Identifier backendId) {
        return new EditorBackendSelector(backendId);
    }

    /// 是否自动选择。
    public boolean isAuto() {
        return backendId == null;
    }
}
