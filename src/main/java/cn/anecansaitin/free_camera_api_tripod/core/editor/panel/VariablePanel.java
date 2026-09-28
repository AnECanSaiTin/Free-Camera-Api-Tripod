package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.ExpressionScope;
import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.Scope;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ConstantValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Expression;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.FormulaValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.TrackValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.LabelWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.NumberFieldWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 变量面板：定义表达式里可以按名字引用的变量。
///
/// 每个变量一行：选中标记 + 名字（可编辑，不限字符集，中文也行）+ 取值来源 + 取值。
/// 取值来源三选一：
/// - **固定值**：取值列就是一个可编辑的数值框，变量恒等于这个数
/// - **公式**：变量由一段表达式算出来，可以引用别的变量
/// - **曲线轨道**：播放时取该轨道在当前时刻的读数（只读静态曲线，见 [ExpressionScope]）
///
/// 变量之间靠公式里的名字互相引用，成环会让求值原地打转，所以环上的变量会标红并在悬停时说明。
/// 名字要能被表达式识别成标识符（字母、下划线或非 ASCII 字符开头），改名与新建都会挡掉重名。
///
/// 列表最前面还有两行内置变量（播放进度、世界时间）：它们由当前播放状态提供，
/// 改不了也不需要定义，摆在这里是为了让人知道公式里能直接写什么
public class VariablePanel extends EditorPanel {
    public static final String ID = "variables";

    /// 行高与行内控件高度：统一取面板基类的值，与其它面板、各栏按钮同高
    private static final int ROW_HEIGHT = EditorPanel.ROW_HEIGHT;
    private static final int FIELD_HEIGHT = EditorPanel.CONTROL_HEIGHT;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_MARGIN = 4;
    /// 行首选中标记的宽度
    private static final int MARKER_WIDTH = 9;
    /// 行尾取值列占的最大宽度：固定值模式下这里放一个数值输入框
    private static final int VALUE_WIDTH = 58;
    /// 行内三列之间的间隙
    private static final int GAP = 2;
    /// 变量名长度上限：名字要手写进公式里，太长没意义
    private static final int NAME_MAX_LENGTH = 24;
    /// 固定值与取值的显示精度
    private static final int VALUE_DECIMALS = 3;

    private final EditorContext context;
    private final List<Runnable> refreshers = new ArrayList<>();
    private @Nullable String selectedName;
    private @Nullable String lastRevision;
    /// 变量之间的第一条循环引用（变量名，首尾同名）；重建时算一次，界面据此标红
    private @Nullable List<String> cycle;
    private int scrollY;
    private int totalHeight;
    private int contentRight;

    public VariablePanel(EditorContext context) {
        super(ID, EditorLang.t("panel.variables"));
        this.context = context;
    }

    @Override
    protected void layoutWidgets(UiRect content) {
        rebuild(content);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, UiRect content, int mouseX, int mouseY) {
        Draw.canvas(graphics, content, Draw.CANVAS_BG);

        if (!revision().equals(lastRevision)) {
            rebuild(content);
        }

        // 取值与选中状态每帧都在变：控件摆好位置，文本与配色交给刷新器现算
        for (Runnable refresher : refreshers) {
            refresher.run();
        }

        renderScrollbar(graphics, content);
    }

    /// 这一行的取值是否有问题：成环、自嵌套，或者干脆算不出来（NaN）。
    /// 来源列与取值列都据此标红，两列颜色对得上
    private boolean valueInvalid(Variable variable) {
        return valueProblem(variable) != null || Float.isNaN(variable.source().evaluate(context.scope()));
    }

    /// 取不到值的原因；算得出值时返回 null。标红的两列共用它，悬停时也拿它当提示
    private @Nullable Component valueProblem(Variable variable) {
        List<String> currentCycle = cycle;

        if (currentCycle != null && currentCycle.contains(variable.name())) {
            return EditorLang.t("variables.cycle", String.join(" → ", currentCycle));
        }

        if (context.animation().selfReferencing(variable)) {
            return EditorLang.t("variables.self_reference");
        }

        return null;
    }

