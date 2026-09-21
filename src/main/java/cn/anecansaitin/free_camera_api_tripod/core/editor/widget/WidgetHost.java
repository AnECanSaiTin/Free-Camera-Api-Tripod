package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 控件容器：负责焦点管理与事件分发（后加入的控件优先接收事件）。
public final class WidgetHost {
    private final List<EditorWidget> widgets = new ArrayList<>();
    private @Nullable EditorWidget focused;

    public <T extends EditorWidget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    public void clear() {
        widgets.clear();
        focused = null;
    }

    public List<EditorWidget> widgets() {
        return widgets;
    }

    public @Nullable EditorWidget focused() {
        return focused;
    }

    public void focus(@Nullable EditorWidget widget) {
        if (focused == widget) {
            return;
        }

        if (focused != null) {
            focused.focused(false);
        }

        focused = widget;

        if (focused != null) {
            focused.focused(true);
        }
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (EditorWidget widget : widgets) {
            if (widget.visible()) {
                widget.updateHovered(mouseX, mouseY);
                widget.render(graphics, mouseX, mouseY);
            }
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            EditorWidget widget = widgets.get(i);

            if (widget.isMouseOver(event.x(), event.y()) && widget.mouseClicked(event, doubleClick)) {
                focus(widget);
                return true;
            }
        }

        focus(null);
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (focused != null && focused.mouseReleased(event)) {
            return true;
        }

        boolean handled = false;

        for (EditorWidget widget : widgets) {
            handled |= widget.mouseReleased(event);
        }

        return handled;
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (focused != null && focused.mouseDragged(event, deltaX, deltaY)) {
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            EditorWidget widget = widgets.get(i);

            if (widget.isMouseOver(mouseX, mouseY) && widget.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }

        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return focused != null && focused.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return focused != null && focused.charTyped(event);
    }
}
