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

/// 文本输入框：点击进入编辑（并落定光标位置），双击全选，右键清空，回车提交，Esc 取消；
/// 编辑中左右键移动光标、Home / End 跳首尾，正在编辑时不受外部刷新影响。
///
/// 与 {@link NumberFieldWidget} 的区别在于不限定字符集，供动画名称这类自由文本使用。
public class TextFieldWidget extends EditorWidget {
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_ENTER = 257;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;
    private static final int KEY_KP_ENTER = 335;
    private static final int KEY_V = 86;
    /// 文本长度上限的默认值，避免过长的名称挤满标题栏等展示位置
    private static final int MAX_LENGTH = 48;

    private final Consumer<String> onChange;
    private String value;
    private String text;
    private boolean editing;
    /// 光标在文本里的下标：输入、退格、删除与左右键都在它那里发生
    private int caret;
    /// 本控件的文本长度上限，默认 {@link #MAX_LENGTH}；地址栏这类需要长文本的地方可以调大
    private int maxLength = MAX_LENGTH;
    /// 覆盖文字颜色的 ARGB；为 0 表示按可用状态取默认色
    private int textColor;
    /// 全选态：下次输入字符或退格时先清空全文（本控件没有选区模型，用它代替「选中全部文本」）
    private boolean selectAll;

    public TextFieldWidget(UiRect rect, String value, Consumer<String> onChange) {
        super(rect);
        this.onChange = onChange;
        this.value = value;
        this.text = value;
        this.caret = value.length();
    }

    public String value() {
        return value;
    }

    /// 外部同步文本；编辑中不覆盖用户输入
    public void value(String value) {
        this.value = value;

        if (!editing) {
            this.text = value;
            this.caret = value.length();
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

    /// 覆盖文字颜色（ARGB）；传 0 恢复默认（可用取 {@link Draw#TEXT}、禁用取 {@link Draw#TEXT_DISABLED}）。
    /// 函数面板用它把编译不过的函数体标红
    public TextFieldWidget textColor(int argb) {
        this.textColor = argb;
        return this;
    }

    /// 直接进入编辑态并全选：给「双击就地重命名」这类入口用，省掉再点一次输入框
    public void edit() {
        editing = true;
        selectAll = true;
        text = value;
        caret = 0;
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

        String shown = editing ? text : value;

        // 全选态给文本铺一层高亮，提示下次输入会整体替换
        if (editing && selectAll && !text.isEmpty()) {
            int highlightWidth = Math.min(rect().width() - 6, Draw.font().width(text));
            graphics.fill(rect().x() + 3, rect().y() + 2, rect().x() + 3 + highlightWidth, rect().bottom() - 2, Draw.ROW_SELECTED);
        }

        int color = textColor != 0 ? textColor : (enabled() ? Draw.TEXT : Draw.TEXT_DISABLED);
        Draw.textEllipsized(graphics, shown, rect().x() + 3, rect().centerY() - 4, rect().width() - 6, color);

        if (editing && !selectAll && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = Math.min(rect().right() - 3, rect().x() + 3 + Draw.font().width(text.substring(0, clampCaret())));
            Draw.vLine(graphics, caretX, rect().y() + 3, rect().bottom() - 3, Draw.ACCENT);
        }

        renderTooltip(graphics, mouseX, mouseY);
    }

    /// 光标下标夹在文本长度内：外部改过文本、或长度上限截断之后仍然安全
    private int clampCaret() {
        return Math.max(0, Math.min(caret, text.length()));
    }

    /// 鼠标横坐标落在哪个字符边界上：逐字符累加宽度，取离鼠标最近的那条边界
    private int caretAt(double mouseX) {
        int offset = (int) (mouseX - rect().x() - 3);
        int index = 0;
        int width = 0;

        while (index < text.length()) {
            int charWidth = Draw.font().width(text.substring(index, index + 1));

            if (width + charWidth / 2 >= offset) {
                break;
            }

            width += charWidth;
            index++;
        }

        return index;
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
            caret = 0;
            return true;
        }

        if (event.button() != 0) {
            return false;
        }

        editing = true;
        text = value;
        // 双击全选：本控件没有选区模型，故用「输入即替换」状态实现，效果等同全选后输入
        selectAll = doubleClick;
        // 单击按落点定光标，双击是全选态所以回到开头
        caret = doubleClick ? 0 : caretAt(event.x());
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

    /// 在光标处插入一段文本（逐字输入与 Ctrl+V 粘贴共用），超出长度上限的部分直接丢掉
    private void insert(String addition) {
        if (addition.isEmpty()) {
            return;
        }

        if (selectAll) {
            // 全选态下首次输入直接替换全部内容
            text = "";
            caret = 0;
            selectAll = false;
        }

        int room = maxLength - text.length();

        if (room <= 0) {
            return;
        }

        int at = clampCaret();
        String clipped = addition.length() <= room ? addition : addition.substring(0, room);
        text = text.substring(0, at) + clipped + text.substring(at);
        caret = at + clipped.length();
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
                caret = text.length();
            }
            case KEY_ENTER, KEY_KP_ENTER -> commit();
            case KEY_LEFT -> {
                // 全选态下按左右键＝先取消全选：左键落到开头、右键落到末尾
                caret = selectAll ? 0 : Math.max(0, clampCaret() - 1);
                selectAll = false;
            }
            case KEY_RIGHT -> {
                caret = selectAll ? text.length() : Math.min(text.length(), clampCaret() + 1);
                selectAll = false;
            }
            case KEY_HOME -> {
                caret = 0;
                selectAll = false;
            }
            case KEY_END -> {
                caret = text.length();
                selectAll = false;
            }
            case KEY_BACKSPACE -> {
                if (selectAll) {
                    // 全选态下退格等同清空全文
                    text = "";
                    caret = 0;
                    selectAll = false;
                } else {
                    int at = clampCaret();

                    if (at > 0) {
                        text = text.substring(0, at - 1) + text.substring(at);
                        caret = at - 1;
                    }
                }
            }
            case KEY_DELETE -> {
                if (selectAll) {
                    text = "";
                    caret = 0;
                    selectAll = false;
                } else {
                    int at = clampCaret();

                    if (at < text.length()) {
                        text = text.substring(0, at) + text.substring(at + 1);
                    }
                }
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
        caret = text.length();
        onChange.accept(value);
    }

    /// 左右 Ctrl 是否有一个按下（粘贴用）
    private static boolean ctrlDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
