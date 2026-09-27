package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/// 数值输入框：点击进入编辑，双击全选，右键清空，回车提交，Esc 取消，正在编辑时不受外部刷新影响。
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
    /// 全选态：下次输入字符或退格时先清空全文（本控件无光标与选区模型，用它代替「选中全部文本」）
    private boolean selectAll;

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
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.field(graphics, rect(), editing || hovered());

        String shown = editing ? text : Draw.num(value, decimals);

        // 全选态给文本铺一层高亮，提示下次输入会整体替换
        if (editing && selectAll && !text.isEmpty()) {
            int highlightWidth = Math.min(rect().width() - 6, Draw.font().width(text));
            graphics.fill(rect().x() + 3, rect().y() + 2, rect().x() + 3 + highlightWidth, rect().bottom() - 2, Draw.ROW_SELECTED);
        }

        Draw.textEllipsized(graphics, shown, rect().x() + 3, rect().centerY() - 4, rect().width() - 6, enabled() ? Draw.TEXT : Draw.TEXT_DISABLED);

        if (editing && !selectAll && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = Math.min(rect().right() - 3, rect().x() + 3 + Draw.font().width(text));
            Draw.vLine(graphics, caretX, rect().y() + 3, rect().bottom() - 3, Draw.ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active()) {
            return false;
        }

        // 右键：清空文本缓冲并进入编辑态，返回 true 让 WidgetHost 把焦点落在本控件上，方便立刻重新输入。
        // 这里只清空缓冲区、不提交；若清空后未输入就失焦，commit() 解析空串失败会保留原值。
        if (event.button() == 1) {
            editing = true;
            text = "";
            selectAll = false;
            return true;
        }

        if (event.button() != 0) {
            return false;
        }

        editing = true;
        text = Draw.num(value, decimals);
        // 双击全选：本控件没有光标与选区模型，故用「输入即替换」状态实现，效果等同全选后输入
        selectAll = doubleClick;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!editing) {
            return false;
        }

        char typed = (char) event.codepoint();

        if (Character.isDigit(typed) || typed == '.' || (typed == '-' && (selectAll || text.isEmpty()))) {
            if (selectAll) {
                // 全选态下首次输入直接替换全部内容
                text = "";
                selectAll = false;
            }

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
                selectAll = false;
                text = Draw.num(value, decimals);
            }
            case KEY_ENTER, KEY_KP_ENTER -> commit();
            case KEY_BACKSPACE -> {
                // 全选态下退格等同清空全文
                if (selectAll) {
                    text = "";
                    selectAll = false;
                } else if (!text.isEmpty()) {
                    text = text.substring(0, text.length() - 1);
                }
            }
            case KEY_DELETE -> {
                text = "";
                selectAll = false;
            }
            default -> {
                return false;
            }
        }

        return true;
    }

    private void commit() {
        editing = false;
        selectAll = false;

        try {
            value = Float.parseFloat(text);
        } catch (NumberFormatException ignored) {
            // 输入非法时保留原值
        }

        text = Draw.num(value, decimals);
        onChange.set(value);
    }
}
