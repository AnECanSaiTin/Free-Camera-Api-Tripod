package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.DockLayout;
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

/// 浏览界面基类：本地文件与存档数据两套浏览界面共用的骨架。
///
/// 三块内容共用，这几套界面「看起来是同一个」的原因也在这里：
/// - 顶栏：标题行 + 「上一级 / 面包屑 / 关闭」行 + 可选的第二行
///   （本地文件放地址栏，存档数据管理放「动画 / 路径」来源切换，没有内容的界面直接把这一行去掉）
/// - 中部：当前层级下的条目列表，容器排在条目前面，带选中、悬停、滚动与细滚动条
/// - 底栏：左侧是子类登记的按钮（新建文件夹等），右侧是「取消」与主按钮，
///   中间放名字输入框（保存类界面用），底栏上方留一行提示 / 状态
///
/// 子类只负责数据与语义：列什么、进哪一层、主按钮做什么。渲染与事件分派都在这里，
/// 因此界面之间的差异只来自数据源，不会各自重画一套像素。
///
/// 所有目录 / 存档读取异常都由子类在内部消化（读不出来就当空列表），界面不会因此中断。
public abstract class BrowserScreen extends Screen {
    /// 标题行高度：界面名居中显示
    protected static final int TITLE_ROW_HEIGHT = 16;
    /// 面包屑行高度
    protected static final int BAR_ROW_HEIGHT = 18;
    /// 可选第二行的高度
    protected static final int EXTRA_ROW_HEIGHT = 18;
    protected static final int BOTTOM_BAR_HEIGHT = 22;
    /// 栏内按钮与单行输入框的统一高度（取全局统一值）
    protected static final int CONTROL_HEIGHT = DockLayout.TOOL_BUTTON_HEIGHT;
    protected static final int PADDING = 6;
    protected static final int ROW_HEIGHT = 13;
    protected static final int LIST_PADDING = 2;
    protected static final int SCROLLBAR_WIDTH = 3;
    /// 关闭按钮：与其它按钮同高的方形按钮
    protected static final int CLOSE_BUTTON_WIDTH = DockLayout.TOOL_BUTTON_HEIGHT;
    /// 底栏上方的提示行高度
    protected static final int HINT_ROW_HEIGHT = 11;
    /// 提示行至少要有这么宽才绘制
    protected static final int STATUS_MIN_WIDTH = 24;
    protected static final int BUTTON_GAP = 4;
    /// 新建文件夹的默认名（ASCII，避免不同文件系统 / 存档条目名下的编码问题）
    private static final String DEFAULT_FOLDER_NAME = "new_folder";
    /// 名字输入框的宽度区间：空间紧张时先压缩输入框，保证按钮完整可见
    private static final int NAME_FIELD_MAX_WIDTH = 200;
    private static final int NAME_FIELD_MIN_WIDTH = 60;

    /// 列表条目：显示名 + 定位键 + 是否是可进入的容器（文件夹）。
    ///
    /// 键由子类自己解释（本地文件是绝对路径，存档数据是带层级的全名），基类只把它原样回传。
    protected record Row(String name, String key, boolean container) {
    }

    private final Component screenTitle;
    /// 打开本界面的来源界面，关闭时返回；没有来源界面时为 null
    private final @Nullable Screen parent;
    protected final WidgetHost widgets = new WidgetHost();
    protected final BreadcrumbBar breadcrumb = new BreadcrumbBar();
    /// 当前层级的条目（容器在前，由子类排好序）
    protected final List<Row> rows = new ArrayList<>();
    /// 二次确认弹窗（覆盖确认、删除确认等）
    protected @Nullable ConfirmDialog dialog;
    /// 一次性状态提示（删除结果、地址栏跳转失败等），切换层级或重读数据时清除
    protected @Nullable String statusMessage;
    protected int selectedIndex = -1;
    protected int scrollY;
    /// 底栏左侧按钮：子类在 {@link #buildFooterLeft()} 里登记，位置由基类排
    private final List<ButtonWidget> footerButtons = new ArrayList<>();
    private @Nullable ButtonWidget upButton;
    private @Nullable ButtonWidget primaryButton;
    private @Nullable TextFieldWidget nameField;
    /// 名字输入框的初始值；失焦提交后同步，控件重建时取用
    protected String nameValue = "";
    /// 底栏的「名字」标签是否绘制得下
    private boolean nameLabelVisible;
    /// 面包屑区域：面包屑行里「上一级」与关闭按钮之间的整段
    private UiRect breadcrumbRect = new UiRect(0, 0, 0, 0);
    /// 上次构建控件时的屏幕尺寸，尺寸变化才重建，避免打断输入框编辑状态
    private int layoutWidth = -1;
    private int layoutHeight = -1;

