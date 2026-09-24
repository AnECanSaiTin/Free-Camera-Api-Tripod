package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.WidgetHost;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// 编辑器面板基类：统一处理背景、标题栏、折叠与内容区裁剪，并转发输入事件。
///
/// 面板还负责持有自己的右键菜单：渲染由屏幕在最后统一调用（保证盖住其它面板），
/// 事件也由屏幕优先派发。
public abstract class EditorPanel {
    /// 标题栏高度
    public static final int HEADER_HEIGHT = 16;
    /// 标题栏右侧折叠按钮的尺寸
    private static final int COLLAPSE_BUTTON_SIZE = 11;
    /// 浮动窗口右下角缩放手柄的尺寸
    private static final int RESIZE_GRIP_SIZE = 9;
    /// 字体行高，用于把图标在按钮内垂直居中
    private static final int FONT_HEIGHT = 9;

    /// 面板标识，用于布局持久化与顺序恢复
    private final String id;
    private final Component title;
    private UiRect rect = new UiRect(0, 0, 0, 0);
    private UiRect lastLayoutRect = new UiRect(0, 0, -1, -1);
    /// 上次重建控件时的主题版本。面板的标签缓存会把配色固化进去，换主题必须重建一次
    private int lastThemeRevision = -1;
    private boolean collapsed;
    /// 是否已脱离停靠布局，成为独立窗口
    private boolean floating;
    /// 浮动窗口自身的矩形，停靠布局每帧据此推导面板矩形
    private UiRect floatingRect = new UiRect(0, 0, 0, 0);
    /// 折叠按钮已按下但尚未松开
    private boolean collapsePressed;
    private @Nullable ContextMenu menu;
    private int screenWidth;
    private int screenHeight;
    protected final WidgetHost widgets = new WidgetHost();

    protected EditorPanel(String id, Component title) {
        this.id = id;
        this.title = title;
    }

    public String id() {
        return id;
    }

    public Component title() {
        return title;
    }

    public UiRect rect() {
        return rect;
    }

    public void rect(UiRect rect) {
        this.rect = rect;
    }

    public WidgetHost widgets() {
        return widgets;
    }

    public boolean collapsed() {
        return collapsed;
    }

    public void collapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    /// 是否已成为浮动窗口（脱离停靠布局，绘制在所有停靠面板之上）
    public boolean floating() {
        return floating;
    }

    public void floating(boolean floating) {
        if (this.floating != floating) {
            this.floating = floating;
            invalidateLayout();
        }
    }

    /// 浮动窗口的矩形；停靠时无意义
    public UiRect floatingRect() {
        return floatingRect;
    }

    public void floatingRect(UiRect floatingRect) {
        this.floatingRect = floatingRect;
    }

    /// 浮动窗口右下角的缩放手柄区域，拖动它可以改变窗口尺寸
    public UiRect resizeGripRect() {
        int size = Math.min(RESIZE_GRIP_SIZE, Math.min(rect.width(), rect.height()));
        return new UiRect(rect.right() - size, rect.bottom() - size, size, size);
    }

    public void toggleCollapsed() {
        this.collapsed = !this.collapsed;
        invalidateLayout();
    }

    public UiRect headerRect() {
        return new UiRect(rect.x(), rect.y(), rect.width(), HEADER_HEIGHT);
    }

    /// 标题栏右侧的折叠按钮；只有点在这里才展开/收起
    public UiRect collapseButtonRect() {
        return new UiRect(rect.right() - COLLAPSE_BUTTON_SIZE - 3, rect.y() + (HEADER_HEIGHT - COLLAPSE_BUTTON_SIZE) / 2, COLLAPSE_BUTTON_SIZE, COLLAPSE_BUTTON_SIZE);
    }

    /// 内容区；折叠时高度为零
    public UiRect contentRect() {
        if (collapsed) {
            return new UiRect(rect.x(), rect.y() + HEADER_HEIGHT, rect.width(), 0);
        }

        int top = rect.y() + HEADER_HEIGHT;
        return new UiRect(rect.x(), top, rect.width(), Math.max(0, rect.bottom() - top));
    }

    /// 内容区尺寸变化时重建控件
    private void invalidateLayout() {
        lastLayoutRect = new UiRect(0, 0, -1, -1);
    }

    public final void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.screenWidth = graphics.guiWidth();
        this.screenHeight = graphics.guiHeight();

        if (panelBackgroundVisible()) {
            Draw.panel(graphics, rect);
        } else {
            Draw.border(graphics, rect, Draw.BORDER);
        }

        renderHeader(graphics, mouseX, mouseY);

        if (collapsed) {
            return;
        }

        UiRect content = contentRect();
        int themeRevision = Draw.themeRevision();

