package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationFiles;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.BreadcrumbBar;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ConfirmDialog;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.TextFieldWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.WidgetHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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
/// MC 里没法弹出系统文件对话框，所以用本界面代替：
/// - 顶栏第一行：面包屑路径（每段可点击跳转，段尾箭头可展开同级目录，与 Windows 地址栏一致）、「上一级」与关闭按钮
/// - 顶栏第二行：可编辑的地址栏（显示当前目录绝对路径，回车跳转）
/// - 中部：目录内容列表（文件夹在前，文件在后且只显示符合后缀的文件，按名字排序），带滚动条与滚动
/// - 底栏：文件名输入框、「保存 / 打开」主按钮、「新建文件夹」与「取消」按钮
///
/// 交互：单击选中，双击文件夹进入、双击文件执行主操作；Esc 取消并返回来源界面。
/// 所有目录读取异常都在界面内消化（读不出显示空列表），不会中断游戏。
///
/// 本界面只负责挑选路径（保存时按调用方要求的后缀补齐文件名），真正的读写由调用方在回调里完成。
public class FileBrowserScreen extends Screen {
    /// 顶栏单行高度；顶栏共两行（面包屑 + 地址栏）
    private static final int BAR_ROW_HEIGHT = 18;
    private static final int TOP_BAR_HEIGHT = BAR_ROW_HEIGHT * 2;
    private static final int BOTTOM_BAR_HEIGHT = 22;
    private static final int CONTROL_HEIGHT = 14;
    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 13;
    private static final int LIST_PADDING = 2;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int CLOSE_BUTTON_WIDTH = 16;
    private static final int STATUS_ROW_HEIGHT = 11;
    private static final int BUTTON_GAP = 4;
    /// 文件名输入框的宽度区间：窗口过窄时先压缩输入框，保证按钮完整可见
    private static final int NAME_FIELD_MAX_WIDTH = 200;
    private static final int NAME_FIELD_MIN_WIDTH = 60;
    /// 地址栏输入框的最小宽度：放不下时省略「目录」标签，把整段宽度留给输入框
    private static final int ADDRESS_FIELD_MIN_WIDTH = 60;
    /// 新建文件夹的默认名（ASCII，避免不同文件系统下的编码问题）
    private static final String DEFAULT_FOLDER_NAME = "new_folder";

