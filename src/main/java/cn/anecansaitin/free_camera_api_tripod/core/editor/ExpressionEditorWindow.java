package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ExpressionContext;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlayer;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.WidgetHost;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// 表达式编辑窗口：左侧一大块公式输入区，右上变量表，右下函数表。
///
/// 与 {@link cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ConfirmDialog} 一样是**模态**的：
/// 屏幕在最上层绘制它并优先派发输入，直到 {@link #finished()} 为真才关掉。
///
/// 变量表里的 `+` / `−` 直接增删动画的变量（与变量面板操作同一份数据），点变量名把它插到光标处；
/// 函数表同理，插入模板并把光标停进括号里。输入区下方实时给出当前公式的求值结果，
/// 公式非法时转成警告色并说明原因——但**不会**阻止保存，求值失败时播放链会回退到固定数值。
public final class ExpressionEditorWindow {
    private static final int PADDING = 8;
    /// 窗口内第一行：窗口标题 + 实时求值结果
    private static final int TITLE_HEIGHT = 12;
    /// 第二行：「属性：xxx」，说明正在编辑的是哪个数值的公式
    private static final int SUBTITLE_HEIGHT = 11;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_PADDING = 3;
    private static final int GAP = 6;
    private static final int BUTTON_WIDTH = 62;
    private static final int BUTTON_HEIGHT = 15;
    private static final int SMALL_BUTTON_SIZE = 11;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int MIN_WIDTH = 380;
    private static final int MIN_HEIGHT = 220;
    private static final int MAX_WIDTH = 470;
    private static final int MAX_HEIGHT = 270;
    /// 公式长度上限，避免一行行堆到看不见头
    private static final int MAX_TEXT_LENGTH = 512;
    /// 变量表占右栏的比例，其余留给函数表
    private static final float VARIABLE_SECTION_RATIO = 0.58f;

    private static final int KEY_ESCAPE = 256;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_LEFT = 263;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;
    private static final int KEY_V = 86;

    /// 函数表的一项：显示文本 + 点一下插进公式的模板
    private record FunctionItem(String label, String template) {
    }

    /// 与 {@link Expression} 支持的内置函数保持一致
    private static final List<FunctionItem> FUNCTIONS = List.of(
            new FunctionItem("min(a, b)", "min()"),
            new FunctionItem("max(a, b)", "max()"),
            new FunctionItem("random()", "random()"),
            new FunctionItem("random(a, b)", "random()"),
            new FunctionItem("sin(x)", "sin()"),
            new FunctionItem("cos(x)", "cos()"));

    private final CameraAnimation animation;
    private final CameraPlayer player;
    /// 正在编辑的字段名（例如「取值」「位置 X」），显示在标题右侧，用来区分开的是哪个数值的公式
    private final Component fieldLabel;
    private final Consumer<String> onConfirm;

    private final WidgetHost widgets = new WidgetHost();
    private String text;
    private int caret;
    private int textScroll;
    private int variableScroll;
    private int functionScroll;
    /// 编辑动作后是否把光标所在行滚进可视范围；手动滚动输入区时关掉，免得马上又被拽回来
    private boolean followCaret = true;
    /// 变量表里被点中的那一个，`−` 按钮删的就是它
    private @Nullable Variable selected;
    private boolean finished;

    private UiRect rect = new UiRect(0, 0, 0, 0);
    private UiRect textArea = new UiRect(0, 0, 0, 0);
    private UiRect variableList = new UiRect(0, 0, 0, 0);
    private UiRect functionList = new UiRect(0, 0, 0, 0);
    private int screenWidth;
    private int screenHeight;
    /// 变量区的 `+` / `−` 与底部的「确定 / 取消」。只建一次，之后每次布局只挪位置：
    /// 按钮的「按下」状态跨帧存在，重建会把「按下 → 松开」的一次点击直接丢掉
    private final ButtonWidget addButton;
    private final ButtonWidget removeButton;
    private final ButtonWidget cancelButton;
    private final ButtonWidget confirmButton;