    /// 变量表与曲线轨道变化时重建；选中状态与只读取值不进 revision，否则每帧都要重建。
    /// 取值来源的类型与内容都进 revision：换了来源，取值列的控件形态也要跟着换
    private String revision() {
        StringBuilder builder = new StringBuilder();

        for (Variable variable : context.animation().variables()) {
            builder.append(variable.name()).append(':').append(variable.source()).append('|');
        }

        builder.append('#');

        for (CurveTrack track : context.animation().curveTracks()) {
            builder.append(track.id()).append('|');
        }

        return builder.toString();
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        // 成环只在重建时算一次：变量表与公式都进了 revision，重建即意味着依赖关系变了
        cycle = context.animation().variableCycle();
        widgets.clear();
        refreshers.clear();

        int x = content.x() + 5;
        contentRight = content.right() - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int width = Math.max(20, contentRight - x);
        int y = content.y() + 4 - scrollY;

        y = actionRow(x, y, width);
        // 内置变量排在用户变量前面：它们是现成的，公式里直接写变量名就能用
        y = builtinRow(x, y, ExpressionScope.TIME_VARIABLE, EditorLang.t("variables.builtin_time"));
        y = builtinRow(x, y, ExpressionScope.PROGRESS_VARIABLE, EditorLang.t("variables.builtin_progress"));
        y = builtinRow(x, y, ExpressionScope.WORLD_TIME_VARIABLE, EditorLang.t("variables.builtin_world_time"));
        List<Variable> variables = context.animation().variables();

        if (variables.isEmpty()) {
            y = hintRow(x, y, EditorLang.t("variables.empty"), Draw.TEXT_DISABLED);
        }

        for (Variable variable : variables) {
            y = variableRow(x, y, variable);
        }

        y = hintRow(x, y, EditorLang.t("variables.name_hint"), Draw.TEXT_DISABLED);
        totalHeight = y + scrollY - content.y() + 8;
    }

    // region 行构建

    /// 顶部两个动作按钮：新增 / 删除选中
    private int actionRow(int x, int y, int width) {
        int cell = Math.max(1, (width - 2) / 2);
        ButtonWidget add = new ButtonWidget(new UiRect(x, y + 1, cell, FIELD_HEIGHT),
                EditorLang.t("variables.add"), this::addVariable);
        ButtonWidget remove = new ButtonWidget(new UiRect(x + cell + 2, y + 1, Math.max(1, contentRight - x - cell - 2), FIELD_HEIGHT),
                EditorLang.t("variables.remove"), this::removeSelected);
        widgets.add(add);
        widgets.add(remove);
        refreshers.add(() -> remove.enabled(selectedVariable() != null));
        return y + ROW_HEIGHT;
    }

    /// 一行只读文字：说明、空态提示
    private int hintRow(int x, int y, Component text, int color) {
        widgets.add(new LabelWidget(new UiRect(x, y + 1, Math.max(8, contentRight - x), FIELD_HEIGHT), text).color(color));
        return y + ROW_HEIGHT;
    }

