package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// 自绘按钮，同时兼作开关按钮（{@link #toggled(boolean)}）。
///
/// 文字宽度超出按钮时会被裁剪到按钮内部并横向滚动，避免溢出到相邻控件上。
///
/// 动作在**松开**鼠标时执行：按下只记录状态，指针拖出按钮范围即取消，
/// 这样按下去想拖动面板之类的操作不会被误当成点击。
public class ButtonWidget extends EditorWidget {
    private static final int TEXT_MARGIN = 2;
    /// 未指定文字色时的取值：绘制时随主题取 {@link Draw#TEXT}，切主题后不用重建控件
    private static final int DEFAULT_TEXT_COLOR = 0;
    /// 滚动速度（像素/秒）
    private static final float SCROLL_SPEED = 16f;
    /// 滚动到两端时的停顿（秒）
    private static final float SCROLL_PAUSE = 0.9f;
    /// 超出宽度小于该值时不做滚动，直接居中裁剪，避免来回抖动
    private static final int MIN_SCROLL_RANGE = 5;

    private Component label;
    private final Runnable onPress;
    /// 开关状态：为 true 时按钮显示为按下（选中）样式
    private boolean toggled;
    /// 文字色；为 {@link #DEFAULT_TEXT_COLOR} 时随主题取 {@link Draw#TEXT}
    private int textColor = DEFAULT_TEXT_COLOR;
    /// 以强调色显示底边，用于区分主要动作
    private boolean accent;
    /// 悬停提示；按钮只放图标时用来补全含义
    private @Nullable Component tooltip;
    /// 已按下但尚未松开
    private boolean pressed;

    public ButtonWidget(UiRect rect, Component label, Runnable onPress) {
        super(rect);
        this.label = label;
        this.onPress = onPress;
    }

    public ButtonWidget textColor(int textColor) {
        this.textColor = textColor;
        return this;
    }

    public ButtonWidget accent(boolean accent) {
        this.accent = accent;
        return this;
    }

    public ButtonWidget tooltip(Component tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public Component label() {
        return label;
    }

    public void label(Component label) {
        this.label = label;
    }

    public boolean toggled() {
        return toggled;
    }

    public void toggled(boolean toggled) {
        this.toggled = toggled;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.button(graphics, rect(), hovered(), toggled);

        if (accent && !toggled) {
            Draw.hLine(graphics, rect().x() + 1, rect().right() - 1, rect().bottom() - 2, Draw.ACCENT);
        }

        renderLabel(graphics);

        if (tooltip != null && hovered()) {
            graphics.setTooltipForNextFrame(Draw.font(), tooltip, mouseX, mouseY);
        }
    }

    /// 文字超出按钮时裁剪到按钮内部并横向滚动（跑马灯）
    private void renderLabel(GuiGraphicsExtractor graphics) {
        int color = !enabled() ? Draw.TEXT_DISABLED : textColor == DEFAULT_TEXT_COLOR ? Draw.TEXT : textColor;
        String text = label.getString();
        int textWidth = Draw.font().width(text);
        int innerWidth = Math.max(1, rect().width() - TEXT_MARGIN * 2);
        int textY = rect().centerY() - 4;

        if (textWidth <= innerWidth) {
            Draw.text(graphics, text, rect().centerX() - textWidth / 2, textY, color);
            return;
        }

        int range = textWidth - innerWidth;
        int offset = range < MIN_SCROLL_RANGE ? range / 2 : (int) scrollOffset(range);

        graphics.enableScissor(rect().x() + 1, rect().y() + 1, rect().right() - 1, rect().bottom() - 1);
        Draw.text(graphics, text, rect().x() + TEXT_MARGIN - offset, textY, color);
        graphics.disableScissor();
    }

    /// 在 0 与 range 之间往返滚动，两端各停顿一段时间
    private float scrollOffset(int range) {
        float travel = range / SCROLL_SPEED;
        float period = travel + SCROLL_PAUSE * 2f;
        float time = (System.currentTimeMillis() % (long) (period * 1000f)) / 1000f;

        if (time < SCROLL_PAUSE) {
            return 0f;
        }

        if (time < SCROLL_PAUSE + travel) {
            return (time - SCROLL_PAUSE) * SCROLL_SPEED;
        }

        return range;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active()) {
            return false;
        }

        if (event.button() == 0) {
            // 只记录按下，动作等松开时再执行
            pressed = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!pressed) {
            return false;
        }

        pressed = false;

        // 按下与松开都落在按钮上才算一次点击；中途拖出范围视为放弃
        if (event.button() == 0 && isMouseOver(event.x(), event.y())) {
            onPress.run();
        }

        return true;
    }

    @Override
    public void updateHovered(int mouseX, int mouseY) {
        super.updateHovered(mouseX, mouseY);

        // 指针离开按钮即视为放弃这次点击，避免松开时在别处触发
        if (pressed && !hovered()) {
            pressed = false;
        }
    }
}
