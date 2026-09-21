package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
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

    /// 面板标识，用于布局持久化与顺序恢复
    private final String id;
    private final Component title;
    private UiRect rect = new UiRect(0, 0, 0, 0);
    private UiRect lastLayoutRect = new UiRect(0, 0, -1, -1);
    private boolean collapsed;
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

    public void toggleCollapsed() {
        this.collapsed = !this.collapsed;
        invalidateLayout();
    }

    public UiRect headerRect() {
        return new UiRect(rect.x(), rect.y(), rect.width(), HEADER_HEIGHT);
    }

    public UiRect contentRect() {
        if (collapsed) {
            return new UiRect(rect.x(), rect.y() + HEADER_HEIGHT, rect.width(), 0);
        }

        return rect.inset(0, HEADER_HEIGHT, 0, 0);
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

        if (!content.equals(lastLayoutRect)) {
            widgets.clear();
            layoutWidgets(content);
            lastLayoutRect = content;
        }

        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        renderContent(graphics, content, mouseX, mouseY);
        widgets.render(graphics, mouseX, mouseY);
        graphics.disableScissor();
    }

    /// 面板底色与描边；子类可关掉它以便让内容自己铺满
    protected boolean panelBackgroundVisible() {
        return true;
    }

    protected void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect header = headerRect();
        Draw.canvas(graphics, header, Draw.PANEL_HEADER_BG);
        Draw.hLine(graphics, header.x(), header.right(), header.bottom() - 1, Draw.BORDER);
        Draw.text(graphics, title, header.x() + 5, header.y() + 4, Draw.TEXT_DIM);
        Draw.text(graphics, collapsed ? "▸" : "▾", header.right() - 10, header.y() + 4, Draw.TEXT_DIM);
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
    protected void openMenu(ContextMenu menu, double mouseX, double mouseY) {
        this.menu = menu.at(mouseX, mouseY, screenWidth, screenHeight);
    }

    /// 由屏幕在所有面板绘制完之后调用，保证菜单盖在其它面板之上
    public void renderMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menu != null) {
            menu.render(graphics, mouseX, mouseY);
        }
    }

    /// 菜单打开时优先消费点击；点在菜单之外同样关闭菜单
    public boolean menuMouseClicked(MouseButtonEvent event) {
        if (menu == null) {
            return false;
        }

        menu.mouseClicked(event);
        menu = null;
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

        if (headerRect().contains(event.x(), event.y())) {
            if (doubleClick) {
                toggleCollapsed();
            }

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