    public ExpressionEditorWindow(CameraAnimation animation, CameraPlayer player, Component fieldLabel,
                                  @Nullable String expression, Consumer<String> onConfirm) {
        this.animation = animation;
        this.player = player;
        this.fieldLabel = fieldLabel;
        this.onConfirm = onConfirm;
        this.text = expression == null ? "" : expression;
        this.caret = text.length();
        this.addButton = new ButtonWidget(new UiRect(0, 0, SMALL_BUTTON_SIZE, SMALL_BUTTON_SIZE),
                Component.literal(Icons.ADD), this::addVariable).tooltip(EditorLang.t("expression.variable.add"));
        this.removeButton = new ButtonWidget(new UiRect(0, 0, SMALL_BUTTON_SIZE, SMALL_BUTTON_SIZE),
                Component.literal(Icons.REMOVE), this::removeVariable).tooltip(EditorLang.t("expression.variable.remove"));
        this.cancelButton = new ButtonWidget(new UiRect(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT),
                EditorLang.t("common.cancel"), () -> finished = true);
        this.confirmButton = new ButtonWidget(new UiRect(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT),
                EditorLang.t("common.confirm"), this::confirm).accent(true);
        widgets.add(addButton);
        widgets.add(removeButton);
        widgets.add(cancelButton);
        widgets.add(confirmButton);
    }

    /// 按屏幕尺寸重新居中并切分内部区域
    public void update(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        int width = Mth.clamp(screenWidth - PADDING * 4, MIN_WIDTH, Math.max(MIN_WIDTH, MAX_WIDTH));
        width = Math.min(width, Math.max(1, screenWidth - PADDING * 2));
        int height = Mth.clamp(screenHeight - PADDING * 4, MIN_HEIGHT, Math.max(MIN_HEIGHT, MAX_HEIGHT));
        height = Math.min(height, Math.max(1, screenHeight - PADDING * 2));
        rect = new UiRect(Math.max(0, (screenWidth - width) / 2), Math.max(0, (screenHeight - height) / 2), width, height);
        layout();
        updateButtons();
    }

    /// 切分窗口内部区域。
    ///
    /// 竖向上：第一行是窗口标题（右侧带实时求值结果），第二行是「属性：xxx」，
    /// 之后左右分栏——左侧输入区从**变量区顶部**一直拉到**函数列表底部**，两侧严格齐平；
    /// 变量区与函数区各自上方留一行小标题（例「变量」与 `+` / `−` 按钮）。
    private void layout() {
        int left = rect.x() + PADDING;
        int right2 = rect.right() - PADDING;
        int columnTop = rect.y() + PADDING + TITLE_HEIGHT + SUBTITLE_HEIGHT;
        int columnBottom = rect.bottom() - PADDING - BUTTON_HEIGHT - GAP;
        int leftWidth = Math.round((right2 - left - GAP) * 0.62f);
        textArea = new UiRect(left, columnTop, Math.max(40, leftWidth), Math.max(LINE_HEIGHT, columnBottom - columnTop));
        int right = textArea.right() + GAP;
        int rightWidth = Math.max(1, right2 - right);
        int columnHeight = columnBottom - columnTop;
        int variableHeight = Math.max(11 + LINE_HEIGHT, Math.round(columnHeight * VARIABLE_SECTION_RATIO));
        variableList = new UiRect(right, columnTop + LINE_HEIGHT, rightWidth, Math.max(LINE_HEIGHT, variableHeight - LINE_HEIGHT));
        int functionTop = columnTop + variableHeight + GAP;
        functionList = new UiRect(right, functionTop + LINE_HEIGHT, rightWidth, Math.max(LINE_HEIGHT, columnBottom - functionTop - LINE_HEIGHT));
    }

