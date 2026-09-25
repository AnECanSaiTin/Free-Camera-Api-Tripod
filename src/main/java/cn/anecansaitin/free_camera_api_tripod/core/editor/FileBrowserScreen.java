package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationFiles;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ConfirmDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// 游戏内文件浏览界面：资源管理器式的保存 / 打开对话框。
///
/// MC 里没法弹出系统文件对话框，所以用本界面代替。界面骨架（标题行、面包屑、列表、底栏、提示行）
/// 与存档数据界面完全一致，见 {@link BrowserScreen}；这里只管本地文件这一侧的语义：
/// - 顶栏是面包屑：点某一段直接跳过去，段尾箭头列出同级目录（与 Windows 地址栏一致）
/// - 列表列出当前目录下的子目录与符合后缀的文件（目录在前，按名字排序）
/// - 底栏：文件名输入框、「新建文件夹」、「保存 / 打开」与「取消」
///
/// 交互：单击选中，双击目录进入、双击文件执行主操作；Esc 取消并返回来源界面。
/// 所有目录读取异常都在界面内消化（读不出显示空列表），不会中断游戏。
///
/// 本界面只负责挑选路径（保存时按调用方要求的后缀补齐文件名），真正的读写由调用方在回调里完成。
public class FileBrowserScreen extends BrowserScreen {
    /// 目录在前、文件在后，同类按名字不区分大小写排序
    private static final Comparator<Row> LIST_ORDER = Comparator.comparing((Row row) -> !row.container())
            .thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER);

    /// 保存模式为 true，打开模式为 false
    private final boolean save;
    /// 本界面要求保存 / 显示的文件后缀（如 .animation.json / .path），目录不受它限制
    private final String suffix;
    private final Consumer<Path> picked;
    /// 当前目录，始终是绝对路径
    private Path directory;

    public FileBrowserScreen(boolean save, Path directory, @Nullable String defaultName, String suffix, Consumer<Path> picked) {
        super(EditorLang.t(save ? "file_browser.title.save" : "file_browser.title.open"));
        this.save = save;
        this.directory = resolveStartDirectory(directory.toAbsolutePath().normalize());
        this.suffix = suffix;
        this.picked = picked;
        this.nameValue = save && defaultName != null ? defaultName : "";

        reload();
    }

    /// 打开文件浏览界面。suffix 为要求保存 / 显示的文件后缀（如 .animation.json / .path）；
    /// picked 在用户确认后回调（带后缀的绝对路径），取消时不回调。
    public static void open(boolean save, Path directory, @Nullable String defaultName, String suffix, Consumer<Path> picked) {
        Minecraft.getInstance().setScreen(new FileBrowserScreen(save, directory, defaultName, suffix, picked));
    }

    // region 目录

    /// 起始目录：不存在时尝试创建，创建不了就退到最近的已存在父目录
    private static Path resolveStartDirectory(Path path) {
        if (Files.isDirectory(path)) {
            return path;
        }

        try {
            Files.createDirectories(path);
            return path;
        } catch (IOException e) {
            // 创建失败（无权限、路径被文件占用等）：上溯到存在的父目录
        }

        Path parent = path.getParent();

        while (parent != null && !Files.isDirectory(parent)) {
            parent = parent.getParent();
        }

        if (parent != null) {
            return parent;
        }

        Path root = path.getRoot();
        return root != null ? root : path;
    }

    /// 读取当前目录内容（目录 + 符合后缀的文件）；读不出来就当空目录，界面不因此中断
    @Override
    protected void reloadRows() {
        try (Stream<Path> stream = Files.list(directory)) {
            List<Row> found = new ArrayList<>();

            stream.forEach(path -> {
                Path fileName = path.getFileName();
                boolean directory = Files.isDirectory(path);

                if (!directory && !matchesSuffix(path)) {
                    // 后缀不符的文件不显示，目录始终显示
                    return;
                }

                found.add(new Row(fileName == null ? path.toString() : fileName.toString(), path.toString(), directory));
            });

            found.sort(LIST_ORDER);
            rows.addAll(found);
        } catch (IOException e) {
            // 目录被删除或不可读：显示为空列表
        }
    }

    /// 文件是否符合本界面要求的后缀
    private boolean matchesSuffix(Path path) {
        return AnimationFiles.ANIMATION_SUFFIX.equals(suffix) ? AnimationFiles.isAnimationFile(path)
                : AnimationFiles.isPathFile(path);
    }

    /// 进入子目录
    private void enterDirectory(Path path) {
        directory = path.toAbsolutePath().normalize();
        enteredContainer();
    }

    /// 返回上一级；已在根目录时不做任何事
    @Override
    protected void goUp() {
        Path parent = directory.getParent();

        if (parent != null) {
            enterDirectory(parent);
        }
    }

    /// 当前目录是否还有上一级（盘符 / 根目录时为 false）
    @Override
    protected boolean canGoUp() {
        return directory.getParent() != null;
    }

    /// 在当前目录下建一个不重名的新文件夹。
    ///
    /// 名字怎么取、建完选中哪一行由 {@link BrowserScreen#newFolder()} 统一处理，
    /// 这里只回答「哪些名字被占了」与「怎么建出来」
    @Override
    protected List<String> takenNames() {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.map(Path::getFileName).filter(Objects::nonNull).map(Path::toString).toList();
        } catch (IOException e) {
            // 目录读不出来：当成空目录，命名从默认名开始
            return List.of();
        }
    }

    /// 建立文件夹；无权限、路径被占用等失败时返回 false，界面保持原状（列表不变，不弹异常）
    @Override
    protected boolean createFolder(String name) {
        try {
            Files.createDirectories(directory.resolve(name));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // endregion

    // region 面包屑

    /// 刷新面包屑：每段可点击跳转，段尾箭头列出该段的同级目录（与 Windows 地址栏一致）
    @Override
    protected void refreshCrumbs() {
        List<Path> chain = directoryChain();

        for (int i = 0; i < chain.size(); i++) {
            Path path = chain.get(i);
            int index = i;
            breadcrumb.add(crumbLabel(path), () -> enterDirectory(path), siblingDirectories(chain, index),
                    name -> enterSibling(chain, index, name));
        }
    }

    /// 从根到当前目录的路径链
    private List<Path> directoryChain() {
        List<Path> chain = new ArrayList<>();
        Path current = directory;

        while (current != null) {
            chain.add(current);
            current = current.getParent();
        }

        Collections.reverse(chain);
        return chain;
    }

    /// 段上的文字：根段显示完整路径（如 D:\），其余只显示目录名
    private static String crumbLabel(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    /// 某一段的同级目录：根段列出所有盘符，其余列出上一层目录下的子目录
    private static List<String> siblingDirectories(List<Path> chain, int index) {
        if (index == 0) {
            List<String> roots = new ArrayList<>();

            for (File root : File.listRoots()) {
                roots.add(root.getAbsolutePath());
            }

            return roots;
        }

        return childDirectories(chain.get(index - 1));
    }

    /// 目录下的子目录名（按名字排序；读不出来时返回空列表）
    private static List<String> childDirectories(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .filter(Objects::nonNull)
                    .map(Path::toString)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /// 选中某一段的同级目录：根段选的是盘符，其余是上一层目录下的子目录
    private void enterSibling(List<Path> chain, int index, String name) {
        if (index == 0) {
            enterDirectory(Path.of(name));
            return;
        }

        enterDirectory(chain.get(index - 1).resolve(name));
    }

    // endregion

    // region 界面

    /// 底栏左侧：新建文件夹
    @Override
    protected void buildFooterLeft() {
        addFooterButton(EditorLang.t("browser.new_folder"), this::newFolder);
    }

    @Override
    protected boolean nameFieldPresent() {
        return true;
    }

    @Override
    protected boolean nameFieldEditable() {
        return save;
    }

    @Override
    protected Component nameLabel() {
        return EditorLang.t("file_browser.name");
    }

    @Override
    protected Component emptyText() {
        return EditorLang.t("file_browser.empty");
    }

    @Override
    protected @Nullable Component hintText() {
        if (save) {
            String name = nameFieldValue().strip();

            if (!name.isEmpty() && Files.exists(targetFile(name))) {
                return EditorLang.t("file_browser.overwrite", name);
            }
        }

        return EditorLang.t("file_browser.hint");
    }

    @Override
    protected Component primaryLabel() {
        return EditorLang.t(save ? "file_browser.save" : "file_browser.open");
    }

    /// 目标已存在时会被覆盖，提示随按钮悬停展示
    @Override
    protected @Nullable Component primaryTooltip() {
        return EditorLang.t("file_browser.overwrite");
    }

    /// 主按钮可用条件：保存模式要填了文件名，打开模式要选中一个文件
    @Override
    protected boolean primaryEnabled() {
        if (save) {
            // 输入框正在编辑时拿不到编辑中的文本，先按可用处理，点击时提交后再判定
            return nameFieldEditing() || !nameFieldValue().isBlank();
        }

        Row entry = selectedRow();
        return entry != null && !entry.container();
    }

    /// 单击选中，双击目录进入 / 文件执行主操作；保存模式下双击文件会填进输入框便于覆盖
    @Override
    protected void rowClicked(int index, @Nullable Row row, boolean doubleClick) {
        if (row == null || !doubleClick) {
            return;
        }

        if (row.container()) {
            enterDirectory(Path.of(row.key()));
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

    /// 主按钮：保存模式回调节径（已存在先确认覆盖），打开模式回调选中文件
    @Override
    protected void primaryAction() {
        if (save) {
            String name = nameFieldValue().strip();

            if (name.isEmpty()) {
                return;
            }

            Path target = targetFile(name);

            if (Files.exists(target)) {
                dialog = new ConfirmDialog(EditorLang.t("file_browser.overwrite_confirm", name), () -> finish(target));
                return;
            }

            finish(target);
            return;
        }

        Row entry = selectedRow();

        if (entry == null || entry.container()) {
            return;
        }

        finish(Path.of(entry.key()));
    }

    /// 当前目录 + 输入的文件名（按本界面要求的后缀补齐）组成的绝对路径
    private Path targetFile(String name) {
        return directory.resolve(AnimationFiles.withSuffix(Path.of(name), suffix)).toAbsolutePath().normalize();
    }

    /// 回调结果并关闭界面
    private void finish(Path path) {
        picked.accept(path);
        onClose();
    }

    // endregion
}
