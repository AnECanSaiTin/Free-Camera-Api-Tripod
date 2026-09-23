package cn.anecansaitin.free_camera_api_tripod.api.editor;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// 编辑器界面后端的注册与询问入口。
///
/// 主 mod 打开编辑器时调用 [#open(EditorSession)]：按优先级依次询问已注册的后端，
/// 第一个愿意接管的负责渲染界面；都没有接管则返回 false，主 mod 回退到内置界面。
///
/// 依赖方向是单向的——附属 mod 依赖主 mod 并调用本类，主 mod 不认识任何具体后端。
@NullMarked
public final class EditorUiHost {
    /// 已注册的后端，按优先级从高到低排列
    private static final List<EditorUiBackend> BACKENDS = new ArrayList<>();

    private EditorUiHost() {
    }

    /// 注册一个界面后端；重复注册同一实例会被忽略
    public static synchronized void register(EditorUiBackend backend) {
        if (BACKENDS.contains(backend)) {
            return;
        }

        BACKENDS.add(backend);
        BACKENDS.sort(Comparator.comparingInt(EditorUiBackend::priority).reversed());
    }

    /// 是否已有后端注册
    public static synchronized boolean hasBackend() {
        return !BACKENDS.isEmpty();
    }

    /// 询问各后端是否接管界面；返回 true 表示已被接管，false 表示应回退内置界面
    public static synchronized boolean open(EditorSession session) {
        for (EditorUiBackend backend : List.copyOf(BACKENDS)) {
            if (backend.openEditor(session)) {
                return true;
            }
        }

        return false;
    }
}