    /// 变量区的 `+` / `−` 与底部的「确定 / 取消」随窗口布局挪位置
    private void updateButtons() {
        int contentTop = rect.y() + PADDING + TITLE_HEIGHT + SUBTITLE_HEIGHT;
        int buttonY = rect.bottom() - PADDING - BUTTON_HEIGHT;
        int right = variableList.right();
        addButton.rect(new UiRect(right - SMALL_BUTTON_SIZE * 2 - 2, contentTop, SMALL_BUTTON_SIZE, SMALL_BUTTON_SIZE));
        removeButton.rect(new UiRect(right - SMALL_BUTTON_SIZE, contentTop, SMALL_BUTTON_SIZE, SMALL_BUTTON_SIZE));
        cancelButton.rect(new UiRect(rect.right() - PADDING - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT));
        confirmButton.rect(new UiRect(rect.right() - PADDING - BUTTON_WIDTH * 2 - GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    public boolean finished() {
        return finished;
    }

    // region 渲染

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, screenWidth, screenHeight, Draw.OVERLAY_DIM);
        Draw.canvas(graphics, rect, Draw.FLOATING_BG);
        Draw.border(graphics, rect, Draw.BORDER);
        renderTitle(graphics);
        Draw.textEllipsized(graphics, EditorLang.t("expression.attribute", fieldLabel.getString()).getString(),
                rect.x() + PADDING, rect.y() + PADDING + TITLE_HEIGHT, rect.width() - PADDING * 2, Draw.TEXT_DIM);
        renderTextArea(graphics, mouseX, mouseY);
        renderVariables(graphics, mouseX, mouseY);
        renderFunctions(graphics, mouseX, mouseY);
        widgets.render(graphics, mouseX, mouseY);
    }

    /// 标题行：左边窗口名，右边实时求值结果（合法给数值，非法转警告色）
    private void renderTitle(GuiGraphicsExtractor graphics) {
        Component title = EditorLang.t("expression.title");
        Draw.text(graphics, title, rect.x() + PADDING, rect.y() + PADDING, Draw.TEXT);
        String stripped = text.strip();
        String status;
        int color;

        if (stripped.isEmpty()) {
            status = EditorLang.t("expression.preview.empty").getString();
            color = Draw.TEXT_DISABLED;
        } else {
            float evaluated = context().evaluate(stripped);
            boolean valid = !Float.isNaN(evaluated);
            status = EditorLang.t("expression.preview.result", valid ? Draw.num(evaluated, 4) : EditorLang.t("expression.invalid").getString()).getString();
            color = valid ? Draw.ACCENT : Draw.WARNING;
        }

        int statusWidth = Draw.font().width(status);
        int available = rect.width() - PADDING * 2 - Draw.font().width(title.getString()) - GAP;

        if (statusWidth <= available) {
            Draw.textRight(graphics, status, rect.right() - PADDING, rect.y() + PADDING, color);
        } else {
            Draw.textEllipsized(graphics, status, rect.right() - PADDING - available, rect.y() + PADDING, available, color);
        }
    }

    private void renderTextArea(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean hovered = textArea.contains(mouseX, mouseY);
        Draw.field(graphics, textArea, hovered);
        // 文字区右侧留出滚动条的位置
        graphics.enableScissor(textArea.x() + 1, textArea.y() + 1, textArea.right() - SCROLLBAR_WIDTH - 2, textArea.bottom() - 1);

        if (text.isEmpty()) {
            Draw.text(graphics, EditorLang.t("expression.placeholder"), textArea.x() + TEXT_PADDING, textArea.y() + TEXT_PADDING, Draw.TEXT_DISABLED);
        }

        List<String> lines = lines();
        int caretLine = caretLine(lines);
        int visible = visibleLines();

        // 编辑动作之后把光标行带进视野；手动滚动过就不动它，免得刚滚开又被拽回来
        if (followCaret) {
            if (caretLine < textScroll) {
                textScroll = caretLine;
            } else if (caretLine >= textScroll + visible) {
                textScroll = caretLine - visible + 1;
            }
        }

        textScroll = clampScroll(textScroll, lines.size(), visible);

        for (int i = 0; i < visible && i + textScroll < lines.size(); i++) {
            int lineIndex = i + textScroll;
            Draw.text(graphics, lines.get(lineIndex), textArea.x() + TEXT_PADDING, textArea.y() + TEXT_PADDING + i * LINE_HEIGHT, Draw.TEXT);
        }

        // 光标闪烁；行已被上面滚进可视范围，这里再判一次是为了避免取到视口外
        if (!finished && (System.currentTimeMillis() / 500) % 2 == 0 && caretLine >= textScroll && caretLine < textScroll + visible) {
            String line = lines.get(caretLine);
            int column = Math.min(caret - lineStart(lines, caretLine), line.length());
            int caretX = Math.min(textArea.right() - SCROLLBAR_WIDTH - 3, textArea.x() + TEXT_PADDING + Draw.font().width(line.substring(0, column)));
            int caretY = textArea.y() + TEXT_PADDING + (caretLine - textScroll) * LINE_HEIGHT;
            Draw.vLine(graphics, caretX, caretY, caretY + 9, Draw.ACCENT);
        }

        graphics.disableScissor();
        renderScrollbar(graphics, textArea, lines.size(), visible, textScroll);
    }

    private void renderVariables(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.text(graphics, EditorLang.t("expression.variables"), variableList.x(), variableList.y() - LINE_HEIGHT, Draw.TEXT_DIM);
        Draw.canvas(graphics, variableList, Draw.CANVAS_BG);
        Draw.border(graphics, variableList, Draw.BORDER);
        graphics.enableScissor(variableList.x() + 1, variableList.y() + 1, variableList.right() - 1, variableList.bottom() - 1);
        List<Variable> variables = animation.variables();
        int visible = visibleRows(variableList);

        if (variables.isEmpty()) {
            Draw.textEllipsized(graphics, EditorLang.t("expression.variables.empty").getString(), variableList.x() + 3, variableList.y() + 2,
                    variableList.width() - 6, Draw.TEXT_DISABLED);
        }

        for (int i = 0; i < visible && i + variableScroll < variables.size(); i++) {
            Variable variable = variables.get(i + variableScroll);
            UiRect row = rowRect(variableList, i);
            boolean active = variable == selected;

            if (active) {
                Draw.canvas(graphics, row, Draw.ROW_SELECTED);
            } else if (row.contains(mouseX, mouseY)) {
                Draw.canvas(graphics, row, Draw.ROW_ALT);
            }

            Draw.text(graphics, variable.name(), row.x() + 3, row.y() + 1, Draw.TEXT);
            String source = sourceLabel(variable);
            int nameWidth = Draw.font().width(variable.name()) + 6;
            Draw.textEllipsized(graphics, source, row.x() + 3 + nameWidth, row.y() + 1,
                    Math.max(1, row.width() - nameWidth - 6 - SCROLLBAR_WIDTH), Draw.TEXT_DISABLED);
        }

        graphics.disableScissor();
        renderScrollbar(graphics, variableList, variables.size(), visible, variableScroll);
    }

    private void renderFunctions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.text(graphics, EditorLang.t("expression.functions"), functionList.x(), functionList.y() - LINE_HEIGHT, Draw.TEXT_DIM);
        Draw.canvas(graphics, functionList, Draw.CANVAS_BG);
        Draw.border(graphics, functionList, Draw.BORDER);
        graphics.enableScissor(functionList.x() + 1, functionList.y() + 1, functionList.right() - 1, functionList.bottom() - 1);
        int visible = visibleRows(functionList);
        functionScroll = clampScroll(functionScroll, FUNCTIONS.size(), visible);

        for (int i = 0; i < visible && i + functionScroll < FUNCTIONS.size(); i++) {
            UiRect row = rowRect(functionList, i);

            if (row.contains(mouseX, mouseY)) {
                Draw.canvas(graphics, row, Draw.ROW_ALT);
            }

            Draw.text(graphics, FUNCTIONS.get(i + functionScroll).label(), row.x() + 3, row.y() + 1, Draw.TEXT);
        }

        graphics.disableScissor();
        renderScrollbar(graphics, functionList, FUNCTIONS.size(), visible, functionScroll);
    }