    /// 一个内置变量：变量名 + 类型 + 取值，三列与变量行对齐（变量名对名字列、类型对来源列、取值对取值列）。
    /// 三样都改不了，所以整行只摆标签；取值随播放头每帧变，交给刷新器重写文本
    private int builtinRow(int x, int y, String name, Component type) {
        UiRect marker = new UiRect(x, y + 1, MARKER_WIDTH, FIELD_HEIGHT);
        int total = Math.max(0, contentRight - marker.right());
        int valueWidth = Math.min(VALUE_WIDTH, total / 3);
        int rest = Math.max(0, total - valueWidth - GAP * 2);
        int nameWidth = rest / 2;
        UiRect nameRect = new UiRect(marker.right(), y + 1, nameWidth, FIELD_HEIGHT);
        UiRect typeRect = new UiRect(nameRect.right() + GAP, y + 1, rest - nameWidth, FIELD_HEIGHT);
        UiRect valueRect = new UiRect(typeRect.right() + GAP, y + 1, valueWidth, FIELD_HEIGHT);

        widgets.add(new LabelWidget(marker, Component.literal(Icons.UNMARKED)).color(Draw.TEXT_DISABLED));
        // 三列都挂提示：整行都改不了，鼠标停在哪一列上都该知道为什么
        Component tip = EditorLang.t("variables.builtin_tooltip");
        widgets.add(new LabelWidget(nameRect, Component.literal(name)).tooltip(tip));
        widgets.add(new LabelWidget(typeRect, type).color(Draw.TEXT_DIM).tooltip(tip));
        LabelWidget value = new LabelWidget(valueRect, Component.empty()).tooltip(tip);
        widgets.add(value);
        refreshers.add(() -> value.text(Component.literal(Draw.num(builtinValue(name), VALUE_DECIMALS))));
        return y + ROW_HEIGHT;
    }

    /// 内置变量当前的取值
    private float builtinValue(String name) {
        Scope scope = context.scope();
        return switch (name) {
            case ExpressionScope.PROGRESS_VARIABLE -> scope.progress();
            case ExpressionScope.WORLD_TIME_VARIABLE -> scope.worldTime();
            default -> scope.time();
        };
    }

    /// 一个变量：标记 + 名字 + 取值来源 + 取值。
    ///
    /// 三列按比例分配、右沿首尾相接：取值列最多占三分之一（再封顶 {@link #VALUE_WIDTH}），
    /// 剩下的名字与取值来源各一半。面板被拖窄时三列一起收缩，
    /// 不会出现某一列顶着最小宽度把旁边那列盖掉的情况
    private int variableRow(int x, int y, Variable variable) {
        UiRect marker = new UiRect(x, y + 1, MARKER_WIDTH, FIELD_HEIGHT);
        int total = Math.max(0, contentRight - marker.right());
        int valueWidth = Math.min(VALUE_WIDTH, total / 3);
        int rest = Math.max(0, total - valueWidth - GAP * 2);
        int nameWidth = rest / 2;
        int bindingWidth = rest - nameWidth;
        UiRect nameRect = new UiRect(marker.right(), y + 1, nameWidth, FIELD_HEIGHT);
        UiRect binding = new UiRect(nameRect.right() + GAP, y + 1, bindingWidth, FIELD_HEIGHT);
        UiRect value = new UiRect(binding.right() + GAP, y + 1, valueWidth, FIELD_HEIGHT);

        // 选中标记：点它就是选中这一行，「删除变量」删的正是它
        LabelWidget mark = new LabelWidget(marker, Component.empty()).onClick(() -> selectedName = variable.name());
        widgets.add(mark);
        refreshers.add(() -> {
            boolean selected = variable.name().equals(selectedName);
            mark.text(Component.literal(selected ? Icons.MARKED : Icons.UNMARKED));
            mark.color(selected ? Draw.ACCENT : Draw.TEXT_DISABLED);
        });

        TextFieldWidget field = new TextFieldWidget(nameRect, variable.name(), name -> renameVariable(variable, name));
        field.maxLength(NAME_MAX_LENGTH);
        widgets.add(field);
        refreshers.add(() -> field.value(variable.name()));

        // 取值来源：点开三选一的菜单。行尾给一个展开箭头，提示这一格点得开
        LabelWidget source = new LabelWidget(binding, Component.empty()).field(true)
                .suffix(Component.literal(Icons.COLLAPSE));
        source.onClick(() -> openSourceMenu(variable, binding));
        widgets.add(source);
        refreshers.add(() -> {
            // 取不到值时公式原文与取值一起标红，一眼能看出是哪条公式出了问题
            source.text(Component.literal(sourceLabel(variable)));
            source.color(valueInvalid(variable) ? Draw.WARNING : Draw.TEXT);
        });

        // 固定值模式下取值列直接给一个可编辑的数值框；公式与轨道读数都是只读预览
        if (variable.source() instanceof ConstantValue) {
            NumberFieldWidget fixed = new NumberFieldWidget(value, constantOf(variable),
                    v -> variable.source(ValueSource.withConstant(variable.source(), v)));
            fixed.decimals(VALUE_DECIMALS);
            widgets.add(fixed);
            refreshers.add(() -> fixed.value(constantOf(variable)));
        } else {
            LabelWidget readout = new LabelWidget(value, Component.empty()).onClick(() -> selectedName = variable.name());
            widgets.add(readout);
            refreshers.add(() -> {
                float evaluated = variable.source().evaluate(context.scope());
                readout.text(Component.literal(Float.isNaN(evaluated) ? Icons.INVALID : Draw.num(evaluated, VALUE_DECIMALS)));
                readout.color(valueInvalid(variable) ? Draw.WARNING : Draw.TEXT);
                readout.tooltip(valueProblem(variable));
            });
        }

        return y + ROW_HEIGHT;
    }

