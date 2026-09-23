package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/// 自绘右键菜单：图标 + 文本，可带勾选状态、分隔线与二级菜单，宽度按最长一行自适应。
///
/// 面板负责持有与分发，屏幕负责在最后统一绘制，保证菜单盖在其它面板之上。
public final class ContextMenu {
    private static final int ROW_HEIGHT = 13;
    private static final int SEPARATOR_HEIGHT = 6;
    private static final int ICON_WIDTH = 11;
    private static final int PADDING = 4;
    private static final int MIN_WIDTH = 88;
    /// 二级菜单相对父菜单的横向偏移：稍微叠一点，视觉上连成一体
    private static final int SUBMENU_OFFSET = 2;

    /// 一条菜单项；{@code label} 为 null 表示分隔线，{@code submenu} 非 null 表示点开后是二级菜单
    private record Item(@Nullable String icon, @Nullable Component label,
                        @Nullable BooleanSupplier toggled, @Nullable Runnable action,
                        @Nullable ContextMenu submenu) {
    }

    private final List<Item> items = new ArrayList<>();
    private int x;
    private int y;
    private int width;
    private int height;
    private int screenWidth;
    private int screenHeight;
    /// 当前悬停展开的二级菜单；鼠标移开后清空
    private @Nullable ContextMenu openSubmenu;
    /// 上一次点击是否只是展开了二级菜单（此时菜单整体要保持打开）
    private boolean keepOpen;

    public ContextMenu item(String icon, Component label, Runnable action) {
        items.add(new Item(icon, label, null, action, null));
        return this;
    }

    /// 带勾选状态的开关项
    public ContextMenu toggle(String icon, Component label, BooleanSupplier state, Runnable action) {
        items.add(new Item(icon, label, state, action, null));
        return this;
    }

    /// 二级菜单项：鼠标悬停在整行上时在右侧展开，行尾显示一个小三角
    public ContextMenu submenu(String icon, Component label, ContextMenu submenu) {
        items.add(new Item(icon, label, null, null, submenu));
        return this;
    }

    public ContextMenu separator() {
        items.add(new Item(null, null, null, null, null));
        return this;
    }

    /// 以 (mouseX, mouseY) 为锚点定位；贴到屏幕边缘时向反方向翻转
    public ContextMenu at(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        width = MIN_WIDTH;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        for (Item item : items) {
            if (item.label() != null) {
                width = Math.max(width, Draw.font().width(item.label().getString()) + ICON_WIDTH + PADDING * 2 + 6);
            }
        }

        height = PADDING * 2;

        for (Item item : items) {
            height += item.label() == null ? SEPARATOR_HEIGHT : ROW_HEIGHT;
        }

        x = (int) mouseX;
        y = (int) mouseY;

        if (x + width > screenWidth) {
            x = Math.max(0, screenWidth - width);
        }

        if (y + height > screenHeight) {
            y = Math.max(0, y - height);
        }

        return this;
    }

    public UiRect rect() {
        return new UiRect(x, y, width, height);
    }

    public boolean contains(double mouseX, double mouseY) {
        return rect().contains(mouseX, mouseY);
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.canvas(graphics, rect(), Draw.MENU_BG);
        Draw.border(graphics, rect(), Draw.BORDER);
        int rowY = y + PADDING;
        // 悬停到哪一行带二级菜单，就在这一行右侧展开它
        ContextMenu hoveredSubmenu = null;
        int hoveredSubmenuY = 0;

        for (Item item : items) {
            if (item.label() == null) {
                Draw.hLine(graphics, x + PADDING, x + width - PADDING, rowY + SEPARATOR_HEIGHT / 2, Draw.BORDER);
                rowY += SEPARATOR_HEIGHT;
                continue;
            }

            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean checked = item.toggled() != null && item.toggled().getAsBoolean();

            if (hovered) {
                Draw.canvas(graphics, new UiRect(x + 1, rowY, width - 2, ROW_HEIGHT), Draw.BUTTON_BG_HOVER);
            }

            int textY = rowY + (ROW_HEIGHT - 8) / 2;
            Draw.text(graphics, item.icon(), x + PADDING, textY, checked ? Draw.ACCENT : Draw.TEXT_DISABLED);
            Draw.text(graphics, item.label().getString(), x + PADDING + ICON_WIDTH, textY, checked ? Draw.ACCENT : Draw.TEXT);

            if (item.submenu() != null) {
                Draw.text(graphics, Icons.EXPAND, x + width - PADDING - 4, textY,
                        hovered ? Draw.TEXT : Draw.TEXT_DIM);

                if (hovered) {
                    hoveredSubmenu = item.submenu();
                    hoveredSubmenuY = rowY;
                }
            }

            rowY += ROW_HEIGHT;
        }

        if (hoveredSubmenu != null) {
            // 展开时记住位置，之后鼠标移进二级菜单也靠这个矩形判断「还在菜单里」
            openSubmenu = hoveredSubmenu
                    .at(x + width - SUBMENU_OFFSET, hoveredSubmenuY - PADDING, screenWidth, screenHeight);
        } else if (openSubmenu != null && !openSubmenu.containsSubmenu(mouseX, mouseY)) {
            // 鼠标既不在父菜单上、也不在已展开的二级菜单里才收起；
            // 否则鼠标从父项移向二级菜单的路上菜单就消失了
            openSubmenu = null;
        }

        if (openSubmenu != null) {
            openSubmenu.render(graphics, mouseX, mouseY);
        }
    }

    /// 鼠标是否落在本菜单或它展开的二级菜单里
    private boolean containsSubmenu(double mouseX, double mouseY) {
        if (contains(mouseX, mouseY)) {
            return true;
        }

        return openSubmenu != null && openSubmenu.containsSubmenu(mouseX, mouseY);
    }

    /// 上一次点击是否只是展开了二级菜单：调用方据此决定要不要把整个菜单关掉
    public boolean keepOpen() {
        return keepOpen;
    }

    /// 命中某一项则执行并返回 true；点在菜单空白处也返回 true（消费掉，避免穿透）。
    /// 二级菜单展开时优先交给它处理。
    public boolean mouseClicked(MouseButtonEvent event) {
        keepOpen = false;

        if (openSubmenu != null && openSubmenu.mouseClicked(event)) {
            return true;
        }

        if (!contains(event.x(), event.y())) {
            return false;
        }

        int rowY = y + PADDING;

        for (Item item : items) {
            if (item.label() == null) {
                rowY += SEPARATOR_HEIGHT;
                continue;
            }

            if (event.y() >= rowY && event.y() < rowY + ROW_HEIGHT) {
                // 二级菜单的父项本身不执行动作，点一下等同悬停展开，菜单整体保持打开
                if (item.submenu() != null) {
                    openSubmenu = item.submenu();
                    keepOpen = true;
                    return true;
                }

                if (item.action() != null) {
                    item.action().run();
                }

                return true;
            }

            rowY += ROW_HEIGHT;
        }

        return true;
    }
}
