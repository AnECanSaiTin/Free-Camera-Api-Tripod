package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationSavedData;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// 编辑器自绘的「存档数据浏览器」界面：列出当前单机存档里已经存在的动画或路径。
///
/// 与 {@link FileBrowserScreen} 的区别在于数据来源：本界面列的是存档内的数据条目
/// （{@link AnimationSavedData}），不是本地文件。条目名支持用 {@code /} 分隔的多层级，
/// 所以这里用与资源管理器相同的方式浏览：面包屑显示当前层级，双击文件夹进入下一层。
///
/// - 顶栏第一行：标题（打开 / 保存 × 动画 / 路径）
/// - 顶栏第二行：「上一级」按钮 + 面包屑（段尾箭头展开同级文件夹）+ 关闭按钮
/// - 中部：当前文件夹下的条目列表（文件夹在前），超出时滚轮滚动并带细滚动条
/// - 保存模式：列表下方多一个名字输入框（点已有条目可填入名字，便于覆盖）
/// - 底栏：「新建文件夹」「保存 / 打开」与「取消」
///
/// 交互：单击选中，双击文件夹进入、双击条目确认；保存模式下目标名字已存在时先弹覆盖确认。
/// 本界面只负责挑选名字（可含层级），真正的读写由调用方在回调里完成。
public class StorageBrowserScreen extends Screen {
    /// 标题行高度
    private static final int TITLE_ROW_HEIGHT = 16;
    /// 路径行高度（上一级 + 面包屑 + 关闭）
    private static final int BAR_ROW_HEIGHT = 18;
    private static final int TOP_BAR_HEIGHT = TITLE_ROW_HEIGHT + BAR_ROW_HEIGHT;
    private static final int BOTTOM_BAR_HEIGHT = 22;
    private static final int CONTROL_HEIGHT = 14;
    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 13;
    private static final int LIST_PADDING = 2;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int BUTTON_GAP = 4;
    /// 提示行与「名字」标签行的高度
    private static final int HINT_ROW_HEIGHT = 11;
    private static final int NAME_LABEL_HEIGHT = 10;
    /// 保存模式下的名字长度上限，与 TextFieldWidget 的默认值一致
    private static final int NAME_MAX_LENGTH = 48;
    /// 新建文件夹的默认名（ASCII，与本地文件浏览界面保持一致）
    private static final String DEFAULT_FOLDER_NAME = "new_folder";
    /// 层级分隔符，与 {@link AnimationSavedData} 一致
    private static final String SEPARATOR = "/";
    private static final int CLOSE_BUTTON_WIDTH = 16;

    /// 保存模式为 true，打开模式为 false
    private final boolean save;
    /// 操作对象为存档里的「路径」时为 true，为「动画」时为 false
    private final boolean path;
    /// 打开本界面的来源界面，关闭时返回；没有来源界面时为 null
    private final @Nullable Screen parent;
    private final Consumer<String> picked;
    private final WidgetHost widgets = new WidgetHost();
    /// 当前文件夹下的条目（文件夹在前，同类按名字排序）
    private final List<Entry> entries = new ArrayList<>();
    /// 顶栏第二行的面包屑路径
    private final BreadcrumbBar breadcrumb = new BreadcrumbBar();
    /// 当前所在文件夹，相对存档根，空串表示根；层级用 / 分隔
    private String folder = "";
    /// 标题文本，构造时按模式定好
    private final Component screenTitle;
    private @Nullable TextFieldWidget nameField;
    private @Nullable ButtonWidget primaryButton;
    private @Nullable ButtonWidget upButton;
    private @Nullable ConfirmDialog dialog;
    private UiRect upButtonRect = new UiRect(0, 0, 0, 0);
    private UiRect closeButtonRect = new UiRect(0, 0, 0, 0);
    /// 面包屑区域：顶栏第二行里「上一级」与关闭按钮之间的整段
    private UiRect breadcrumbRect = new UiRect(0, 0, 0, 0);
    private int selectedIndex = -1;
    private int scrollY;
    /// 上次构建控件时的屏幕尺寸，尺寸变化才重建，避免打断输入框编辑状态
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    /// 保存模式下名字输入框的当前值，重建控件时取用（输入框失焦提交后同步）
    private String defaultName = "";

