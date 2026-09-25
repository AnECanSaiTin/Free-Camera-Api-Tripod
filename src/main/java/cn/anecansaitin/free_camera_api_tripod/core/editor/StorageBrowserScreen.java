package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationSavedData;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ConfirmDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/// 编辑器自绘的「存档数据浏览器」界面：列出当前单机存档里已经存在的动画或路径，供打开或写入。
///
/// 与 {@link FileBrowserScreen} 的区别只在数据来源：那边是本地文件，这边是存档内的数据条目。
/// 界面骨架见 {@link BrowserScreen}，存档这一侧的数据逻辑（层级、面包屑、建文件夹）见
/// {@link StorageDataScreen}，所以几套界面的排版与操作手感完全一致。
///
/// - 列表：当前层级的文件夹与条目，双击文件夹进入下一层
/// - 打开模式：选中条目后按「打开」回调它的全名
/// - 保存模式：可以输入带层级的新名字（例如 {@code 分镜/开场}），已存在时先确认覆盖
///
/// 本界面只负责挑选名字（可含层级），真正的读写由调用方在回调里完成。
public class StorageBrowserScreen extends StorageDataScreen {
    /// 保存模式为 true，打开模式为 false
    private final boolean save;
    private final Consumer<String> picked;

    public StorageBrowserScreen(boolean save, boolean path, @Nullable String defaultName, Consumer<String> picked) {
        super(EditorLang.t(titleKey(save, path)), path);
        this.save = save;
        this.picked = picked;
        this.nameValue = save && defaultName != null ? defaultName : "";

        reload();
    }

    /// 打开存档数据浏览界面。save=false 为打开模式（只列已有条目，选中后回调名字），
    /// save=true 为保存模式（可输入名字，列表用于选已有名字覆盖）；
    /// path=true 表示操作对象是存档里的「路径」，false 表示「动画」。
    public static void open(boolean save, boolean path, @Nullable String defaultName, Consumer<String> picked) {
        Minecraft.getInstance().setScreen(new StorageBrowserScreen(save, path, defaultName, picked));
    }

    /// 标题语言键：打开 / 保存 × 动画 / 路径
    private static String titleKey(boolean save, boolean path) {
        if (save) {
            return path ? "storage_browser.title.save_path" : "storage_browser.title.save_animation";
        }

        return path ? "storage_browser.title.open_path" : "storage_browser.title.open_animation";
    }

    // region 数据

    /// 把输入的名字整理成相对存档根的全名：统一分隔符、丢掉空段，再接到当前文件夹后面。
    /// 直接输入 {@code a/b} 可以一次建出多级；整理后为空时返回空串，调用方据此忽略本次输入。
    private String normalizeTarget(String input) {
        StringBuilder relative = new StringBuilder();

        for (String part : input.replace('\\', SEPARATOR.charAt(0)).split(SEPARATOR)) {
            String trimmed = part.strip();

            if (trimmed.isEmpty()) {
                continue;
            }

            if (!relative.isEmpty()) {
                relative.append(SEPARATOR);
            }

            relative.append(trimmed);
        }

        return relative.isEmpty() ? "" : AnimationSavedData.join(folder, relative.toString());
    }

    // endregion

    // region 界面

    /// 只有保存模式才有名字输入框：打开模式的名字直接取自选中条目
    @Override
    protected boolean nameFieldPresent() {
        return save;
    }

    @Override
    protected boolean nameFieldEditable() {
        return save;
    }

    @Override
    protected Component nameLabel() {
        return EditorLang.t("storage_browser.name");
    }

    @Override
    protected Component emptyText() {
        return EditorLang.t("storage_browser.empty");
    }

    @Override
    protected @Nullable Component hintText() {
        return EditorLang.t("storage_browser.hint");
    }

    @Override
    protected Component primaryLabel() {
        return EditorLang.t(save ? "file_browser.save" : "file_browser.open");
    }

    /// 主按钮可用条件：保存模式要填了名字，打开模式要选中一个条目（文件夹不能选）
    @Override
    protected boolean primaryEnabled() {
        if (save) {
            // 输入框正在编辑时拿不到编辑中的文本，先按可用处理，点击时提交后再判定
            return nameFieldEditing() || !nameFieldValue().isBlank();
        }

        Row entry = selectedRow();
        return entry != null && !entry.container();
    }

    /// 单击选中，双击文件夹进入 / 条目确认；保存模式下双击已有条目会填进输入框便于覆盖
    @Override
    protected void rowClicked(int index, @Nullable Row row, boolean doubleClick) {
        if (row == null || !doubleClick) {
            return;
        }

        if (row.container()) {
            enterFolder(row.key());
            return;
        }

        if (save) {
            nameFieldValue(row.name());
            return;
        }

        primaryAction();
    }

    // endregion

    // region 主操作

    /// 主按钮：保存模式回调名字（已存在先确认覆盖），打开模式回调选中条目的全名
    @Override
    protected void primaryAction() {
        if (save) {
            String target = normalizeTarget(nameFieldValue());

            if (target.isEmpty()) {
                return;
            }

            if (AnimationSavedData.exists(path, target)) {
                dialog = new ConfirmDialog(EditorLang.t("file_browser.overwrite_confirm", target), () -> finish(target));
                return;
            }

            finish(target);
            return;
        }

        Row entry = selectedRow();

        if (entry == null || entry.container()) {
            return;
        }

        finish(entry.key());
    }

    /// 回调结果并关闭界面
    private void finish(String name) {
        picked.accept(name);
        onClose();
    }

    // endregion
}
