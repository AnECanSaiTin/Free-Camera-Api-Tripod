package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationSavedData;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ConfirmDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// 存档数据管理界面：查看并删除随存档保存的动画与路径。
///
/// 与 {@link StorageBrowserScreen} 用同一套骨架与同一份数据（层级、面包屑、建文件夹都在
/// {@link StorageDataScreen} 里），区别只在语义：那边是「挑一个名字去读写」，
/// 这边是「看看存了什么、删掉不要的」，因此主按钮是「删除」，顶栏第二行用来切换动画 / 路径。
///
/// - 列表：当前层级的文件夹与条目，双击文件夹进入下一层
/// - 底栏：左侧「新建文件夹」，右侧「删除」对选中条目生效；
///   选中文件夹时连同其中的内容一起删（删前二次确认）
/// - 提示行：显示当前层级的条目数，删除结果也在这里回显
public class StorageManagerScreen extends StorageDataScreen {
    private @Nullable ButtonWidget animationButton;
    private @Nullable ButtonWidget pathButton;

    public StorageManagerScreen() {
        this(false);
    }

    public StorageManagerScreen(boolean path) {
        super(EditorLang.t("storage_manager.title"), path);

        reload();
    }

    /// 打开存档数据管理界面；没有存档时调用方先提示，这里不做二次判断
    public static void open() {
        Minecraft.getInstance().setScreen(new StorageManagerScreen());
    }

    /// 换一份数据查看：动画与路径各是一套，切换后回到根层级
    private void switchType(boolean path) {
        if (this.path == path) {
            return;
        }

        this.path = path;
        folder = "";
        enteredContainer();
    }

    // region 界面

    /// 顶栏第二行：动画 / 路径的来源切换
    @Override
    protected int extraRowHeight() {
        return EXTRA_ROW_HEIGHT;
    }

    @Override
    protected void buildExtraRow(UiRect row) {
        int y = row.y() + (EXTRA_ROW_HEIGHT - CONTROL_HEIGHT) / 2;
        int available = Math.clamp(width - PADDING * 2, 40, 240);
        int buttonWidth = Math.max(1, (available - BUTTON_GAP) / 2);
        animationButton = widgets.add(new ButtonWidget(new UiRect(PADDING, y, buttonWidth, CONTROL_HEIGHT),
                EditorLang.t("storage_manager.animation"), () -> switchType(false)));
        pathButton = widgets.add(new ButtonWidget(new UiRect(PADDING + buttonWidth + BUTTON_GAP, y, buttonWidth, CONTROL_HEIGHT),
                EditorLang.t("storage_manager.path"), () -> switchType(true)));
    }

    @Override
    protected void refreshExtraWidgets() {
        if (animationButton != null) {
            animationButton.toggled(!path);
        }

        if (pathButton != null) {
            pathButton.toggled(path);
        }
    }

    @Override
    protected Component emptyText() {
        return EditorLang.t("storage_manager.empty");
    }

    /// 提示行：当前层级的条目数，以及「删除会直接改存档」这句提醒
    @Override
    protected @Nullable Component hintText() {
        return EditorLang.t("storage_manager.hint", rows.size());
    }

    /// 主按钮就是「删除」，没有选中项时不可用
    @Override
    protected Component primaryLabel() {
        return EditorLang.t("storage_manager.delete");
    }

    @Override
    protected boolean primaryEnabled() {
        return selectedRow() != null;
    }

    @Override
    protected void primaryAction() {
        deleteSelected();
    }

    /// 单击选中；双击容器进入下一层，双击条目等同于点「删除」
    @Override
    protected void rowClicked(int index, @Nullable Row row, boolean doubleClick) {
        if (row == null || !doubleClick) {
            return;
        }

        if (row.container()) {
            enterFolder(row.key());
            return;
        }

        deleteSelected();
    }

    // endregion

    // region 删除

    /// 删除选中的条目或文件夹：先二次确认，文件夹是连同其中的内容一起删
    private void deleteSelected() {
        Row row = selectedRow();

        if (row == null) {
            return;
        }

        Component message = EditorLang.t(row.container()
                ? "storage_manager.delete_folder_confirm" : "storage_manager.delete_confirm", row.name());
        dialog = new ConfirmDialog(message, () -> delete(row));
    }

    private void delete(Row row) {
        if (row.container()) {
            AnimationSavedData.deleteFolder(path, row.key());
        } else if (!AnimationSavedData.deleteEntry(path, row.key())) {
            // 条目已经被别处删掉了：只提示，不再当成删除成功
            statusMessage = EditorLang.t("storage_manager.nothing_deleted").getString();
            reload();
            return;
        }

        selectedIndex = -1;
        statusMessage = EditorLang.t("storage_manager.deleted", row.name()).getString();
        reload();
    }

    // endregion
}