    public StorageBrowserScreen(boolean save, boolean path, @Nullable String defaultName, Consumer<String> picked) {
        super(EditorLang.t(titleKey(save, path)));
        this.save = save;
        this.path = path;
        this.screenTitle = EditorLang.t(titleKey(save, path));
        this.picked = picked;
        // 构造时当前界面仍是来源界面，因为 setScreen 在参数求值之后才切换
        this.parent = Minecraft.getInstance().screen;
        this.defaultName = save && defaultName != null ? defaultName : "";

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

    /// 读取当前文件夹下的条目；没有存档（多人游戏或未进世界）时列表为空
    private void reload() {
        entries.clear();

        for (String name : AnimationSavedData.listFolders(path, folder)) {
            entries.add(new Entry(name, fullName(name), true));
        }

        for (String name : AnimationSavedData.listFiles(path, folder)) {
            entries.add(new Entry(name, fullName(name), false));
        }

        if (selectedIndex >= entries.size()) {
            selectedIndex = -1;
        }

        scrollY = Mth.clamp(scrollY, 0, maxScroll());
    }

    /// 当前文件夹下某个条目的全名
    private String fullName(String name) {
        return AnimationSavedData.join(folder, name);
    }

    private @Nullable Entry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < entries.size() ? entries.get(selectedIndex) : null;
    }

    /// 列表条目：文件夹或数据条目；name 为显示名，fullName 为含层级的完整名字
    private record Entry(String name, String fullName, boolean isFolder) {
    }

    /// 进入某个文件夹（空串表示回到根）
    private void enterFolder(String fullName) {
        folder = fullName == null ? "" : fullName;
        selectedIndex = -1;
        scrollY = 0;
        reload();
    }

    /// 返回上一层；已在根时不做任何事
    private void goUp() {
        int separator = folder.lastIndexOf(SEPARATOR);
        enterFolder(separator < 0 ? "" : folder.substring(0, separator));
    }

    private boolean canGoUp() {
        return !folder.isEmpty();
    }

