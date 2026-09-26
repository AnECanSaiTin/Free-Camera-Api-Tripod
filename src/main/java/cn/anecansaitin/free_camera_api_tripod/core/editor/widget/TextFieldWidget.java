package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/// 文本输入框：点击进入编辑，双击全选，右键清空，回车提交，Esc 取消，正在编辑时不受外部刷新影响。
///
/// 与 {@link NumberFieldWidget} 的区别在于不限定字符集，供动画名称这类自由文本使用。
public class TextFieldWidget extends EditorWidget {
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_ENTER = 257;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_KP_ENTER = 335;
    private static final int KEY_V = 86;
    /// 文本长度上限的默认值，避免过长的名称挤满标题栏等展示位置
    private static final int MAX_LENGTH = 48;

    private final Consumer<String> onChange;
    private String value;
    private String text;
    private boolean editing;
    /// 本控件的文本长度上限，默认 {@link #MAX_LENGTH}；地址栏这类需要长文本的地方可以调大
    private int maxLength = MAX_LENGTH;
    /// 全选态：下次输入字符或退格时先清空全文（本控件无光标与选区模型，用它代替「选中全部文本」）
    private boolean selectAll;

    public TextFieldWidget(UiRect rect, String value, Consumer<String> onChange) {
        super(rect);
        this.onChange = onChange;
        this.value = value;
        this.text = value;
    }

    public String value() {
        return value;
    }

    /// 外部同步文本；编辑中不覆盖用户输入
    public void value(String value) {
        this.value = value;

        if (!editing) {
            this.text = value;
        }
    }

    public boolean editing() {
        return editing;
    }

    /// 设置文本长度上限；地址栏这类要填绝对路径的地方需要放宽
    public TextFieldWidget maxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        return this;
    }

    /// 直接进入编辑态并全选：给「双击就地重命名」这类入口用，省掉再点一次输入框
    public void edit() {
        editing = true;
        selectAll = true;
        text = value;
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

        String shown = editing ? text : value;

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

        // 右键：清空文本缓冲并进入编辑态，返回 true 让 WidgetHost 把焦点落在本控件上，方便立刻重新输入
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
        text = value;
        // 双击全选：本控件没有光标与选区模型，故用「输入即替换」状态实现，效果等同全选后输入
        selectAll = doubleClick;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!editing) {
            return false;
        }

        int codepoint = event.codepoint();

        // 控制字符（回车、换行等）不进文本；其余按完整码点追加，中文与 BMP 之外的字符都算一个字符
        if (Character.isISOControl(codepoint)) {
            return true;
        }

        insert(new String(Character.toChars(codepoint)));
        return true;
    }

    /// 追加一段文本（逐字输入与 Ctrl+V 粘贴共用），超出长度上限的部分直接丢掉
    private void insert(String addition) {
        if (addition.isEmpty()) {
            return;
        }

        if (selectAll) {
            // 全选态下首次输入直接替换全部内容
            text = "";
            selectAll = false;
        }

        int room = maxLength - text.length();

        if (room <= 0) {
            return;
        }

        text += addition.length() <= room ? addition : addition.substring(0, room);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!editing) {
            return false;
        }

        // Ctrl+V：中文这类不好直接敲的文本可以直接贴进来
        if (event.key() == KEY_V && ctrlDown()) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard().strip());
            return true;
        }

        switch (event.key()) {
            case KEY_ESCAPE -> {
                editing = false;
                selectAll = false;
                text = value;
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

        // 空名称没有意义，输入为空时保留原值
        if (!text.isBlank()) {
            value = text;
        }

        text = value;
        onChange.accept(value);
    }

    /// 左右 Ctrl 是否有一个按下（粘贴用）
    private static boolean ctrlDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
