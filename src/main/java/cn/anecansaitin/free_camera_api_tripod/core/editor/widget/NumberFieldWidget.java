package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/// 数值输入框：点击进入编辑，回车提交，Esc 取消，正在编辑时不受外部刷新影响。
public class NumberFieldWidget extends EditorWidget {
    @FunctionalInterface
    public interface FloatSetter {
        void set(float value);
    }

    private static final int KEY_ESCAPE = 256;
    private static final int KEY_ENTER = 257;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_KP_ENTER = 335;

    private final FloatSetter onChange;
    private float value;
    private String text;
    private int decimals = 3;
    private boolean editing;

    public NumberFieldWidget(UiRect rect, float value, FloatSetter onChange) {
        super(rect);
        this.onChange = onChange;
        this.value = value;
        this.text = Draw.num(value, decimals);
    }

    public NumberFieldWidget decimals(int decimals) {
        this.decimals = decimals;
        this.text = Draw.num(value, decimals);
        return this;
    }

    public float value() {
        return value;
    }

    /// 外部同步数值；编辑中不覆盖用户输入
    public void value(float value) {
        this.value = value;

        if (!editing) {
            this.text = Draw.num(value, decimals);
        }
    }

    public boolean editing() {
        return editing;
    }

    @Override
    public void focused(boolean focused) {
        if (!focused && editing) {
            commit();
        }

        super.focused(focused);
    }

    @Override
    public void updateHovered(int mouseX, int mouseY) {
        // 编辑中保持高亮
        if (!editing) {
            super.updateHovered(mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.field(graphics, rect(), editing || hovered());

        String shown = editing ? text : Draw.num(value, decimals);
        Draw.textEllipsized(graphics, shown, rect().x() + 3, rect().centerY() - 4, rect().width() - 6, enabled() ? Draw.TEXT : Draw.TEXT_DISABLED);

        if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = Math.min(rect().right() - 3, rect().x() + 3 + Draw.font().width(text));
            Draw.vLine(graphics, caretX, rect().y() + 3, rect().bottom() - 3, Draw.ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active() || event.button() != 0) {
            return false;
        }

        editing = true;
        text = Draw.num(value, decimals);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!editing) {
            return false;
        }

        char typed = (char) event.codepoint();

        if (Character.isDigit(typed) || typed == '.' || (typed == '-' && text.isEmpty())) {
            text += typed;
        }

        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!editing) {
            return false;
        }

        switch (event.key()) {
            case KEY_ESCAPE -> {
                editing = false;
                text = Draw.num(value, decimals);
            }
            case KEY_ENTER, KEY_KP_ENTER -> commit();
            case KEY_BACKSPACE -> {
                if (!text.isEmpty()) {
                    text = text.substring(0, text.length() - 1);
                }
            }
            case KEY_DELETE -> text = "";
            default -> {
                return false;
            }
        }

        return true;
    }

    private void commit() {
        editing = false;

        try {
            value = Float.parseFloat(text);
        } catch (NumberFormatException ignored) {
            // 输入非法时保留原值
        }

        text = Draw.num(value, decimals);
        onChange.set(value);
    }
}
