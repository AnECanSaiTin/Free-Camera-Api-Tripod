package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/// 自绘右键菜单：图标 + 文本，可带勾选状态与分隔线，宽度按最长一行自适应。
///
/// 面板负责持有与分发，屏幕负责在最后统一绘制，保证菜单盖在其它面板之上。
public final class ContextMenu {
    private static final int ROW_HEIGHT = 13;
    private static final int SEPARATOR_HEIGHT = 6;
    private static final int ICON_WIDTH = 11;
    private static final int PADDING = 4;
    private static final int MIN_WIDTH = 88;

    /// 一条菜单项；{@code label} 为 null 表示分隔线
    private record Item(@Nullable String icon, @Nullable Component label,
                        @Nullable BooleanSupplier toggled, @Nullable Runnable action) {
    }

    private final List<Item> items = new ArrayList<>();
    private int x;
    private int y;
    private int width;
    private int height;

    public ContextMenu item(String icon, Component label, Runnable action) {
        items.add(new Item(icon, label, null, action));
        return this;
    }

    /// 带勾选状态的开关项
    public ContextMenu toggle(String icon, Component label, BooleanSupplier state, Runnable action) {
        items.add(new Item(icon, label, state, action));
        return this;
    }

    public ContextMenu separator() {
        items.add(new Item(null, null, null, null));
        return this;
    }

    /// 以 (mouseX, mouseY) 为锚点定位；贴到屏幕边缘时向反方向翻转
    public ContextMenu at(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        width = MIN_WIDTH;

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
        Draw.canvas(graphics, rect(), 0xF51B1B21);
        Draw.border(graphics, rect(), Draw.BORDER);
        int rowY = y + PADDING;

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
            rowY += ROW_HEIGHT;
        }
    }

    /// 命中某一项则执行并返回 true；点在菜单空白处也返回 true（消费掉，避免穿透）
    public boolean mouseClicked(MouseButtonEvent event) {
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