        // 内容区尺寸变化，或者主题换过一套配色，就重建控件与标签。
        // 标签在构建时把 Draw.TEXT 这类颜色取成了 int 存下来，不重建就一直是旧主题的颜色
        if (themeRevision != lastThemeRevision || !content.equals(lastLayoutRect)) {
            lastThemeRevision = themeRevision;
            widgets.clear();
            layoutWidgets(content);
            lastLayoutRect = content;
        }

        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        renderContent(graphics, content, mouseX, mouseY);
        widgets.render(graphics, mouseX, mouseY);
        graphics.disableScissor();

        if (floating) {
            renderFloatGrip(graphics);
        }
    }

    /// 浮动窗口右下角的缩放手柄：三条由短到长的斜线
    private void renderFloatGrip(GuiGraphicsExtractor graphics) {
        UiRect grip = resizeGripRect();

        for (int i = 1; i <= 3; i++) {
            Draw.hLine(graphics, grip.right() - i * 3, grip.right(), grip.bottom() - i * 3, Draw.TEXT_DIM);
        }
    }

    /// 面板底色与描边；子类可关掉它以便让内容自己铺满
    protected boolean panelBackgroundVisible() {
        return true;
    }

    protected void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect header = headerRect();
        Draw.canvas(graphics, header, Draw.PANEL_HEADER_BG);
        Draw.hLine(graphics, header.x(), header.right(), header.bottom() - 1, Draw.BORDER);
        Draw.textEllipsized(graphics, title.getString(), header.x() + 5, header.y() + 4, header.width() - COLLAPSE_BUTTON_SIZE - 12, Draw.TEXT_DIM);

        // 折叠按钮平时只显示箭头，鼠标悬停时才浮现按钮底
        UiRect button = collapseButtonRect();
        boolean hovered = button.contains(mouseX, mouseY);

        if (hovered) {
            Draw.canvas(graphics, button, Draw.BUTTON_BG_HOVER);
        }

        Draw.textCentered(graphics, collapsed ? Icons.EXPAND : Icons.COLLAPSE, button.centerX(),
                button.y() + (COLLAPSE_BUTTON_SIZE - FONT_HEIGHT) / 2, hovered ? Draw.TEXT : Draw.TEXT_DIM);
    }

    /// 首次布局或内容区尺寸变化时创建/重置控件
    protected void layoutWidgets(UiRect content) {
    }

    protected abstract void renderContent(GuiGraphicsExtractor graphics, UiRect content, int mouseX, int mouseY);

    // region 右键菜单

    public @Nullable ContextMenu contextMenu() {
        return menu;
    }

    public void closeMenu() {
        this.menu = null;
    }

    /// 在鼠标位置打开菜单；贴近屏幕边缘时菜单会朝反方向翻转
    public void openMenu(ContextMenu menu, double mouseX, double mouseY) {
        this.menu = menu.at(mouseX, mouseY, screenWidth, screenHeight);
    }

    /// 由屏幕在所有面板绘制完之后调用，保证菜单盖在其它面板之上
    public void renderMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menu != null) {
            menu.render(graphics, mouseX, mouseY);
        }
    }

    /// 菜单打开时优先消费点击；点在菜单之外同样关闭菜单。
    /// 点到二级菜单的父项时只是展开子菜单，菜单整体保持打开。
    public boolean menuMouseClicked(MouseButtonEvent event) {
        if (menu == null) {
            return false;
        }

        menu.mouseClicked(event);

        if (!menu.keepOpen()) {
            menu = null;
        }

        return true;
    }

    // endregion

    // region 事件

    public void mouseMoved(double mouseX, double mouseY) {
    }

    public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!rect.contains(event.x(), event.y())) {
            return false;
        }

        // 只有箭头按钮本身才触发展开/收起，标题栏其余位置留给拖拽重排；
        // 与其它按钮一致，动作等松开时再执行
        if (collapseButtonRect().contains(event.x(), event.y())) {
            collapsePressed = true;
            return true;
        }

        if (headerRect().contains(event.x(), event.y())) {
            return true;
        }

        if (collapsed) {
            return true;
        }

        if (widgets.mouseClicked(event, doubleClick)) {
            return true;
        }

        return contentMouseClicked(event, doubleClick);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        // 折叠按钮：按下与松开都落在箭头内才切换
        if (collapsePressed) {
            collapsePressed = false;

            if (collapseButtonRect().contains(event.x(), event.y())) {
                toggleCollapsed();
            }

            return true;
        }

        return widgets.mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (widgets.mouseDragged(event, deltaX, deltaY)) {
            return true;
        }

        return contentMouseDragged(event, deltaX, deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (widgets.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }

        return contentMouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean keyPressed(KeyEvent event) {
        if (widgets.keyPressed(event)) {
            return true;
        }

        return contentKeyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return widgets.charTyped(event);
    }

    protected boolean contentMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    protected boolean contentMouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return false;
    }

    protected boolean contentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    protected boolean contentKeyPressed(KeyEvent event) {
        return false;
    }

    // endregion
}
