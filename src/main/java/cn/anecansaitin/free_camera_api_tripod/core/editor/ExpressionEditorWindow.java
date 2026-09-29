package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ConstantValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.ExpressionScope;
import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.Scope;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Expression;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.FormulaValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.TrackValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlayer;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.EditorWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.LabelWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.TextFieldWidget;
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
/// 函数表同理，插入模板并把光标停进括号里。标题栏右侧实时给出当前公式的求值结果，
/// 公式非法时转成警告色并说明原因——但**不会**阻止保存，求值失败时播放链会回退到固定数值。
///
/// 唯一拦住不让保存的是**自嵌套**：公式引用了绑定到本轨道的变量，那取到的是轨道上的数值模式取值，
/// 与本键播放时的取值不是一回事，改了就说不清，所以标题栏标红并按住「确定」。
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
    /// 参数输入框的宽度与长度上限
    private static final int PARAMETER_FIELD_WIDTH = 150;
    private static final int PARAMETER_MAX_LENGTH = 64;
    /// 变量表占右栏的比例，其余留给函数表
    private static final float VARIABLE_SECTION_RATIO = 0.58f;

    /// 编辑函数体时的参数栏：窗口在第二行摆一个输入框，改动即时写回。
    /// 预览与「能不能保存」都按这份参数判定
    public interface Parameters {
        List<String> get();

        void set(List<String> parameters);
    }

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

    /// 变量表的一行：底板铺整行（底色 + 点击），名字与取值来源分两段排。
    /// 两层是分开的控件，所以名字与来源能用不同颜色，整行的可点区域又只有一处
    private record VariableRow(LabelWidget base, LabelWidget name, LabelWidget source, Variable variable) {
    }

    /// 函数表的一行：底板铺整行，文字只有一段
    private record FunctionRow(LabelWidget base, LabelWidget label, FunctionItem item) {
    }

    /// 函数表的内容：内置函数（清单来自 {@link Expression}，避免两处不同步）+ 动画里的自定义函数。
    /// 自定义函数随时可加，所以每次现拼一份（几十项，开销可忽略）
    private List<FunctionItem> functionItems() {
        List<FunctionItem> items = new ArrayList<>(Expression.BUILTIN_FUNCTIONS.size() + 4);

        for (String signature : Expression.BUILTIN_FUNCTIONS) {
            items.add(new FunctionItem(signature, functionName(signature) + "()"));
        }

        for (CustomFunction function : animation.symbols().functions()) {
            items.add(new FunctionItem(function.name() + "(" + String.join(", ", function.parameters()) + ")",
                    function.name() + "()"));
        }

        return items;
    }

    /// 函数名就是签名里 `(` 之前的那一段
    private static String functionName(String signature) {
        int at = signature.indexOf('(');
        return at < 0 ? signature : signature.substring(0, at);
    }

    private final CameraAnimation animation;
    private final CameraPlayer player;
    /// 正在编辑的字段名（例如「取值」「位置 X」），显示在标题右侧，用来区分开的是哪个数值的公式
    private final Component fieldLabel;
    /// 这条公式所属的轨道 id；不属于任何轨道（例如变量自己的公式）时为 null。自嵌套判定要用
    private final @Nullable String trackId;
    /// 编辑函数体时的参数栏；普通的公式编辑为 null，那时不显示参数输入框
    private final @Nullable Parameters parameters;
    /// 参数输入框；不编辑函数体时为 null
    private final @Nullable TextFieldWidget parameterField;
    /// 上一次参数提交被拒的原因（有参数名写不进公式）；没问题时为 null
    private @Nullable Component parameterProblem;
    /// 编辑中的参数名：改动先落在窗口里，点「确定」才写回函数，取消就原样丢弃
    private final List<String> editingParameters;
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
    /// 两个列表当前摆出来的行控件；行数或滚动位置一变就整批换掉
    private final List<VariableRow> variableRows = new ArrayList<>();
    private final List<FunctionRow> functionRows = new ArrayList<>();
    /// 上一次摆行时的摘要（条目数、滚动位置、两个列表的位置）；没变就不重摆
    private @Nullable String rowRevision;
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
                                  @Nullable String expression, @Nullable String trackId, @Nullable Parameters parameters,
                                  Consumer<String> onConfirm) {
        this.animation = animation;
        this.player = player;
        this.fieldLabel = fieldLabel;
        this.trackId = trackId;
        this.parameters = parameters;
        this.editingParameters = parameters == null ? new ArrayList<>() : new ArrayList<>(parameters.get());
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

        // 编辑函数体时才摆参数输入框：参数怎么改只影响预览与合法性判定，位置由 layout() 定
        if (parameters == null) {
            this.parameterField = null;
        } else {
            this.parameterField = new TextFieldWidget(new UiRect(0, 0, 1, 1), String.join(", ", parameters.get()), this::applyParameters);
            this.parameterField.maxLength(PARAMETER_MAX_LENGTH);
            // 参数在预览里一律当 1，这点容易误会，悬停时说明
            this.parameterField.tooltip(EditorLang.t("expression.parameters.tip"));
            widgets.add(parameterField);
        }
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

        // 参数输入框钉在第二行右端
        if (parameterField != null) {
            parameterField.rect(new UiRect(right2 - PARAMETER_FIELD_WIDTH, rect.y() + PADDING + TITLE_HEIGHT - 1,
                    PARAMETER_FIELD_WIDTH, SUBTITLE_HEIGHT - 1));
        }
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
        // 自嵌套、或函数本身还不能用时把「确定」按住：前者是取值口径有歧义，后者是存下去也用不了
        confirmButton.enabled(!selfReferencing() && functionProblem() == null);
        graphics.fill(0, 0, screenWidth, screenHeight, Draw.OVERLAY_DIM);
        Draw.canvas(graphics, rect, Draw.FLOATING_BG);
        Draw.border(graphics, rect, Draw.BORDER);
        renderTitle(graphics);
        renderSubtitle(graphics);
        renderTextArea(graphics, mouseX, mouseY);
        rebuildRows();
        renderVariables(graphics, mouseX, mouseY);
        renderFunctions(graphics, mouseX, mouseY);
        widgets.render(graphics, mouseX, mouseY);
    }

    /// 第二行：「属性：xxx」；编辑函数体时右端再摆一个参数输入框，标签在它左边
    private void renderSubtitle(GuiGraphicsExtractor graphics) {
        Component attribute = EditorLang.t("expression.attribute", fieldLabel.getString());
        int y = rect.y() + PADDING + TITLE_HEIGHT;

        if (parameterField == null) {
            Draw.textEllipsized(graphics, attribute.getString(), rect.x() + PADDING, y, rect.width() - PADDING * 2, Draw.TEXT_DIM);
            return;
        }

        Component label = EditorLang.t("expression.parameters");
        int labelWidth = Draw.font().width(label) + GAP;
        Draw.text(graphics, label, parameterField.rect().x() - labelWidth, y, Draw.TEXT_DIM);
        Draw.textEllipsized(graphics, attribute.getString(), rect.x() + PADDING, y,
                Math.max(20, parameterField.rect().x() - labelWidth - GAP - (rect.x() + PADDING)), Draw.TEXT_DIM);
    }

    /// 标题行：左边窗口名，右边实时求值结果（自嵌套、成环与非法都转警告色并说明原因）
    private void renderTitle(GuiGraphicsExtractor graphics) {
        Component title = EditorLang.t("expression.title");
        Draw.text(graphics, title, rect.x() + PADDING, rect.y() + PADDING, Draw.TEXT);
        String stripped = text.strip();
        String status;
        int color;

        if (stripped.isEmpty()) {
            status = EditorLang.t("expression.preview.empty").getString();
            color = Draw.TEXT_DISABLED;
        } else if (functionProblem() != null) {
            // 函数还不能用时先报这个：比起求值结果，用户更该知道差在哪
            status = functionProblem().getString();
            color = Draw.WARNING;
        } else if (selfReferencing()) {
            status = EditorLang.t("variables.self_reference").getString();
            color = Draw.WARNING;
        } else {
            ExpressionScope scope = scope();
            float evaluated = Expression.evaluate(stripped, previewResolver(scope));
            List<String> cycle = scope.cycle();

            if (cycle != null) {
                status = EditorLang.t("variables.cycle", String.join(" → ", cycle)).getString();
                color = Draw.WARNING;
            } else {
                boolean valid = !Float.isNaN(evaluated);
                status = EditorLang.t("expression.preview.result", valid ? Draw.num(evaluated, 4) : EditorLang.t("expression.invalid").getString()).getString();
                color = valid ? Draw.ACCENT : Draw.WARNING;
            }
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

        // 光标闪烁；行已被上面滚进可视范围，这里再判一次是为了避免取到视口外。
        // 焦点在参数输入框这类文本控件上时不再画这一根：两个光标一起闪，分不清字会落进哪边
        boolean ownsCaret = !(widgets.focused() instanceof TextFieldWidget);

        if (ownsCaret && !finished && (System.currentTimeMillis() / 500) % 2 == 0 && caretLine >= textScroll && caretLine < textScroll + visible) {
            String line = lines.get(caretLine);
            int column = Math.min(caret - lineStart(lines, caretLine), line.length());
            int caretX = Math.min(textArea.right() - SCROLLBAR_WIDTH - 3, textArea.x() + TEXT_PADDING + Draw.font().width(line.substring(0, column)));
            int caretY = textArea.y() + TEXT_PADDING + (caretLine - textScroll) * LINE_HEIGHT;
            Draw.vLine(graphics, caretX, caretY, caretY + 9, Draw.ACCENT);
        }

        graphics.disableScissor();
        renderScrollbar(graphics, textArea, lines.size(), visible, textScroll);
    }

    /// 按当前位置摆出变量表与函数表要显示的行控件。
    ///
    /// 行是「底板 + 文字」两层：底板铺满整行、负责选中 / 悬停底色与点击，文字层不接点击，
    /// 事件会穿透回底板——这样一行里能用多种文字颜色，而可点区域仍只有整行一处。
    /// 只有摘要变了才重摆：每帧重建会不断造出新控件，还会冲掉「按下 → 松开」这类跨帧状态
    private void rebuildRows() {
        String revision = variableScroll + "|" + variableList + '|' + animation.symbols().variables().size()
                + '|' + functionScroll + "|" + functionList + '|' + functionItems().size();

        if (revision.equals(rowRevision)) {
            return;
        }

        rowRevision = revision;
        clearRows();
        List<Variable> variables = animation.symbols().variables();
        int visible = visibleRows(variableList);

        for (int i = 0; i < visible && i + variableScroll < variables.size(); i++) {
            Variable variable = variables.get(i + variableScroll);
            UiRect row = rowRect(variableList, i);
            VariableRow widgetsRow = new VariableRow(new LabelWidget(row, Component.empty()),
                    new LabelWidget(row, Component.empty()), new LabelWidget(row, Component.empty()), variable);
            widgetsRow.base().onClick(() -> pickVariable(variable));
            widgets.add(widgetsRow.base());
            widgets.add(widgetsRow.name());
            widgets.add(widgetsRow.source());
            variableRows.add(widgetsRow);
        }

        List<FunctionItem> functions = functionItems();
        visible = visibleRows(functionList);

        for (int i = 0; i < visible && i + functionScroll < functions.size(); i++) {
            FunctionItem item = functions.get(i + functionScroll);
            UiRect row = rowRect(functionList, i);
            FunctionRow widgetsRow = new FunctionRow(new LabelWidget(row, Component.empty()),
                    new LabelWidget(row, Component.literal(item.label())), item);
            widgetsRow.base().onClick(() -> pickFunction(item));
            widgets.add(widgetsRow.base());
            widgets.add(widgetsRow.label());
            functionRows.add(widgetsRow);
        }
    }

    /// 撤掉当前摆着的行控件（只撤行，`+` / `−` 与底部按钮这些常驻控件不动）
    private void clearRows() {
        for (VariableRow row : variableRows) {
            removeRowWidgets(row.base(), row.name(), row.source());
        }

        for (FunctionRow row : functionRows) {
            removeRowWidgets(row.base(), row.label());
        }

        variableRows.clear();
        functionRows.clear();
    }

    private void removeRowWidgets(EditorWidget... rowWidgets) {
        List<EditorWidget> targets = List.of(rowWidgets);
        widgets.removeIf(targets::contains);
    }

    private void renderVariables(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.text(graphics, EditorLang.t("expression.variables"), variableList.x(), variableList.y() - LINE_HEIGHT, Draw.TEXT_DIM);
        Draw.canvas(graphics, variableList, Draw.CANVAS_BG);
        Draw.border(graphics, variableList, Draw.BORDER);
        List<Variable> variables = animation.symbols().variables();

        if (variables.isEmpty()) {
            Draw.textEllipsized(graphics, EditorLang.t("expression.variables.empty").getString(), variableList.x() + 3, variableList.y() + 2,
                    variableList.width() - 6, Draw.TEXT_DISABLED);
        }

        // 底色、名字与取值来源每帧都要现算：选中状态与来源都会变，不能固化在控件里
        for (VariableRow row : variableRows) {
            refreshVariableRow(row);
        }

        renderScrollbar(graphics, variableList, variables.size(), visibleRows(variableList), variableScroll);
    }

    /// 变量行：整行底色随选中 / 悬停变化，名字与取值来源分两段。
    /// 名字长度会随改名变，所以每帧重算两段的宽度与位置
    private void refreshVariableRow(VariableRow row) {
        Variable variable = row.variable();
        UiRect base = row.base().rect();
        int nameWidth = Draw.font().width(variable.name()) + 6;
        row.base().background(variable == selected ? Draw.ROW_SELECTED : 0, Draw.ROW_ALT);
        row.name().text(Component.literal(variable.name()));
        row.name().rect(new UiRect(base.x() + 3, base.y(), Math.max(1, nameWidth), LINE_HEIGHT));
        row.source().text(Component.literal(sourceLabel(variable)));
        row.source().rect(new UiRect(base.x() + 3 + nameWidth, base.y(),
                Math.max(1, base.width() - nameWidth - 6), LINE_HEIGHT));
    }

    private void renderFunctions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.text(graphics, EditorLang.t("expression.functions"), functionList.x(), functionList.y() - LINE_HEIGHT, Draw.TEXT_DIM);
        Draw.canvas(graphics, functionList, Draw.CANVAS_BG);
        Draw.border(graphics, functionList, Draw.BORDER);
        List<FunctionItem> functions = functionItems();
        functionScroll = clampScroll(functionScroll, functions.size(), visibleRows(functionList));

        for (FunctionRow row : functionRows) {
            UiRect base = row.base().rect();
            row.base().background(0, Draw.ROW_ALT);
            row.label().rect(new UiRect(base.x() + 3, base.y(), Math.max(1, base.width() - 6), LINE_HEIGHT));
        }

        renderScrollbar(graphics, functionList, functions.size(), visibleRows(functionList), functionScroll);
    }

    /// 变量取值来源的显示文本：固定值给出数值，轨道给出轨道名，公式给出原文
    private String sourceLabel(Variable variable) {
        return switch (variable.source()) {
            case ConstantValue ignored -> EditorLang.t("expression.variable.fixed", Draw.num(variable.source().constant(), 3)).getString();
            case FormulaValue formula -> formula.expression();
            case TrackValue ignored -> {
                CurveTrack track = track(variable.trackId());
                yield track == null ? EditorLang.t("expression.variable.unbound").getString() : track.label().getString();
            }
        };
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

    /// 列表里第 index 行的矩形。右侧给滚动条留出位置，免得行底色把滚动条盖住
    private static UiRect rowRect(UiRect list, int index) {
        return new UiRect(list.x() + 1, list.y() + 1 + index * LINE_HEIGHT,
                Math.max(1, list.width() - 2 - SCROLLBAR_WIDTH - 1), LINE_HEIGHT);
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

        // 两个列表的行是控件，点它们插名字 / 模板这一层已经在上面的 widgets.mouseClicked 里处理

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
            variableScroll = clampScroll(variableScroll + step, animation.symbols().variables().size(), visibleRows(variableList));
            return true;
        }

        if (functionList.contains(mouseX, mouseY)) {
            functionScroll = clampScroll(functionScroll + step, functionItems().size(), visibleRows(functionList));
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
        // 控件（参数输入框）先拿字符：少了这一步，选中它之后打的字会全落进公式文本里
        if (widgets.charTyped(event)) {
            return true;
        }

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

    /// 预览用的作用域：函数参数**一律取 1**，方便把它们当单位量试算（`a + b` 预览就是 2）。
    /// 不这么处理的话，编辑函数体时合法的 `a + b` 会因为参数不是动画变量而被显示成算不出来
    private Expression.Resolver previewResolver(Scope scope) {
        List<String> names = editingParameters;

        if (names.isEmpty()) {
            return scope.resolver();
        }

        Expression.Resolver outer = scope.resolver();

        return new Expression.Resolver() {
            @Override
            public float resolve(String name) {
                return names.contains(name) ? 1f : outer.resolve(name);
            }

            @Override
            public @Nullable CustomFunction function(String name) {
                return outer.function(name);
            }
        };
    }

    /// 正在编辑的函数还能不能用；能用返回 null。
    ///
    /// 与「确定」按钮共用同一份判定：参数名写不进公式、函数体为空、函数体编译不过都算不能用
    private @Nullable Component functionProblem() {
        if (parameters == null) {
            return null;
        }

        if (parameterProblem != null) {
            return parameterProblem;
        }

        if (text.isBlank()) {
            return EditorLang.t("expression.body_empty");
        }

        return Expression.compile(text.strip()) == null ? EditorLang.t("expression.body_invalid") : null;
    }

    /// 参数输入框提交：按逗号拆成参数名，有一个写不进公式就整条拒绝，留着原来的并给出提示。
    /// 结果只落在窗口里，点「确定」时才写回函数——取消窗口不该顺手改掉参数
    private void applyParameters(String value) {
        List<String> parsed = new ArrayList<>();

        for (String part : value.split(",")) {
            String name = part.strip();

            if (name.isEmpty()) {
                continue;
            }

            if (!Expression.validName(name)) {
                parameterProblem = EditorLang.t("expression.parameter_invalid", name);
                return;
            }

            parsed.add(name);
        }

        parameterProblem = null;
        editingParameters.clear();
        editingParameters.addAll(parsed);
    }

    /// 公式是否引用了绑定到本轨道的变量——那就是自嵌套：那个变量在本轨道上取的是数值模式的固定数值，
    /// 与本键播放时的取值不是一回事（与变量面板的判定是同一件事）
    private boolean selfReferencing() {
        if (trackId == null) {
            return false;
        }

        for (String name : Expression.identifiers(text)) {
            Variable variable = animation.symbols().variable(name);

            if (variable != null && trackId.equals(variable.trackId())) {
                return true;
            }
        }

        return false;
    }

    private void confirm() {
        // 按钮已经被按住，这里再挡一次：回车等快捷路径也不能把自嵌套或还不能用的函数存进去
        if (selfReferencing() || functionProblem() != null) {
            return;
        }

        if (parameters != null) {
            parameters.set(List.copyOf(editingParameters));
        }

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

    /// 点变量行：选中它（`−` 按钮删的就是它）并把名字插到光标处
    private void pickVariable(Variable variable) {
        selected = variable;
        insert(variable.name());
    }

    /// 点函数行：把调用模板插到光标处
    private void pickFunction(FunctionItem item) {
        insertTemplate(item.template());
    }

    /// 新增变量：名字由符号表从 var1 起找第一个没被占用的
    private void addVariable() {
        Variable variable = animation.symbols().addVariable();

        if (variable != null) {
            selected = variable;
        }
    }

    /// 删除当前选中的变量；公式里已经写了它的名字，会因此取不到值（提示器会显示公式非法）
    private void removeVariable() {
        if (selected != null && animation.symbols().removeVariable(selected.name())) {
            selected = null;
            variableScroll = clampScroll(variableScroll, animation.symbols().variables().size(), visibleRows(variableList));
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

    /// 本次预览用的作用域：每帧现建一份，变量改了当帧就能反映到结果上
    private ExpressionScope scope() {
        return ExpressionScope.of(animation, player.time(), player.worldTime());
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
