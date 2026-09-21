package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/// 编辑器自绘控件基类。
///
/// 编辑器使用自绘控件而非原版 Widget，以获得统一的深色外观并避免与
/// Screen 的焦点/布局机制相互干扰。
public abstract class EditorWidget {
    private UiRect rect;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean hovered;
    private boolean focused;

    protected EditorWidget(UiRect rect) {
        this.rect = rect;
    }

    public UiRect rect() {
        return rect;
    }

    public void rect(UiRect rect) {
        this.rect = rect;
    }

    public boolean visible() {
        return visible;
    }

    public void visible(boolean visible) {
        this.visible = visible;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean hovered() {
        return hovered;
    }

    public boolean focused() {
        return focused;
    }

    public void focused(boolean focused) {
        this.focused = focused;
    }

    public boolean active() {
        return visible && enabled;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return active() && rect.contains(mouseX, mouseY);
    }

    public void updateHovered(int mouseX, int mouseY) {
        this.hovered = isMouseOver(mouseX, mouseY);
    }

    public abstract void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }
}