    /// 文件夹在前、文件在后，同类按名字不区分大小写排序
    private static final Comparator<Entry> LIST_ORDER = Comparator.comparing((Entry entry) -> !entry.directory())
            .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);

    /// 保存模式为 true，打开模式为 false
    private final boolean save;
    /// 本界面要求保存 / 显示的文件后缀（如 .animation.json / .path），目录不受它限制
    private final String suffix;
    /// 打开本界面的来源界面，关闭时返回；没有来源界面时为 null
    private final @Nullable Screen parent;
    private final Consumer<Path> picked;
    private final WidgetHost widgets = new WidgetHost();
    private final List<Entry> entries = new ArrayList<>();
    /// 顶栏第一行的面包屑路径
    private final BreadcrumbBar breadcrumb = new BreadcrumbBar();
    /// 当前目录，始终是绝对路径
    private Path directory;
    private @Nullable TextFieldWidget nameField;
    private @Nullable TextFieldWidget addressField;
    private @Nullable ButtonWidget upButton;
    private @Nullable ButtonWidget primaryButton;
    private @Nullable ConfirmDialog dialog;
    private UiRect upButtonRect = new UiRect(0, 0, 0, 0);
    private UiRect closeButtonRect = new UiRect(0, 0, 0, 0);
    /// 面包屑区域：顶栏第一行里「上一级」与关闭按钮之间的整段
    private UiRect breadcrumbRect = new UiRect(0, 0, 0, 0);
    /// 顶栏「目录」标签的位置；空间不足时不绘制
    private int addressLabelX;
    private boolean addressLabelVisible;
    /// 底栏「文件名」标签的位置；空间不足时不绘制
    private int nameLabelX;
    private boolean nameLabelVisible;
    /// 一次性状态提示（地址栏跳转失败等），下一次成功切换目录时清除
    private @Nullable String statusMessage;
    private int selectedIndex = -1;
    private int scrollY;
    /// 上次构建控件时的屏幕尺寸，尺寸变化才重建，避免打断输入框编辑状态
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    /// 保存模式下文件名输入框的初始值，控件在 init 里构建时取用
    private String defaultName = "";

    public FileBrowserScreen(boolean save, Path directory, @Nullable String defaultName, String suffix, Consumer<Path> picked) {
        super(EditorLang.t(save ? "file_browser.title.save" : "file_browser.title.open"));
        this.save = save;
        this.directory = resolveStartDirectory(directory.toAbsolutePath().normalize());
        this.suffix = suffix;
        this.picked = picked;
        // 构造时当前界面仍是来源界面，因为 setScreen 在参数求值之后才切换
        this.parent = Minecraft.getInstance().screen;
        this.defaultName = save && defaultName != null ? defaultName : "";

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
    private void reload() {
        entries.clear();

        try (Stream<Path> stream = Files.list(directory)) {
            List<Entry> found = new ArrayList<>();

            stream.forEach(path -> {
                Path fileName = path.getFileName();
                boolean directory = Files.isDirectory(path);

                if (!directory && !matchesSuffix(path)) {
                    // 后缀不符的文件不显示，目录始终显示
                    return;
                }

                found.add(new Entry(fileName == null ? path.toString() : fileName.toString(), path, directory));
            });

            found.sort(LIST_ORDER);
            entries.addAll(found);
        } catch (IOException e) {
            // 目录被删除或不可读：显示为空列表
        }

        if (selectedIndex >= entries.size()) {
            selectedIndex = -1;
        }

        scrollY = Mth.clamp(scrollY, 0, maxScroll());
    }

    /// 文件是否符合本界面要求的后缀
    private boolean matchesSuffix(Path path) {
        return AnimationFiles.ANIMATION_SUFFIX.equals(suffix) ? AnimationFiles.isAnimationFile(path)
                : AnimationFiles.isPathFile(path);
    }

    /// 进入子目录
    private void enterDirectory(Path path) {
        directory = path.toAbsolutePath().normalize();
        selectedIndex = -1;
        scrollY = 0;
        statusMessage = null;
        reload();
    }

    /// 返回上一级；已在根目录时不做任何事
    private void goUp() {
        Path parent = directory.getParent();

        if (parent == null) {
            return;
        }

        enterDirectory(parent);
    }

    /// 当前目录是否还有上一级（盘符 / 根目录时为 false）
    private boolean canGoUp() {
        return directory.getParent() != null;
    }

    /// 地址栏跳转：跳到输入的目录（不存在则尝试创建），失败时留在原目录并给出提示
    private void navigateTo(String input) {
        String text = input.strip();

        if (text.isEmpty()) {
            return;
        }

        Path target;

        try {
            Path path = Path.of(text);
            // 相对路径按当前目录的子路径解析，与资源管理器一致
            target = (path.isAbsolute() ? path : directory.resolve(path)).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            statusMessage = EditorLang.t("file_browser.address_invalid").getString();
            return;
        }

        if (Files.isDirectory(target)) {
            enterDirectory(target);
            return;
        }

        try {
            Files.createDirectories(target);
            enterDirectory(target);
        } catch (IOException e) {
            // 创建失败（无权限、路径被文件占用等）：地址栏内容由 refreshWidgets 还原为当前目录
            statusMessage = EditorLang.t("file_browser.address_invalid").getString();
        }
    }

    /// 在当前目录下建一个不重名的新文件夹
    private void newFolder() {
        Path target = directory.resolve(DEFAULT_FOLDER_NAME);

        for (int i = 2; Files.exists(target); i++) {
            target = directory.resolve(DEFAULT_FOLDER_NAME + "_" + i);
        }

        try {
            Files.createDirectory(target);
        } catch (IOException e) {
            // 建不出来就保持原状（列表不变），不弹异常
            return;
        }

        reload();

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).path().equals(target)) {
                selectedIndex = i;
                break;
            }
        }
    }

    private @Nullable Entry selected() {
        return selectedIndex >= 0 && selectedIndex < entries.size() ? entries.get(selectedIndex) : null;
    }

    private int maxScroll() {
        return Math.max(0, entries.size() * ROW_HEIGHT + LIST_PADDING * 2 - listRect().height());
    }

    /// 目录条目：文件夹 / 文件
    private record Entry(String name, Path path, boolean directory) {
    }

    // endregion

    // region 面包屑

    /// 刷新面包屑：每段可点击跳转，段尾箭头列出该段的同级目录（与 Windows 地址栏一致）
    private void updateBreadcrumb() {
        breadcrumb.layout(breadcrumbRect, width, height);
        breadcrumb.clear();
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

    // region 几何

    private UiRect topBarRect() {
        return new UiRect(0, 0, width, TOP_BAR_HEIGHT);
    }

    private UiRect bottomBarRect() {
        return new UiRect(0, Math.max(0, height - BOTTOM_BAR_HEIGHT), width, BOTTOM_BAR_HEIGHT);
    }

    /// 底栏上方的提示行：显示覆盖提示等信息
    private UiRect statusRect() {
        UiRect bar = bottomBarRect();
        return new UiRect(PADDING, Math.max(0, bar.y() - STATUS_ROW_HEIGHT), Math.max(0, width - PADDING * 2), STATUS_ROW_HEIGHT);
    }

    private UiRect listRect() {
        int top = TOP_BAR_HEIGHT + 4;
        int bottom = Math.max(top, statusRect().y() - 4);
        return new UiRect(PADDING, top, Math.max(1, width - PADDING * 2), bottom - top);
    }

    // endregion

    // region 控件

    @Override
    protected void init() {
        buildWidgets();
    }

    /// 屏幕尺寸变化时重建控件；控件位置全部由当前尺寸推导
    private void ensureLayout() {
        if (width == layoutWidth && height == layoutHeight) {
            return;
        }

        buildWidgets();
    }

    private void buildWidgets() {
        layoutWidth = width;
        layoutHeight = height;
        widgets.clear();
        upButton = null;
        addressField = null;
        nameField = null;
        primaryButton = null;
        addressLabelVisible = false;
        nameLabelVisible = false;

        int row1Y = (BAR_ROW_HEIGHT - CONTROL_HEIGHT) / 2;
        upButtonRect = new UiRect(PADDING, row1Y, textButtonWidth("file_browser.up"), CONTROL_HEIGHT);
        upButton = widgets.add(new ButtonWidget(upButtonRect, EditorLang.t("file_browser.up"), this::goUp));
        upButton.enabled(canGoUp());

        closeButtonRect = new UiRect(Math.max(PADDING, width - PADDING - CLOSE_BUTTON_WIDTH), row1Y,
                CLOSE_BUTTON_WIDTH, CONTROL_HEIGHT);
        widgets.add(new ButtonWidget(closeButtonRect, Component.literal(Icons.CLOSE), this::onClose)
                .tooltip(EditorLang.t("common.cancel")));

        // 面包屑占据第一行剩余整段；上下各留一点，贴住行分隔线
        int breadcrumbX = upButtonRect.right() + 4;
        int breadcrumbRight = closeButtonRect.x() - 4;
        breadcrumbRect = new UiRect(breadcrumbX, 1, Math.max(0, breadcrumbRight - breadcrumbX), BAR_ROW_HEIGHT - 2);

        buildAddressBar(BAR_ROW_HEIGHT);
        buildBottomBar();
    }

    /// 顶栏第二行：显示当前目录绝对路径的输入框，回车跳转
    private void buildAddressBar(int rowY) {
        int x = PADDING;
        int available = Math.max(0, width - PADDING * 2);
        int labelWidth = Draw.font().width(EditorLang.t("file_browser.address").getString()) + 4;
        int fieldWidth = available - labelWidth;

        if (fieldWidth < ADDRESS_FIELD_MIN_WIDTH) {
            // 空间不足：省略标签，把整段宽度留给输入框
            fieldWidth = available;
        } else {
            addressLabelX = x;
            addressLabelVisible = true;
            x += labelWidth;
        }

        if (fieldWidth <= 0) {
            return;
        }

        // 内容在回车提交时才生效，输入过程中不打扰用户；绝对路径通常很长，放宽长度上限
        addressField = widgets.add(new TextFieldWidget(
                new UiRect(x, rowY + (BAR_ROW_HEIGHT - CONTROL_HEIGHT) / 2, fieldWidth, CONTROL_HEIGHT),
                directory.toString(), value -> { }).maxLength(260));
    }

    /// 底栏：新建文件夹、文件名输入框、主按钮、取消，从右往左排布
    private void buildBottomBar() {
        UiRect bar = bottomBarRect();
        int y = bar.y() + (BOTTOM_BAR_HEIGHT - CONTROL_HEIGHT) / 2;
        int cursor = bar.right() - PADDING;

        int cancelWidth = textButtonWidth("common.cancel");
        UiRect cancelRect = new UiRect(cursor - cancelWidth, y, cancelWidth, CONTROL_HEIGHT);
        widgets.add(new ButtonWidget(cancelRect, EditorLang.t("common.cancel"), this::onClose));
        cursor = cancelRect.x() - BUTTON_GAP;

        int primaryWidth = textButtonWidth(save ? "file_browser.save" : "file_browser.open");
        UiRect primaryRect = new UiRect(Math.max(PADDING, cursor - primaryWidth), y, primaryWidth, CONTROL_HEIGHT);
        primaryButton = widgets.add(new ButtonWidget(primaryRect, EditorLang.t(save ? "file_browser.save" : "file_browser.open"),
                this::confirmSelection).accent(true));
        // 目标已存在时会被覆盖，提示随按钮悬停展示
        primaryButton.tooltip(EditorLang.t("file_browser.overwrite"));
        cursor = primaryRect.x() - BUTTON_GAP;

        int newFolderWidth = textButtonWidth("file_browser.new_folder");
        UiRect newFolderRect = new UiRect(Math.max(PADDING, cursor - newFolderWidth), y, newFolderWidth, CONTROL_HEIGHT);
        widgets.add(new ButtonWidget(newFolderRect, EditorLang.t("file_browser.new_folder"), this::newFolder));
        cursor = newFolderRect.x() - BUTTON_GAP;

        // 剩余空间给「文件名」标签与输入框；放不下就整段省略
        int available = cursor - PADDING;
        int labelWidth = Draw.font().width(EditorLang.t("file_browser.name").getString()) + 4;
        int fieldWidth = Mth.clamp(available - labelWidth, 0, NAME_FIELD_MAX_WIDTH);

        if (fieldWidth < NAME_FIELD_MIN_WIDTH) {
            fieldWidth = Math.max(0, available);
        } else {
            nameLabelX = PADDING;
            nameLabelVisible = true;
        }

        if (fieldWidth <= 0) {
            return;
        }

        int fieldX = nameLabelVisible ? PADDING + labelWidth : PADDING;
        // 输入内容按需读取（提交后才生效），这里不需要额外响应
        nameField = widgets.add(new TextFieldWidget(new UiRect(fieldX, y, fieldWidth, CONTROL_HEIGHT),
                save ? defaultName : "", value -> { }));
    }

    /// 按钮宽度：随文字长度自适应，空出左右内边距
    private static int textButtonWidth(String key) {
        return Draw.font().width(EditorLang.t(key).getString()) + 10;
    }

    /// 每帧同步控件的可用状态与显示内容（选中项、输入内容都会变）
    private void refreshWidgets() {
        if (upButton != null) {
            upButton.enabled(canGoUp());
        }

        if (addressField != null) {
            // 回显当前目录；编辑中的内容不会被覆盖，跳转失败时也就此还原
            addressField.value(directory.toString());
        }

        if (nameField != null) {
            if (save) {
                nameField.visible(true);
                nameField.enabled(true);
            } else {
                Entry entry = selected();
                // 打开模式：只回显选中的文件名，不接受输入
                nameField.visible(entry != null);
                nameField.enabled(false);
                nameField.value(entry == null ? "" : entry.name());
            }
        }

        if (primaryButton != null) {
            primaryButton.enabled(primaryEnabled());
        }
    }

    /// 主按钮可用条件：保存模式要填了文件名，打开模式要选中一个文件
    private boolean primaryEnabled() {
        if (save) {
            // 输入框正在编辑时拿不到编辑中的文本，先按可用处理，点击时提交后再判定
            return nameField != null && (nameField.editing() || !nameField.value().strip().isEmpty());
        }

        Entry entry = selected();
        return entry != null && !entry.directory();
    }

    // endregion

    // region 主操作

    /// 主按钮：保存模式回调节径（已存在先确认覆盖），打开模式回调选中文件
    private void confirmSelection() {
        if (save) {
            if (nameField == null) {
                return;
            }

            String name = nameField.value().strip();

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

        Entry entry = selected();

        if (entry == null || entry.directory()) {
            return;
        }

        finish(entry.path());
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

    /// 关闭并返回打开本界面的原界面；没有来源界面时退回游戏
    @Override
    public void onClose() {
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    // endregion

    // region 绘制

    /// 不绘制原版背景，界面底衬由 extractRenderState 统一铺
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    /// 暂停世界：浏览文件期间世界不再变化
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Draw.beginFrame();
        // 铺一层不透明底，界面之外不再露出世界
        graphics.fill(0, 0, width, height, Draw.SCREEN_BG);

        ensureLayout();
        refreshWidgets();

        renderTopBar(graphics);
        renderList(graphics, mouseX, mouseY);
        renderBottomBar(graphics);
        // 控件画在两条栏之上
        widgets.render(graphics, mouseX, mouseY);
        // 面包屑画在控件之上：展开的同级目录下拉要盖住列表与输入框
        updateBreadcrumb();
        breadcrumb.render(graphics, mouseX, mouseY);
        renderStatus(graphics);

        if (dialog != null) {
            // 二次确认弹窗必须盖在所有东西之上
            dialog.update(width, height);
            dialog.render(graphics, mouseX, mouseY);
        }

        Draw.TruncatedText truncated = Draw.truncatedAt(mouseX, mouseY);

        if (truncated != null) {
            Draw.tooltip(graphics, truncated.text(), mouseX, mouseY, width, height);
        }
    }

    /// 顶栏：第一行面包屑，第二行地址栏；两行之间加一条浅分隔线
    private void renderTopBar(GuiGraphicsExtractor graphics) {
        UiRect rect = topBarRect();
        Draw.canvas(graphics, rect, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, rect.x(), rect.right(), rect.bottom() - 1, Draw.BORDER);
        Draw.hLine(graphics, rect.x() + 4, rect.right() - 4, BAR_ROW_HEIGHT, Draw.GRID);

        if (addressLabelVisible) {
            Draw.text(graphics, EditorLang.t("file_browser.address"), addressLabelX,
                    BAR_ROW_HEIGHT + BAR_ROW_HEIGHT / 2 - 4, Draw.TEXT_DIM);
        }
    }

    /// 中部：目录内容列表
    private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect list = listRect();
        Draw.canvas(graphics, list, Draw.CANVAS_BG);
        Draw.border(graphics, list, Draw.BORDER);

        graphics.enableScissor(list.x() + 1, list.y() + 1, list.right() - 1, list.bottom() - 1);

        if (entries.isEmpty()) {
            Draw.textEllipsized(graphics, EditorLang.t("file_browser.empty").getString(), list.x() + 5, list.y() + 4,
                    Math.max(8, list.width() - 10), Draw.TEXT_DISABLED);
        }

        int rowWidth = Math.max(1, list.width() - 8 - SCROLLBAR_WIDTH);

        for (int i = 0; i < entries.size(); i++) {
            int rowY = list.y() + LIST_PADDING + i * ROW_HEIGHT - scrollY;

            if (rowY + ROW_HEIGHT <= list.y() || rowY >= list.bottom()) {
                continue;
            }

            Entry entry = entries.get(i);
            UiRect row = new UiRect(list.x() + 3, rowY, rowWidth, ROW_HEIGHT);
            boolean hovered = list.contains(mouseX, mouseY) && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (i == selectedIndex) {
                Draw.canvas(graphics, row, Draw.ROW_SELECTED);
            } else if (hovered) {
                Draw.canvas(graphics, row, Draw.BUTTON_BG_HOVER);
            } else if (i % 2 == 1) {
                Draw.canvas(graphics, row, Draw.ROW_ALT);
            }

            // 文件夹加一个展开箭头前缀，文件用空格占位保持对齐
            String label = (entry.directory() ? Icons.EXPAND : " ") + " " + entry.name();
            Draw.textEllipsized(graphics, label, row.x() + 3, row.y() + 3, row.width() - 6,
                    entry.directory() ? Draw.ACCENT : Draw.TEXT);
        }

        graphics.disableScissor();
        renderScrollbar(graphics, list);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, UiRect list) {
        int totalHeight = entries.size() * ROW_HEIGHT + LIST_PADDING * 2;

        if (totalHeight <= list.height()) {
            return;
        }

        int trackX = list.right() - SCROLLBAR_WIDTH - 3;
        int trackTop = list.y() + 2;
        int trackHeight = Math.max(1, list.height() - 4);
        int maxScroll = Math.max(1, totalHeight - list.height());
        int thumbHeight = Mth.clamp(Math.round((float) trackHeight * list.height() / totalHeight), 8, trackHeight);
        int thumbTop = trackTop + Math.round((float) (trackHeight - thumbHeight) * scrollY / maxScroll);
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + trackHeight, Draw.GRID);
        graphics.fill(trackX, thumbTop, trackX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, Draw.BORDER);
    }

    /// 底栏：文件名标签（保存 / 打开模式共用）
    private void renderBottomBar(GuiGraphicsExtractor graphics) {
        UiRect rect = bottomBarRect();
        Draw.canvas(graphics, rect, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, rect.x(), rect.right(), rect.y(), Draw.BORDER);

        if (nameLabelVisible) {
            Draw.text(graphics, EditorLang.t("file_browser.name"), nameLabelX, rect.centerY() - 4, Draw.TEXT_DIM);
        }
    }

    /// 底栏上方的提示行：优先显示地址栏跳转失败的提示，其次是保存模式下的覆盖提示
    private void renderStatus(GuiGraphicsExtractor graphics) {
        UiRect rect = statusRect();

        if (rect.width() <= 8) {
            return;
        }

        if (statusMessage != null) {
            Draw.textEllipsized(graphics, statusMessage, rect.x(), rect.y(), rect.width(), Draw.ACCENT);
            return;
        }

        if (nameField == null || !save) {
            return;
        }

        String name = nameField.value().strip();

        if (name.isEmpty() || !Files.exists(targetFile(name))) {
            return;
        }

        Draw.textEllipsized(graphics, EditorLang.t("file_browser.overwrite", name).getString(), rect.x(), rect.y(),
                rect.width(), Draw.ACCENT);
    }

    // endregion

    // region 输入

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ensureLayout();

        // 确认弹窗盖住一切，先把它处理完
        if (dialog != null) {
            ConfirmDialog current = dialog;
            current.mouseClicked(event, doubleClick);

            if (current.finished()) {
                dialog = null;
            }

            return true;
        }

        // 面包屑优先：点段跳转、点箭头展开同级目录，下拉展开时也要先把它关掉
        if (breadcrumb.mouseClicked(event)) {
            return true;
        }

        // 先提交正在编辑的输入框，保证按钮读到的是最新文件名
        widgets.focus(null);
        refreshWidgets();

        if (topBarRect().contains(event.x(), event.y()) || bottomBarRect().contains(event.x(), event.y())) {
            widgets.mouseClicked(event, doubleClick);
            return true;
        }

        if (listRect().contains(event.x(), event.y())) {
            clickRow(event, doubleClick);
            return true;
        }

        return true;
    }

    /// 列表点击：单击选中，双击文件夹进入 / 文件执行主操作
    private void clickRow(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }

        UiRect list = listRect();
        int index = (int) ((event.y() - list.y() - LIST_PADDING + scrollY) / ROW_HEIGHT);

        if (index < 0 || index >= entries.size()) {
            // 点在列表空白处：取消选中
            selectedIndex = -1;
            return;
        }

        selectedIndex = index;
        Entry entry = entries.get(index);

        if (!doubleClick) {
            return;
        }

        if (entry.directory()) {
            enterDirectory(entry.path());
        } else if (save) {
            // 双击文件：把它的名字填进输入框，便于「另存 / 覆盖」
            if (nameField != null) {
                nameField.value(entry.name());
            }
        } else {
            confirmSelection();
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dialog != null) {
            ConfirmDialog current = dialog;
            dialog.mouseReleased(event);

            // 按钮动作在松开时执行，所以关闭判断也要在这里做一遍：
            // 只在点击时关的话，确认后弹窗会留着，再点一次就把动作又执行一遍
            if (dialog.finished() && dialog == current) {
                dialog = null;
            }

            return true;
        }

        widgets.mouseReleased(event);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ensureLayout();

        // 面包屑的同级目录下拉在最上层，滚动先给它
        if (breadcrumb.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        if (widgets.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }

        if (!listRect().contains(mouseX, mouseY)) {
            return false;
        }

        int delta = (int) (scrollY * ROW_HEIGHT * 2);

        if (delta == 0 && scrollY != 0) {
            delta = scrollY > 0 ? ROW_HEIGHT : -ROW_HEIGHT;
        }

        scrollY = Mth.clamp(scrollY - delta, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        // 弹窗打开时：Esc 只关弹窗
        if (dialog != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                dialog = null;
            }

            return true;
        }

        // 面包屑下拉展开时，Esc 只收起它
        if (breadcrumb.menuOpen() && key == GLFW.GLFW_KEY_ESCAPE) {
            breadcrumb.closeMenu();
            return true;
        }

        // 地址栏回车：先让输入框提交内容，再按提交后的路径跳转
        // （只有回车才跳转，点到别处失焦只是提交文本，不会误建目录）
        if (addressField != null && widgets.focused() == addressField && addressField.editing()
                && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            widgets.keyPressed(event);
            navigateTo(addressField.value());
            return true;
        }

        // 其余输入框优先：Esc / 回车由它先消化，避免误退出界面
        if (widgets.keyPressed(event)) {
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return widgets.charTyped(event);
    }

    // endregion
}