    // endregion

    private @Nullable Variable selectedVariable() {
        return selectedName == null ? null : context.animation().variable(selectedName);
    }

    /// 值源携带的固定数值；不是有限值时按 0 处理
    private static float constantOf(Variable variable) {
        float value = variable.source().constant();
        return Float.isFinite(value) ? value : 0f;
    }

    /// 取值来源的显示文本：固定值、公式原文，或绑定的轨道名
    private String sourceLabel(Variable variable) {
        return switch (variable.source()) {
            case ConstantValue ignored -> EditorLang.t("variables.fixed").getString();
            case FormulaValue formula -> formula.expression();
            case TrackValue ignored -> {
                CurveTrack track = track(variable.trackId());
                yield track == null ? EditorLang.t("variables.track_missing").getString() : track.label().getString();
            }
        };
    }

    private @Nullable CurveTrack track(@Nullable String id) {
        if (id == null) {
            return null;
        }

        for (CurveTrack track : context.animation().curveTracks()) {
            if (track.id().equals(id)) {
                return track;
            }
        }

        return null;
    }

    private void addVariable() {
        for (int i = 1; i <= 999; i++) {
            Variable variable = context.animation().addVariable("var" + i);

            if (variable != null) {
                selectedName = variable.name();
                context.notify(EditorLang.t("notify.variable_added", variable.name()));
                return;
            }
        }
    }

    private void removeSelected() {
        Variable variable = selectedVariable();

        if (variable == null) {
            context.notify(EditorLang.t("notify.no_variable_selected"));
            return;
        }

        // 记下删除前的位置：删完之后这个下标正好落在"后一个"上
        int index = indexOf(context.animation().variables(), variable.name());

        if (!context.animation().removeVariable(variable.name())) {
            context.notify(EditorLang.t("notify.no_variable_selected"));
            return;
        }

        context.notify(EditorLang.t("notify.variable_removed", variable.name()));
        // 自动选中相邻的一个：原来是中间或开头就选后一个（原来的下标位置），
        // 原来是末尾就落到新的末尾，也就是前一个
        List<Variable> remaining = context.animation().variables();
        selectedName = remaining.isEmpty() ? null : remaining.get(Math.clamp(index, 0, remaining.size() - 1)).name();
    }

