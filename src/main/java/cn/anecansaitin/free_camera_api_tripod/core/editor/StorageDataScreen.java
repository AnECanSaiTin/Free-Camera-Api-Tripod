package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationSavedData;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 存档数据界面的中间基类：承载「随存档保存的数据」这一侧共用的数据逻辑。
///
/// 浏览（打开 / 保存）与 管理（查看 / 删除）两个界面在这里合一：列条目、进层级、面包屑、
/// 条目避重名与建文件夹完全一致，差异只剩「主按钮做什么」，由子类实现。
/// 层级本身没有实体，全名里的 {@code /} 就是层级，见 {@link AnimationSavedData}。
public abstract class StorageDataScreen extends BrowserScreen {
    /// 层级分隔符，与 {@link AnimationSavedData} 一致
    protected static final String SEPARATOR = "/";

    /// 当前操作的存档数据类型：true 为路径，false 为动画。
    ///
    /// 浏览界面在构造时定下就不再改；管理界面允许在第二行切换
    protected boolean path;
    /// 当前所在文件夹，相对存档根，空串表示根；层级用 / 分隔
    protected String folder = "";

    protected StorageDataScreen(Component title, boolean path) {
        super(title);
        this.path = path;
    }

    // region 数据

    /// 读取当前文件夹下的条目（文件夹在前）；没有存档（多人游戏或未进世界）时列表为空
    @Override
    protected void reloadRows() {
        for (String name : AnimationSavedData.listFolders(path, folder)) {
            rows.add(new Row(name, fullName(name), true));
        }

        for (String name : AnimationSavedData.listFiles(path, folder)) {
            rows.add(new Row(name, fullName(name), false));
        }
    }

    /// 当前文件夹下某个条目的全名
    protected String fullName(String name) {
        return AnimationSavedData.join(folder, name);
    }

    /// 进入某个文件夹（空串或 null 表示回到根）
    protected void enterFolder(@Nullable String fullName) {
        folder = fullName == null ? "" : fullName;
        enteredContainer();
    }

    /// 返回上一层；已在根时不做任何事
    @Override
    protected void goUp() {
        int separator = folder.lastIndexOf(SEPARATOR);
        enterFolder(separator < 0 ? "" : folder.substring(0, separator));
    }

    @Override
    protected boolean canGoUp() {
        return !folder.isEmpty();
    }

    /// 当前文件夹里已占用的名字（文件夹与条目都算），新建文件夹时用来避重名
    @Override
    protected List<String> takenNames() {
        List<String> taken = new ArrayList<>();
        taken.addAll(AnimationSavedData.listFolders(path, folder));
        taken.addAll(AnimationSavedData.listFiles(path, folder));
        return taken;
    }

    /// 存档里的文件夹就是一个层级前缀，建文件夹 = 写入它的占位键
    @Override
    protected boolean createFolder(String name) {
        AnimationSavedData.createFolder(path, fullName(name));
        return true;
    }

    // endregion

    // region 面包屑

    /// 刷新面包屑：第一段是存档根，其后是当前层级的每一级；段尾箭头列出同级文件夹
    @Override
    protected void refreshCrumbs() {
        breadcrumb.add(EditorLang.t("storage_browser.root").getString(), () -> enterFolder(""),
                AnimationSavedData.listFolders(path, ""), this::enterFolder);

        StringBuilder prefix = new StringBuilder();

        for (String part : folder.split(SEPARATOR)) {
            if (part.isEmpty()) {
                continue;
            }

            if (!prefix.isEmpty()) {
                prefix.append(SEPARATOR);
            }

            prefix.append(part);
            String current = prefix.toString();
            String parent = current.lastIndexOf(SEPARATOR) < 0 ? "" : current.substring(0, current.lastIndexOf(SEPARATOR));
            breadcrumb.add(part, () -> enterFolder(current),
                    AnimationSavedData.listFolders(path, parent), name -> enterFolder(AnimationSavedData.join(parent, name)));
        }
    }

    // endregion

    // region 界面

    /// 底栏左侧：新建文件夹（浏览与管理两个界面都放同一个位置）
    @Override
    protected void buildFooterLeft() {
        addFooterButton(EditorLang.t("browser.new_folder"), this::newFolder);
    }

    // endregion
}