    /// 变量取值来源的显示文本：绑定了显示轨道名，否则显示它的固定值
    private String sourceLabel(Variable variable) {
        if (!variable.bound()) {
            return EditorLang.t("expression.variable.fixed", Draw.num(variable.value(), 3)).getString();
        }

        CurveTrack track = track(variable.trackId());
        return track == null ? EditorLang.t("expression.variable.unbound").getString() : track.label().getString();
    }

    /// 贴区域右边缘内侧的滚动条；内容装得下就不画
    private static void renderScrollbar(GuiGraphicsExtractor graphics, UiRect area, int total, int visible, int scroll) {
        int maxScroll = Math.max(0, total - visible);

        if (maxScroll <= 0) {
            return;
        }

        int x = area.right() - SCROLLBAR_WIDTH - 1;
        int top = area.y() + 1;
        int height = Math.max(1, area.height() - 2);
        int thumbHeight = Mth.clamp(Math.round((float) height * visible / total), 8, height);
        int thumbTop = top + Math.round((float) (height - thumbHeight) * scroll / maxScroll);
        graphics.fill(x, top, x + SCROLLBAR_WIDTH, top + height, Draw.GRID);
        graphics.fill(x, thumbTop, x + SCROLLBAR_WIDTH, thumbTop + thumbHeight, Draw.BORDER);
    }

    private static UiRect rowRect(UiRect list, int index) {
        return new UiRect(list.x() + 1, list.y() + 1 + index * LINE_HEIGHT, Math.max(1, list.width() - 2), LINE_HEIGHT);
    }

    private static int visibleRows(UiRect list) {
        return Math.max(1, (list.height() - 2) / LINE_HEIGHT);
    }

    private int visibleLines() {
        return Math.max(1, (textArea.height() - TEXT_PADDING * 2) / LINE_HEIGHT);
    }

