package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

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
    /// 悬停提示；为空表示不提示
    private @Nullable Component tooltip;

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

    /// 设置悬停提示；子类在自己的 render 末尾调 {@link #renderTooltip} 把它显示出来
    public EditorWidget tooltip(@Nullable Component tooltip) {
        this.tooltip = tooltip;
        return this;
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

    /// 鼠标停在控件上时显示提示。
    ///
    /// 判断只看指针是否落在矩形内，不用 {@link #hovered()}：那是给渲染用的悬停态，
    /// 子类可能按自己的需要放宽（例如输入框编辑中冻结悬停态），拿它判提示会让提示跟着鼠标到处跑
    protected void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (tooltip != null && rect.contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Draw.font(), tooltip, mouseX, mouseY);
        }
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