    protected BrowserScreen(Component title) {
        super(title);
        this.screenTitle = title;
        // 构造时当前界面仍是来源界面，因为 setScreen 在参数求值之后才切换
        this.parent = Minecraft.getInstance().screen;
    }

    // region 数据（子类实现）

    /// 重读当前层级的条目写进 {@link #rows}（容器在前，同类按名字排序）
    protected abstract void reloadRows();

    /// 刷新面包屑：基类每帧已经清空上一帧的段并设好可用区域，这里从根到当前层级依次 {@code add} 即可
    protected abstract void refreshCrumbs();

    /// 当前层级是否还有上一级
    protected abstract boolean canGoUp();

    /// 返回上一级
    protected abstract void goUp();

    /// 一行被点击；单击时 {@link #selectedIndex} 已经指向它。
    /// 点在列表空白处时 index 为 -1，此时 selectedIndex 已清空
    protected abstract void rowClicked(int index, @Nullable Row row, boolean doubleClick);

    /// 列表为空时的提示
    protected abstract Component emptyText();

    /// 底栏上方提示行的文本；返回 null 表示这一行留空
    protected abstract @Nullable Component hintText();

    /// 主按钮的文本
    protected abstract Component primaryLabel();

    /// 主按钮当前是否可用
    protected abstract boolean primaryEnabled();

    /// 主按钮的动作（回车键也走这里）
    protected abstract void primaryAction();

    // endregion

    // region 可选扩展点

    /// 顶栏第二行的高度；返回 0 表示不需要这一行
    protected int extraRowHeight() {
        return 0;
    }

    /// 构建顶栏第二行的控件（只在 {@link #extraRowHeight()} 大于 0 时调用）
    protected void buildExtraRow(UiRect row) {
    }

    /// 绘制顶栏第二行里控件之外的静态内容（标签等）
    protected void renderExtraRow(GuiGraphicsExtractor graphics, UiRect row) {
    }

    /// 每帧刷新子类自己的控件（第二行与底栏左侧按钮的开关状态等）
    protected void refreshExtraWidgets() {
    }

    /// 底栏左侧按钮的登记入口，位置由基类从左往右排
    protected final ButtonWidget addFooterButton(Component label, Runnable action) {
        ButtonWidget button = new ButtonWidget(new UiRect(0, 0, textWidth(label), CONTROL_HEIGHT), label, action);
        footerButtons.add(button);
        widgets.add(button);
        return button;
    }

    /// 子类登记底栏左侧按钮
    protected void buildFooterLeft() {
    }

    /// 是否有名字输入框
    protected boolean nameFieldPresent() {
        return false;
    }

    /// 名字输入框是否可编辑；不可编辑时只回显选中条目的名字
    protected boolean nameFieldEditable() {
        return false;
    }

    /// 名字输入框左侧的标签；没有名字输入框的界面用不到它
    protected Component nameLabel() {
        return Component.empty();
    }

    /// 主按钮的悬停提示；返回 null 表示不加提示
    protected @Nullable Component primaryTooltip() {
        return null;
    }

    // endregion

    // region 数据加载

    /// 重读数据并修正选中与滚动位置
    protected void reload() {
        rows.clear();
        reloadRows();

        if (selectedIndex >= rows.size()) {
            selectedIndex = -1;
        }

        scrollY = Mth.clamp(scrollY, 0, maxScroll());
    }

    /// 进入新层级后的公共收尾：清掉选中、滚动与旧提示，再重读数据
    protected void enteredContainer() {
        selectedIndex = -1;
        scrollY = 0;
        statusMessage = null;
        reload();
    }

    protected @Nullable Row selectedRow() {
        return selectedIndex >= 0 && selectedIndex < rows.size() ? rows.get(selectedIndex) : null;
    }

    protected int maxScroll() {
        return Math.max(0, rows.size() * ROW_HEIGHT + LIST_PADDING * 2 - listRect().height());
    }

    /// 名字输入框里的当前内容（控件还没构建时用初始值）
    protected String nameFieldValue() {
        return nameField == null ? nameValue : nameField.value();
    }

    /// 名字输入框是否正在编辑：编辑中的文本还没提交，{@link #nameFieldValue()} 读到的是旧值，
    /// 判定主按钮可用状态时要把它算进来，否则刚点开输入框还没回车按钮就变灰了
    protected boolean nameFieldEditing() {
        return nameField != null && nameField.editing();
    }