    private static int clampScroll(int scroll, int count, int visible) {
        return Mth.clamp(scroll, 0, Math.max(0, count - visible));
    }

    // endregion

    // region 输入

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (widgets.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (textArea.contains(event.x(), event.y()) && event.button() == 0) {
            moveCaretToMouse(event.x(), event.y());
            return true;
        }

        if (variableList.contains(event.x(), event.y()) && event.button() == 0) {
            clickVariable(event.y());
            return true;
        }

        if (functionList.contains(event.x(), event.y()) && event.button() == 0) {
            clickFunction(event.y());
            return true;
        }

        // 点在窗口之外只当作无事发生：输入区是一段没保存的文本，误点一下就丢掉太难接受
        return rect.contains(event.x(), event.y());
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return widgets.mouseReleased(event);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : 1;

        if (textArea.contains(mouseX, mouseY)) {
            followCaret = false;
            textScroll = clampScroll(textScroll + step, lines().size(), visibleLines());
            return true;
        }

        if (variableList.contains(mouseX, mouseY)) {
            variableScroll = clampScroll(variableScroll + step, animation.variables().size(), visibleRows(variableList));
            return true;
        }

        if (functionList.contains(mouseX, mouseY)) {
            functionScroll = clampScroll(functionScroll + step, FUNCTIONS.size(), visibleRows(functionList));
            return true;
        }

        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (widgets.keyPressed(event)) {
            return true;
        }

        switch (event.key()) {
            case KEY_ESCAPE -> finished = true;
            case KEY_ENTER, KEY_KP_ENTER -> insert("\n");
            case KEY_BACKSPACE -> backspace();
            case KEY_DELETE -> delete();
            case KEY_LEFT -> moveCaret(-1);
            case KEY_RIGHT -> moveCaret(1);
            case KEY_UP, KEY_DOWN -> moveCaretVertically(event.key() == KEY_UP ? -1 : 1);
            case KEY_HOME -> moveCaretToLineEdge(true);
            case KEY_END -> moveCaretToLineEdge(false);
            case KEY_V -> {
                if (!ctrlDown()) {
                    return false;
                }

                insert(Minecraft.getInstance().keyboardHandler.getClipboard().replace("\r", ""));
            }
            default -> {
                return false;
            }
        }

        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        int codepoint = event.codepoint();

        // 控制字符（回车、换行、制表符）由 keyPressed 处理，其余按完整码点插入
        if (Character.isISOControl(codepoint)) {
            return true;
        }

        insert(new String(Character.toChars(codepoint)));
        return true;
    }

    // endregion

    // region 编辑动作

    private void confirm() {
        finished = true;
        onConfirm.accept(text.strip());
    }

    private void insert(String addition) {
        if (addition.isEmpty()) {
            return;
        }

        int room = MAX_TEXT_LENGTH - text.length();

        if (room <= 0) {
            return;
        }

        String clipped = addition.length() <= room ? addition : addition.substring(0, room);
        text = text.substring(0, caret) + clipped + text.substring(caret);
        caret += clipped.length();
        followCaret = true;
    }

    /// 插入一段函数模板；以 `)` 结尾时把光标停进括号里，省得再按一次左箭头
    private void insertTemplate(String template) {
        insert(template);

        if (template.endsWith(")")) {
            caret--;
        }
    }

    private void backspace() {
        if (caret <= 0) {
            return;
        }

        int from = previousIndex(caret);
        text = text.substring(0, from) + text.substring(caret);
        caret = from;
        followCaret = true;
    }

    private void delete() {
        if (caret >= text.length()) {
            return;
        }

        text = text.substring(0, caret) + text.substring(nextIndex(caret));
        followCaret = true;
    }

    private void moveCaret(int delta) {
        caret = Mth.clamp(delta < 0 ? previousIndex(caret) : nextIndex(caret), 0, text.length());
        followCaret = true;
    }

    /// 上下移动：尽量保持在同一列上
    private void moveCaretVertically(int delta) {
        List<String> lines = lines();
        int line = caretLine(lines);
        int target = line + delta;

        if (target < 0 || target >= lines.size()) {
            return;
        }

        int column = Math.min(caret - lineStart(lines, line), lines.get(target).length());
        caret = lineStart(lines, target) + column;
        followCaret = true;
    }

    private void moveCaretToLineEdge(boolean start) {
        List<String> lines = lines();
        int line = caretLine(lines);
        caret = start ? lineStart(lines, line) : lineStart(lines, line) + lines.get(line).length();
        followCaret = true;
    }