    /// 在当前文件夹下建一个不重名的文件夹
    private void newFolder() {
        Set<String> taken = new HashSet<>();
        taken.addAll(AnimationSavedData.listFolders(path, folder));
        taken.addAll(AnimationSavedData.listFiles(path, folder));

        String name = DEFAULT_FOLDER_NAME;

        for (int i = 2; taken.contains(name); i++) {
            name = DEFAULT_FOLDER_NAME + "_" + i;
        }

        AnimationSavedData.createFolder(path, fullName(name));
        reload();

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name().equals(name)) {
                selectedIndex = i;
                break;
            }
        }
    }

    private int maxScroll() {
        return Math.max(0, entries.size() * ROW_HEIGHT + LIST_PADDING * 2 - listRect().height());
    }

    // endregion

    // region 面包屑

    /// 刷新面包屑：第一段是存档根，其后是当前层级的每一级；段尾箭头列出同级文件夹
    private void updateBreadcrumb() {
        breadcrumb.layout(breadcrumbRect, width, height);
        breadcrumb.clear();
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

    // region 几何

    private UiRect topBarRect() {
        return new UiRect(0, 0, width, TOP_BAR_HEIGHT);
    }

    private UiRect bottomBarRect() {
        return new UiRect(0, Math.max(0, height - BOTTOM_BAR_HEIGHT), width, BOTTOM_BAR_HEIGHT);
    }

    /// 底栏上方的提示行
    private UiRect hintRect() {
        UiRect bar = bottomBarRect();
        return new UiRect(PADDING, Math.max(0, bar.y() - HINT_ROW_HEIGHT), Math.max(0, width - PADDING * 2), HINT_ROW_HEIGHT);
    }

    /// 名字输入框下方的标签行（仅保存模式绘制）
    private UiRect nameLabelRect() {
        UiRect hint = hintRect();
        return new UiRect(PADDING, Math.max(0, hint.y() - NAME_LABEL_HEIGHT), Math.max(0, width - PADDING * 2), NAME_LABEL_HEIGHT);
    }

    /// 名字输入框（仅保存模式）
    private UiRect nameFieldRect() {
        UiRect label = nameLabelRect();
        return new UiRect(PADDING, Math.max(0, label.y() - CONTROL_HEIGHT - 2), Math.max(1, width - PADDING * 2), CONTROL_HEIGHT);
    }

    /// 中部名字列表：上到顶栏下方，下到名字输入框 / 提示行上方
    private UiRect listRect() {
        int top = TOP_BAR_HEIGHT + 4;
        int bottom = Math.max(top + 1, (save ? nameFieldRect().y() : hintRect().y()) - 4);
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
        nameField = null;
        primaryButton = null;

        // 顶栏第二行：上一级 + 面包屑 + 关闭
        int rowY = TITLE_ROW_HEIGHT + (BAR_ROW_HEIGHT - CONTROL_HEIGHT) / 2;
        upButtonRect = new UiRect(PADDING, rowY, textButtonWidth("storage_browser.up"), CONTROL_HEIGHT);
        upButton = widgets.add(new ButtonWidget(upButtonRect, EditorLang.t("storage_browser.up"), this::goUp));
        upButton.enabled(canGoUp());

        closeButtonRect = new UiRect(Math.max(PADDING, width - PADDING - CLOSE_BUTTON_WIDTH), rowY,
                CLOSE_BUTTON_WIDTH, CONTROL_HEIGHT);
        widgets.add(new ButtonWidget(closeButtonRect, Component.literal(Icons.CLOSE), this::onClose)
                .tooltip(EditorLang.t("common.cancel")));

        // 面包屑占据第二行剩余整段
        int breadcrumbX = upButtonRect.right() + 4;
        int breadcrumbRight = closeButtonRect.x() - 4;
        breadcrumbRect = new UiRect(breadcrumbX, TITLE_ROW_HEIGHT + 1,
                Math.max(0, breadcrumbRight - breadcrumbX), BAR_ROW_HEIGHT - 2);

        buildBottomBar();
    }

    /// 底栏：左侧「新建文件夹」，右侧「取消」与「保存 / 打开」，从右往左排布
    private void buildBottomBar() {
        UiRect bottomBar = bottomBarRect();
        int y = bottomBar.y() + (BOTTOM_BAR_HEIGHT - CONTROL_HEIGHT) / 2;
        int cursor = bottomBar.right() - PADDING;

        int cancelWidth = textButtonWidth("common.cancel");
        UiRect cancelRect = new UiRect(Math.max(PADDING, cursor - cancelWidth), y, cancelWidth, CONTROL_HEIGHT);
        widgets.add(new ButtonWidget(cancelRect, EditorLang.t("common.cancel"), this::onClose));
        cursor = cancelRect.x() - BUTTON_GAP;

        int primaryWidth = textButtonWidth(save ? "file_browser.save" : "file_browser.open");
        UiRect primaryRect = new UiRect(Math.max(PADDING, cursor - primaryWidth), y, primaryWidth, CONTROL_HEIGHT);
        primaryButton = widgets.add(new ButtonWidget(primaryRect, EditorLang.t(save ? "file_browser.save" : "file_browser.open"),
                this::confirmSelection).accent(true));

        int newFolderWidth = textButtonWidth("storage_browser.new_folder");
        widgets.add(new ButtonWidget(new UiRect(PADDING, y, Math.max(1, newFolderWidth), CONTROL_HEIGHT),
                EditorLang.t("storage_browser.new_folder"), this::newFolder));

        if (save) {
            UiRect field = nameFieldRect();
            // 输入内容按需读取（提交后才生效），这里不需要额外响应
            nameField = widgets.add(new TextFieldWidget(field, defaultName, value -> { }).maxLength(NAME_MAX_LENGTH));
        }
    }

    /// 按钮宽度：随文字长度自适应，空出左右内边距
    private static int textButtonWidth(String key) {
        return Draw.font().width(EditorLang.t(key).getString()) + 10;
    }

    /// 每帧同步控件的可用状态与显示内容
    private void refreshWidgets() {
        if (upButton != null) {
            upButton.enabled(canGoUp());
        }

        if (nameField != null) {
            nameField.visible(true);
            nameField.enabled(true);

            if (!nameField.editing()) {
                // 失焦提交后把值留下来，重建控件时不会丢
                defaultName = nameField.value();
            }
        }

        if (primaryButton != null) {
            primaryButton.enabled(primaryEnabled());
        }
    }

    /// 主按钮可用条件：保存模式要填了名字，打开模式要选中一个条目（文件夹不能选）
    private boolean primaryEnabled() {
        if (save) {
            // 输入框正在编辑时拿不到编辑中的文本，先按可用处理，点击时提交后再判定
            return nameField != null && (nameField.editing() || !nameField.value().strip().isEmpty());
        }

        Entry entry = selectedEntry();
        return entry != null && !entry.isFolder();
    }

    // endregion

    // region 主操作

    /// 主按钮：保存模式回调名字（已存在先确认覆盖），打开模式回调选中条目的全名
    private void confirmSelection() {
        if (save) {
            if (nameField == null) {
                return;
            }

            // 先提交正在编辑的文本，保证读到的是最新名字
            widgets.focus(null);
            String target = normalizeTarget(nameField.value());

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

        Entry entry = selectedEntry();

        if (entry == null || entry.isFolder()) {
            return;
        }

        finish(entry.fullName());
    }

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

    /// 回调结果并关闭界面
    private void finish(String name) {
        picked.accept(name);
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

    /// 暂停世界：浏览存档数据期间世界不再变化
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
        // 面包屑画在控件之上：展开的同级文件夹下拉要盖住列表
        updateBreadcrumb();
        breadcrumb.render(graphics, mouseX, mouseY);
        renderHint(graphics);

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

    /// 顶栏：第一行居中标题，第二行「上一级 + 面包屑 + 关闭」
    private void renderTopBar(GuiGraphicsExtractor graphics) {
        UiRect rect = topBarRect();
        Draw.canvas(graphics, rect, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, rect.x(), rect.right(), rect.bottom() - 1, Draw.BORDER);
        Draw.hLine(graphics, rect.x() + 4, rect.right() - 4, TITLE_ROW_HEIGHT, Draw.GRID);
        Draw.textCentered(graphics, screenTitle, rect.centerX(), TITLE_ROW_HEIGHT / 2 - 4, Draw.TEXT);
    }

    /// 中部：存档内的条目列表
    private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect list = listRect();
        Draw.canvas(graphics, list, Draw.CANVAS_BG);
        Draw.border(graphics, list, Draw.BORDER);

        graphics.enableScissor(list.x() + 1, list.y() + 1, list.right() - 1, list.bottom() - 1);

        if (entries.isEmpty()) {
            Draw.textEllipsized(graphics, EditorLang.t("storage_browser.empty").getString(), list.x() + 5, list.y() + 4,
                    Math.max(8, list.width() - 10), Draw.TEXT_DISABLED);
        }

        int rowWidth = Math.max(1, list.width() - 8 - SCROLLBAR_WIDTH);

        for (int i = 0; i < entries.size(); i++) {
            int rowY = list.y() + LIST_PADDING + i * ROW_HEIGHT - scrollY;

            if (rowY + ROW_HEIGHT <= list.y() || rowY >= list.bottom()) {
                continue;
            }

            UiRect row = new UiRect(list.x() + 3, rowY, rowWidth, ROW_HEIGHT);
            boolean hovered = list.contains(mouseX, mouseY) && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (i == selectedIndex) {
                Draw.canvas(graphics, row, Draw.ROW_SELECTED);
            } else if (hovered) {
                Draw.canvas(graphics, row, Draw.BUTTON_BG_HOVER);
            } else if (i % 2 == 1) {
                Draw.canvas(graphics, row, Draw.ROW_ALT);
            }

            Entry entry = entries.get(i);
            // 文件夹加一个展开箭头前缀，条目用空格占位保持对齐
            String label = (entry.isFolder() ? Icons.EXPAND : " ") + " " + entry.name();
            Draw.textEllipsized(graphics, label, row.x() + 3, row.y() + 3, row.width() - 6,
                    entry.isFolder() ? Draw.ACCENT : Draw.TEXT);
        }

        graphics.disableScissor();
        renderScrollbar(graphics, list);
    }

    /// 右侧细滚动条：内容超出列表高度时才画
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

    /// 底栏与提示行：底栏底色 + 输入框下方的「名字」标签
    private void renderBottomBar(GuiGraphicsExtractor graphics) {
        UiRect rect = bottomBarRect();
        Draw.canvas(graphics, rect, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, rect.x(), rect.right(), rect.y(), Draw.BORDER);

        if (!save) {
            return;
        }

        UiRect label = nameLabelRect();

        if (label.height() > 0) {
            Draw.text(graphics, EditorLang.t("storage_browser.name"), label.x(), label.y() + 1, Draw.TEXT_DIM);
        }
    }

    /// 提示行：说明这里是存档内的数据，不是本地文件
    private void renderHint(GuiGraphicsExtractor graphics) {
        UiRect rect = hintRect();

        if (rect.width() <= 8 || rect.height() <= 0) {
            return;
        }

        Draw.textEllipsized(graphics, EditorLang.t("storage_browser.hint").getString(), rect.x(), rect.y() + 1,
                rect.width(), Draw.TEXT_DIM);
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

        // 面包屑优先：点段跳转、点箭头展开同级文件夹，下拉展开时也要先把它关掉
        if (breadcrumb.mouseClicked(event)) {
            return true;
        }

        // 先提交正在编辑的输入框，保证按钮读到的是最新名字
        widgets.focus(null);
        refreshWidgets();

        if (widgets.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (listRect().contains(event.x(), event.y())) {
            clickRow(event, doubleClick);
        }

        return true;
    }

    /// 列表点击：单击选中，双击文件夹进入 / 条目确认；保存模式下点已有条目会填进输入框便于覆盖
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

        if (entry.isFolder()) {
            enterFolder(entry.fullName());
            return;
        }

        if (save) {
            // 双击已有条目：填进输入框，便于「另存 / 覆盖」
            if (nameField != null) {
                nameField.value(entry.name());
            }

            return;
        }

        confirmSelection();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dialog != null) {
            dialog.mouseReleased(event);
            return true;
        }

        widgets.mouseReleased(event);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ensureLayout();

        // 面包屑的同级文件夹下拉在最上层，滚动先给它
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

        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;

        // 面包屑下拉展开时，Esc 只收起它
        if (breadcrumb.menuOpen() && key == GLFW.GLFW_KEY_ESCAPE) {
            breadcrumb.closeMenu();
            return true;
        }

        // 输入框优先：Esc / 回车由它先消化，避免误退出界面
        if (widgets.keyPressed(event)) {
            if (enter) {
                // 输入框刚提交完文本，直接确认（与地址栏回车跳转一致的手感）
                confirmSelection();
            }

            return true;
        }

        if (enter) {
            confirmSelection();
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