    /// 写入名字输入框并记住它，控件重建后仍是这个值
    protected void nameFieldValue(String value) {
        nameValue = value;

        if (nameField != null) {
            nameField.value(value);
        }
    }

    /// 提交正在编辑的输入框，保证随后读到的是最新内容
    protected void commitNameField() {
        widgets.focus(null);
        refreshWidgets();
    }

    // endregion

    // region 新建文件夹

    /// 新建文件夹：先取一个当前层级里没被占用的名字，交给子类真正创建，最后选中新建的容器。
    ///
    /// 命名规则与建完之后的选中收尾都在这里统一，本地文件与存档数据只是「创建」的实现不同，
    /// 因此在哪个界面点「新建文件夹」，行为与结果都一致。
    protected final void newFolder() {
        Set<String> taken = new HashSet<>(takenNames());
        String name = DEFAULT_FOLDER_NAME;

        for (int i = 2; taken.contains(name); i++) {
            name = DEFAULT_FOLDER_NAME + "_" + i;
        }

        if (!createFolder(name)) {
            return;
        }

        statusMessage = null;
        reload();

        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).name().equals(name)) {
                selectedIndex = i;
                break;
            }
        }
    }

    /// 当前层级里已被占用的名字（容器与条目都算），新建文件夹时用来避重名
    protected List<String> takenNames() {
        return List.of();
    }

    /// 在当前层级建立一个文件夹；返回 false 表示没建成（无权限、数据源不可写等），界面保持原状
    protected boolean createFolder(String name) {
        return false;
    }

    // endregion

    // region 几何

    /// 顶栏总高：标题行 + 面包屑行 + 可选第二行
    protected UiRect topBarRect() {
        return new UiRect(0, 0, width, TITLE_ROW_HEIGHT + BAR_ROW_HEIGHT + extraRowHeight());
    }

    private UiRect titleRowRect() {
        return new UiRect(0, 0, width, TITLE_ROW_HEIGHT);
    }

    private UiRect crumbRowRect() {
        return new UiRect(0, TITLE_ROW_HEIGHT, width, BAR_ROW_HEIGHT);
    }

    /// 顶栏第二行（没有这一行时高度为 0）
    protected UiRect extraRowRect() {
        return new UiRect(0, TITLE_ROW_HEIGHT + BAR_ROW_HEIGHT, width, extraRowHeight());
    }

    protected UiRect bottomBarRect() {
        return new UiRect(0, Math.max(0, height - BOTTOM_BAR_HEIGHT), width, BOTTOM_BAR_HEIGHT);
    }

    /// 底栏上方的提示行
    protected UiRect hintRect() {
        UiRect bar = bottomBarRect();
        return new UiRect(PADDING, Math.max(0, bar.y() - HINT_ROW_HEIGHT), Math.max(0, width - PADDING * 2), HINT_ROW_HEIGHT);
    }

    /// 中部列表：上到顶栏下方，下到提示行上方
    protected UiRect listRect() {
        int top = TITLE_ROW_HEIGHT + BAR_ROW_HEIGHT + extraRowHeight() + 4;
        int bottom = Math.max(top + 1, hintRect().y() - 4);
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
        footerButtons.clear();
        upButton = null;
        primaryButton = null;
        nameField = null;
        nameLabelVisible = false;

        if (extraRowHeight() > 0) {
            buildExtraRow(extraRowRect());
        }

        UiRect crumbRow = crumbRowRect();
        int rowY = crumbRow.y() + (BAR_ROW_HEIGHT - CONTROL_HEIGHT) / 2;
        UiRect upButtonRect = new UiRect(PADDING, rowY, textButtonWidth("browser.up"), CONTROL_HEIGHT);
        upButton = widgets.add(new ButtonWidget(upButtonRect, EditorLang.t("browser.up"), this::goUp));
        upButton.enabled(canGoUp());

        UiRect closeButtonRect = new UiRect(Math.max(PADDING, width - PADDING - CLOSE_BUTTON_WIDTH), rowY,
                CLOSE_BUTTON_WIDTH, CONTROL_HEIGHT);
        widgets.add(new ButtonWidget(closeButtonRect, Component.literal(Icons.CLOSE), this::onClose)
                .tooltip(EditorLang.t("common.cancel")));

        // 面包屑占据面包屑行剩余整段
        int breadcrumbX = upButtonRect.right() + 4;
        int breadcrumbRight = closeButtonRect.x() - 4;
        breadcrumbRect = new UiRect(breadcrumbX, crumbRow.y() + 1,
                Math.max(0, breadcrumbRight - breadcrumbX), BAR_ROW_HEIGHT - 2);

        buildFooterLeft();
        buildBottomBar();
    }

    /// 底栏：右侧「取消 + 主按钮」，左侧子类登记的按钮，中间放名字输入框
    private void buildBottomBar() {
        UiRect bar = bottomBarRect();
        int y = bar.y() + (BOTTOM_BAR_HEIGHT - CONTROL_HEIGHT) / 2;
        int cursor = bar.right() - PADDING;

        int cancelWidth = textButtonWidth("common.cancel");
        UiRect cancelRect = new UiRect(Math.max(PADDING, cursor - cancelWidth), y, cancelWidth, CONTROL_HEIGHT);
        widgets.add(new ButtonWidget(cancelRect, EditorLang.t("common.cancel"), this::onClose));
        cursor = cancelRect.x() - BUTTON_GAP;

        int primaryWidth = textWidth(primaryLabel());
        UiRect primaryRect = new UiRect(Math.max(PADDING, cursor - primaryWidth), y, primaryWidth, CONTROL_HEIGHT);
        primaryButton = widgets.add(new ButtonWidget(primaryRect, primaryLabel(), this::primaryAction).accent(true));
        Component tooltip = primaryTooltip();

        if (tooltip != null) {
            primaryButton.tooltip(tooltip);
        }

        int rightEdge = primaryRect.x() - BUTTON_GAP;

        int left = PADDING;

        for (ButtonWidget button : footerButtons) {
            button.rect(new UiRect(left, y, button.rect().width(), CONTROL_HEIGHT));
            left += button.rect().width() + BUTTON_GAP;
        }

        if (!nameFieldPresent()) {
            return;
        }

        int labelWidth = Draw.font().width(nameLabel()) + 4;
        int fieldWidth = Mth.clamp(rightEdge - BUTTON_GAP - left - labelWidth, 0, NAME_FIELD_MAX_WIDTH);

        if (fieldWidth < NAME_FIELD_MIN_WIDTH) {
            // 空间不足：省略标签，把整段宽度留给输入框
            fieldWidth = Math.max(0, rightEdge - BUTTON_GAP - left);
        } else {
            nameLabelVisible = true;
        }

        if (fieldWidth <= 0) {
            return;
        }

        int fieldX = nameLabelVisible ? left + labelWidth : left;
        // 输入内容按需读取（提交后才生效），这里不需要额外响应
        nameField = widgets.add(new TextFieldWidget(new UiRect(fieldX, y, fieldWidth, CONTROL_HEIGHT), nameValue, value -> { }));
    }

    /// 每帧同步控件的可用状态与显示内容
    private void refreshWidgets() {
        if (upButton != null) {
            upButton.enabled(canGoUp());
        }

        if (primaryButton != null) {
            primaryButton.enabled(primaryEnabled());
        }

        if (nameField != null) {
            if (nameFieldEditable()) {
                nameField.visible(true);
                nameField.enabled(true);

                if (!nameField.editing()) {
                    // 失焦提交后把值留下来，重建控件时不会丢
                    nameValue = nameField.value();
                }
            } else {
                // 只读回显：跟着选中项走
                Row selected = selectedRow();
                nameField.visible(selected != null);
                nameField.enabled(false);
                nameField.value(selected == null ? "" : selected.name());
            }
        }

        refreshExtraWidgets();
    }

    /// 按钮宽度：随文字长度自适应，空出左右内边距
    private static int textWidth(Component label) {
        return Draw.font().width(label) + 10;
    }

    private static int textButtonWidth(String key) {
        return textWidth(EditorLang.t(key));
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

    /// 暂停世界：浏览数据期间世界不再变化
    @Override
    public boolean isPauseScreen() {
        return false;
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
        // 面包屑画在控件之上：展开的同级下拉要盖住列表与输入框。
        // 段内容每帧重来一遍，所以先清空上帧的段再让子类填
        breadcrumb.layout(breadcrumbRect, width, height);
        breadcrumb.clear();
        refreshCrumbs();
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

    /// 顶栏：标题行 + 面包屑行 + 可选第二行
    private void renderTopBar(GuiGraphicsExtractor graphics) {
        UiRect titleRow = titleRowRect();
        UiRect crumbRow = crumbRowRect();
        boolean hasExtra = extraRowHeight() > 0;
        UiRect extra = extraRowRect();

        Draw.canvas(graphics, titleRow, Draw.TOOLBAR_BG);
        Draw.canvas(graphics, crumbRow, Draw.TOOLBAR_BG);

        if (hasExtra) {
            Draw.canvas(graphics, extra, Draw.TOOLBAR_BG);
        }

        // 行分隔线统一画在各行底色之后，否则会被下一行的底色盖掉
        Draw.hLine(graphics, titleRow.x() + 4, titleRow.right() - 4, titleRow.bottom() - 1, Draw.GRID);
        Draw.hLine(graphics, crumbRow.x(), crumbRow.right(), crumbRow.bottom() - 1, Draw.BORDER);
        Draw.textCentered(graphics, screenTitle, titleRow.centerX(), titleRow.centerY() - 4, Draw.TEXT);

        if (!hasExtra) {
            return;
        }

        Draw.hLine(graphics, extra.x(), extra.right(), extra.bottom() - 1, Draw.BORDER);
        renderExtraRow(graphics, extra);
    }

    /// 中部：当前层级的条目列表
    private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect list = listRect();
        Draw.canvas(graphics, list, Draw.CANVAS_BG);
        Draw.border(graphics, list, Draw.BORDER);

        graphics.enableScissor(list.x() + 1, list.y() + 1, list.right() - 1, list.bottom() - 1);

        if (rows.isEmpty()) {
            Draw.textEllipsized(graphics, emptyText().getString(), list.x() + 5, list.y() + 4,
                    Math.max(8, list.width() - 10), Draw.TEXT_DISABLED);
        }

        int rowWidth = Math.max(1, list.width() - 8 - SCROLLBAR_WIDTH);

        for (int i = 0; i < rows.size(); i++) {
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

            Row entry = rows.get(i);
            // 容器加一个展开箭头前缀，条目用空格占位保持对齐
            String label = (entry.container() ? Icons.EXPAND : " ") + " " + entry.name();
            Draw.textEllipsized(graphics, label, row.x() + 3, row.y() + 3, row.width() - 6,
                    entry.container() ? Draw.ACCENT : Draw.TEXT);
        }

        graphics.disableScissor();
        renderScrollbar(graphics, list);
    }

    /// 右侧细滚动条：内容超出列表高度时才画
    private void renderScrollbar(GuiGraphicsExtractor graphics, UiRect list) {
        int totalHeight = rows.size() * ROW_HEIGHT + LIST_PADDING * 2;

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

    /// 底栏：底色与名字标签
    private void renderBottomBar(GuiGraphicsExtractor graphics) {
        UiRect rect = bottomBarRect();
        Draw.canvas(graphics, rect, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, rect.x(), rect.right(), rect.y(), Draw.BORDER);

        if (nameLabelVisible) {
            Draw.text(graphics, nameLabel(), rect.x() + PADDING, rect.centerY() - 4, Draw.TEXT_DIM);
        }
    }

    /// 提示行：优先显示一次性状态，其次是子类给的常驻提示
    private void renderHint(GuiGraphicsExtractor graphics) {
        UiRect rect = hintRect();

        if (rect.width() < STATUS_MIN_WIDTH || rect.height() <= 0) {
            return;
        }

        if (statusMessage != null) {
            Draw.textEllipsized(graphics, statusMessage, rect.x(), rect.y() + 1, rect.width(), Draw.ACCENT);
            return;
        }

        Component hint = hintText();

        if (hint != null) {
            Draw.textEllipsized(graphics, hint.getString(), rect.x(), rect.y() + 1, rect.width(), Draw.TEXT_DIM);
        }
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

        // 先提交正在编辑的输入框，保证按钮读到的是最新内容
        commitNameField();

        UiRect topBar = topBarRect();
        UiRect extra = extraRowRect();

        if (topBar.contains(event.x(), event.y()) || extra.contains(event.x(), event.y())
                || bottomBarRect().contains(event.x(), event.y())) {
            widgets.mouseClicked(event, doubleClick);
            return true;
        }

        if (listRect().contains(event.x(), event.y())) {
            clickRow(event, doubleClick);
        }

        return true;
    }

    /// 列表点击：算出命中的行，交给子类决定单击 / 双击各自做什么
    private void clickRow(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }

        UiRect list = listRect();
        int index = (int) ((event.y() - list.y() - LIST_PADDING + scrollY) / ROW_HEIGHT);

        if (index < 0 || index >= rows.size()) {
            // 点在列表空白处：取消选中
            selectedIndex = -1;
            rowClicked(-1, null, doubleClick);
            return;
        }

        selectedIndex = index;
        rowClicked(index, rows.get(index), doubleClick);
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

        // 面包屑的同级下拉在最上层，滚动先给它
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

        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;

        // 输入框优先：Esc / 回车由它先消化，避免误退出界面
        if (widgets.keyPressed(event)) {
            if (enter) {
                // 输入框刚提交完文本，直接走主操作（与地址栏回车跳转一致的手感）
                primaryAction();
            }

            return true;
        }

        if (enter) {
            primaryAction();
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
}