    private void moveCaretToMouse(double mouseX, double mouseY) {
        List<String> lines = lines();
        int row = (int) ((mouseY - textArea.y() - TEXT_PADDING) / LINE_HEIGHT);
        int line = Mth.clamp(row + textScroll, 0, lines.size() - 1);
        String content = lines.get(line);
        int column = 0;
        int width = 0;

        // 按字符逐个量宽，落到离鼠标最近的两个字符之间
        while (column < content.length()) {
            int next = nextIndexIn(content, column);
            int nextWidth = Draw.font().width(content.substring(0, next));

            if (mouseX < textArea.x() + TEXT_PADDING + (width + nextWidth) / 2f) {
                break;
            }

            width = nextWidth;
            column = next;
        }

        caret = lineStart(lines, line) + column;
        followCaret = true;
    }

    private void clickVariable(double mouseY) {
        List<Variable> variables = animation.variables();
        int index = (int) ((mouseY - variableList.y() - 1) / LINE_HEIGHT) + variableScroll;

        if (index < 0 || index >= variables.size()) {
            return;
        }

        Variable variable = variables.get(index);
        selected = variable;
        insert(variable.name());
    }

    private void clickFunction(double mouseY) {
        int index = (int) ((mouseY - functionList.y() - 1) / LINE_HEIGHT) + functionScroll;

        if (index >= 0 && index < FUNCTIONS.size()) {
            insertTemplate(FUNCTIONS.get(index).template());
        }
    }

    /// 新增变量：名字从 var1 起找第一个没被占用的
    private void addVariable() {
        for (int i = 1; i <= 999; i++) {
            Variable variable = animation.addVariable("var" + i);

            if (variable != null) {
                selected = variable;
                return;
            }
        }
    }

    /// 删除当前选中的变量；公式里已经写了它的名字，会因此取不到值（提示器会显示公式非法）
    private void removeVariable() {
        if (selected != null && animation.removeVariable(selected.name())) {
            selected = null;
            variableScroll = clampScroll(variableScroll, animation.variables().size(), visibleRows(variableList));
        }
    }

    // endregion

    // region 文本工具

    /// 按 `\n` 切出各行（不含换行符本身）
    private List<String> lines() {
        List<String> lines = new ArrayList<>();
        int start = 0;

        while (true) {
            int end = text.indexOf('\n', start);
            lines.add(text.substring(start, end < 0 ? text.length() : end));

            if (end < 0) {
                return lines;
            }

            start = end + 1;
        }
    }

    private static int lineStart(List<String> lines, int line) {
        int start = 0;

        for (int i = 0; i < line; i++) {
            start += lines.get(i).length() + 1;
        }

        return start;
    }

    /// 光标所在行
    private int caretLine(List<String> lines) {
        int start = 0;

        for (int i = 0; i < lines.size(); i++) {
            int end = start + lines.get(i).length();

            if (caret <= end) {
                return i;
            }

            start = end + 1;
        }

        return lines.size() - 1;
    }

    private ExpressionContext context() {
        return new ExpressionContext(animation, player.time());
    }

    private @Nullable CurveTrack track(String id) {
        if (id.isEmpty()) {
            return null;
        }

        for (CurveTrack track : animation.curveTracks()) {
            if (track.id().equals(id)) {
                return track;
            }
        }

        return null;
    }

    /// 前一个字符的起点（跳过代理对的后半）
    private int previousIndex(int index) {
        if (index <= 0) {
            return 0;
        }

        int previous = index - 1;
        return Character.isLowSurrogate(text.charAt(previous)) && previous > 0 && Character.isHighSurrogate(text.charAt(previous - 1))
                ? previous - 1 : previous;
    }

    /// 后一个字符的起点（跳过代理对的前半）
    private int nextIndex(int index) {
        if (index >= text.length()) {
            return text.length();
        }

        int next = index + 1;
        return Character.isHighSurrogate(text.charAt(index)) && next < text.length() && Character.isLowSurrogate(text.charAt(next))
                ? next + 1 : next;
    }

    private static int nextIndexIn(String content, int index) {
        int next = index + 1;
        return Character.isHighSurrogate(content.charAt(index)) && next < content.length() && Character.isLowSurrogate(content.charAt(next))
                ? next + 1 : next;
    }

    /// Ctrl 是否按下（粘贴用）
    private static boolean ctrlDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    // endregion
}