    private static int indexOf(List<Variable> variables, String name) {
        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i).name().equals(name)) {
                return i;
            }
        }

        return 0;
    }

    /// 改名：空名、重名或写不进公式的名字一律拒绝并提示，保持变化前的名字
    private void renameVariable(Variable variable, String name) {
        if (variable.name().equals(name)) {
            return;
        }

        if (!Expression.validName(name)) {
            context.notify(EditorLang.t("notify.variable_name_invalid", name));
            return;
        }

        // 内置变量在求值器里先被认出来，叫同一个名字的变量永远取不到，只能拦在改名这一步
        if (ExpressionScope.isBuiltin(name)) {
            context.notify(EditorLang.t("notify.variable_name_builtin", name));
            return;
        }

        if (!context.animation().renameVariable(variable.name(), name)) {
            context.notify(EditorLang.t("notify.variable_rename_failed", name));
            return;
        }

        if (variable.name().equals(selectedName)) {
            selectedName = name;
        }
    }

    /// 取值来源菜单：固定值 / 公式，加一个「绑定轨道」子菜单。打开菜单的同时把这个变量选上
    private void openSourceMenu(Variable variable, UiRect binding) {
        selectedName = variable.name();
        ContextMenu menu = new ContextMenu();
        menu.toggle("", EditorLang.t("variables.fixed"), () -> variable.source() instanceof ConstantValue,
                () -> variable.source(new ConstantValue(constantOf(variable))));
        menu.toggle(Icons.FORMULA, EditorLang.t("variables.formula"),
                () -> variable.source() instanceof FormulaValue, () -> openFormula(variable));
        List<CurveTrack> tracks = context.animation().curveTracks();

        if (!tracks.isEmpty()) {
            ContextMenu bindingMenu = new ContextMenu();

            for (CurveTrack track : tracks) {
                bindingMenu.toggle("", track.label(), () -> track.id().equals(variable.trackId()),
                        () -> variable.source(new TrackValue(track.id())));
            }

            menu.separator().submenu(Icons.TRACK, EditorLang.t("variables.bind_track"), bindingMenu);
        }

        openMenu(menu, binding.x(), binding.bottom() + 1);
    }

    /// 切到公式并打开编辑窗口；留空（或直接取消）就当没挂公式，仍旧用固定值
    private void openFormula(Variable variable) {
        float fallback = constantOf(variable);
        ValueSource current = variable.source();
        String expression = current instanceof FormulaValue formula ? formula.expression() : "";
        // 变量自己的公式不属于任何轨道，也不是函数体，所以既不给轨道上下文也不给参数栏：
        // 公式里写变量自己属于成环，由窗口的红字提示拦
        context.openExpressionEditor(EditorLang.t("variables.formula"), expression, null, null, value -> variable.source(
                value.isBlank() ? new ConstantValue(fallback) : new FormulaValue(value, fallback)));
    }

    /// 内容超出高度时在右侧绘制滚动条指示器：轨道底色 + 反映当前滚动位置的滑块
    private void renderScrollbar(GuiGraphicsExtractor graphics, UiRect content) {
        int maxScroll = Math.max(0, totalHeight - content.height());

        if (maxScroll <= 0) {
            return;
        }

        int trackX = content.right() - SCROLLBAR_MARGIN;
        int trackTop = content.y() + 1;
        int trackHeight = Math.max(1, content.height() - 2);
        int thumbHeight = Mth.clamp(Math.round((float) trackHeight * content.height() / totalHeight), 8, trackHeight);
        int thumbTop = trackTop + Math.round((float) (trackHeight - thumbHeight) * scrollY / maxScroll);
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + trackHeight, Draw.GRID);
        graphics.fill(trackX, thumbTop, trackX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, Draw.BORDER);
    }

    @Override
    protected boolean contentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiRect content = contentRect();
        int maxScroll = Math.max(0, totalHeight - content.height());
        int delta = (int) (scrollY * ROW_HEIGHT * 1.5);

        if (delta == 0 && scrollY != 0) {
            delta = scrollY > 0 ? ROW_HEIGHT : -ROW_HEIGHT;
        }

        this.scrollY = Mth.clamp(this.scrollY - delta, 0, maxScroll);
        rebuild(content);
        return true;
    }
}
