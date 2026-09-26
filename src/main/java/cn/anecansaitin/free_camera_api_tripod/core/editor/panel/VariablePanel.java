package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Expression;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.NumberFieldWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 变量面板：定义表达式里可以按名字引用的变量。
///
/// 每个变量一行：选中标记 + 名字（可编辑，不限字符集，中文也行）+ 取值来源 + 取值。
/// 取值来源二选一：
/// - **绑定曲线轨道**：播放时取该轨道在当前时刻的读数，取值列是该读数的只读预览
/// - **固定值**：取值列就是一个可编辑的数值框，变量恒等于这个数
///
/// 名字要能被表达式识别成标识符（字母、下划线或非 ASCII 字符开头），改名与新建都会挡掉重名。
public class VariablePanel extends EditorPanel {
    public static final String ID = "variables";

    /// 行高与行内控件高度：统一取面板基类的值，与其它面板、各栏按钮同高
    private static final int ROW_HEIGHT = EditorPanel.ROW_HEIGHT;
    private static final int FIELD_HEIGHT = EditorPanel.CONTROL_HEIGHT;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_MARGIN = 4;
    /// 行首选中标记的宽度
    private static final int MARKER_WIDTH = 9;
    /// 行尾取值列占的宽度：固定值模式下这里放一个数值输入框
    private static final int VALUE_WIDTH = 58;
    /// 绑定选择器的最小宽度
    private static final int BINDING_MIN_WIDTH = 34;
    /// 变量名长度上限：名字要手写进公式里，太长没意义
    private static final int NAME_MAX_LENGTH = 24;
    /// 固定值的显示精度
    private static final int VALUE_DECIMALS = 3;

    private final EditorContext context;
    private final List<LabelDraw> labels = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<RowRef> rows = new ArrayList<>();
    private @Nullable String selectedName;
    private @Nullable String lastRevision;
    private int scrollY;
    private int totalHeight;
    private int contentRight;

    /// maxWidth 大于 0 时超出宽度会被省略号截断
    private record LabelDraw(Component text, int x, int y, int color, int maxWidth) {
    }

    /// 一行的可点区域；标记与取值是每帧现画的（选中状态与播放头都会变），所以只缓存矩形
    private record RowRef(Variable variable, String name, UiRect marker, UiRect binding, UiRect value) {
    }

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

        for (Runnable refresher : refreshers) {
            refresher.run();
        }

        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());

        // 选中标记与当前取值随选中状态、播放头实时变化，不能进标签缓存
        for (RowRef row : rows) {
            boolean selected = row.name().equals(selectedName);
            Draw.text(graphics, selected ? Icons.MARKED : Icons.UNMARKED, row.marker().x() + 1, row.marker().y() + 3,
                    selected ? Draw.ACCENT : Draw.TEXT_DISABLED);
            Draw.field(graphics, row.binding(), row.binding().contains(mouseX, mouseY));
            Draw.textEllipsized(graphics, bindingLabel(row.variable()), row.binding().x() + 3, row.binding().y() + 3,
                    Math.max(8, row.binding().width() - 16), Draw.TEXT);
            Draw.text(graphics, Icons.COLLAPSE, row.binding().right() - 10, row.binding().y() + 3, Draw.TEXT_DIM);

            // 固定值模式下取值列是个输入框，值由它自己显示，这里只画绑定模式的只读预览
            if (row.variable().bound()) {
                float value = context.evaluateExpression(row.name());
                // 自嵌套：取值来源的轨道又引用了这个变量，此时读的是轨道上的固定数值，与播放时不一致
                boolean selfReference = context.animation().selfReferencing(row.variable());
                int color = Draw.TEXT;

                if (selfReference) {
                    color = Draw.WARNING;
                } else if (Float.isNaN(value)) {
                    color = Draw.TEXT_DISABLED;
                }

                Draw.textEllipsized(graphics, Float.isNaN(value) ? Icons.INVALID : Draw.num(value, VALUE_DECIMALS),
                        row.value().x(), row.value().y() + 3, row.value().width(), color);

                if (selfReference && row.value().contains(mouseX, mouseY)) {
                    graphics.setTooltipForNextFrame(Draw.font(), EditorLang.t("variables.self_reference"), mouseX, mouseY);
                }
            }
        }

        for (LabelDraw label : labels) {
            if (label.maxWidth() > 0) {
                Draw.textEllipsized(graphics, label.text().getString(), label.x(), label.y(), label.maxWidth(), label.color());
            } else {
                Draw.text(graphics, label.text(), label.x(), label.y(), label.color());
            }
        }

        graphics.disableScissor();
        renderScrollbar(graphics, content);
    }

    /// 变量表与曲线轨道变化时重建；选中状态与绑定模式下的实时取值不进 revision，否则每帧都要重建。
    /// 绑定 / 固定值两种模式的取值列控件不同，所以「是否绑定」必须参与比较
    private String revision() {
        StringBuilder builder = new StringBuilder();

        for (Variable variable : context.animation().variables()) {
            builder.append(variable.name()).append(':').append(variable.trackId()).append('|');
        }

        builder.append('#');

        for (CurveTrack track : context.animation().curveTracks()) {
            builder.append(track.id()).append('|');
        }

        return builder.toString();
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        labels.clear();
        widgets.clear();
        refreshers.clear();
        rows.clear();

        int x = content.x() + 5;
        contentRight = content.right() - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int width = Math.max(20, contentRight - x);
        int y = content.y() + 4 - scrollY;

        y = actionRow(x, y, width);
        List<Variable> variables = context.animation().variables();

        if (variables.isEmpty()) {
            labels.add(new LabelDraw(EditorLang.t("variables.empty"), x, y + 3, Draw.TEXT_DISABLED, width));
            y += ROW_HEIGHT;
        }

        for (Variable variable : variables) {
            y = variableRow(x, y, variable);
        }

        y = hintRow(x, y);
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

    /// 一个变量：标记 + 名字 + 取值来源 + 取值
    private int variableRow(int x, int y, Variable variable) {
        UiRect marker = new UiRect(x, y + 1, MARKER_WIDTH, FIELD_HEIGHT);
        // 从右往左分配：取值固定宽度，剩下的一半给名字、一半给取值来源，保证不越出内容区
        int valueX = Math.max(marker.right() + 30, contentRight - VALUE_WIDTH);
        UiRect value = new UiRect(valueX, y + 1, Math.max(8, contentRight - valueX), FIELD_HEIGHT);
        int rest = Math.max(24, valueX - marker.right() - 4);
        int nameWidth = Math.max(24, rest / 2);
        UiRect nameRect = new UiRect(marker.right(), y + 1, nameWidth, FIELD_HEIGHT);
        UiRect binding = new UiRect(nameRect.right() + 2, y + 1, Math.max(BINDING_MIN_WIDTH, valueX - nameRect.right() - 4), FIELD_HEIGHT);
        TextFieldWidget field = new TextFieldWidget(nameRect, variable.name(), name -> renameVariable(variable, name));
        field.maxLength(NAME_MAX_LENGTH);
        widgets.add(field);
        refreshers.add(() -> field.value(variable.name()));

        // 没绑轨道就说明取值来源是固定值，取值列直接给一个可编辑的数值框
        if (!variable.bound()) {
            NumberFieldWidget fixed = new NumberFieldWidget(value, variable.value(), variable::value);
            fixed.decimals(VALUE_DECIMALS);
            widgets.add(fixed);
            refreshers.add(() -> fixed.value(variable.value()));
        }

        rows.add(new RowRef(variable, variable.name(), marker, binding, value));
        return y + ROW_HEIGHT;
    }

    /// 末尾的说明：内置变量、取值来源与命名规则
    private int hintRow(int x, int y) {
        labels.add(new LabelDraw(EditorLang.t("variables.hint"), x, y + 3, Draw.TEXT_DISABLED, Math.max(8, contentRight - x)));
        y += ROW_HEIGHT;
        labels.add(new LabelDraw(EditorLang.t("variables.binding_hint"), x, y + 3, Draw.TEXT_DISABLED, Math.max(8, contentRight - x)));
        y += ROW_HEIGHT;
        labels.add(new LabelDraw(EditorLang.t("variables.name_hint"), x, y + 3, Draw.TEXT_DISABLED, Math.max(8, contentRight - x)));
        return y + ROW_HEIGHT;
    }

    // endregion

    private @Nullable Variable selectedVariable() {
        return selectedName == null ? null : context.animation().variable(selectedName);
    }

    /// 取值来源的显示文本；未绑定就是固定值（取值列已是一个数值框），
    /// 绑定了但轨道已被删掉时按未绑定显示
    private String bindingLabel(Variable variable) {
        if (!variable.bound()) {
            return EditorLang.t("variables.fixed").getString();
        }

        for (CurveTrack track : context.animation().curveTracks()) {
            if (track.id().equals(variable.trackId())) {
                return track.label().getString();
            }
        }

        return EditorLang.t("variables.fixed").getString();
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

        if (variable == null || !context.animation().removeVariable(variable.name())) {
            context.notify(EditorLang.t("notify.no_variable_selected"));
            return;
        }

        context.notify(EditorLang.t("notify.variable_removed", variable.name()));
        selectedName = null;
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

        if (!context.animation().renameVariable(variable.name(), name)) {
            context.notify(EditorLang.t("notify.variable_rename_failed", name));
            return;
        }

        if (variable.name().equals(selectedName)) {
            selectedName = name;
        }
    }

    /// 取值来源菜单：一个「固定值」加全部曲线轨道
    private void openBindingMenu(RowRef row) {
        select(row);
        ContextMenu menu = new ContextMenu();
        Variable variable = row.variable();
        menu.toggle("", EditorLang.t("variables.fixed"), () -> !variable.bound(),
                () -> variable.trackId(""));

        for (CurveTrack track : context.animation().curveTracks()) {
            menu.toggle("", track.label(), () -> track.id().equals(variable.trackId()),
                    () -> variable.trackId(track.id()));
        }

        openMenu(menu, row.binding().x(), row.binding().bottom() + 1);
    }

    private void select(RowRef row) {
        selectedName = row.name();
    }

    @Override
    protected boolean contentMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (RowRef row : rows) {
            if (row.marker().contains(event.x(), event.y()) || row.value().contains(event.x(), event.y())) {
                select(row);
                return true;
            }

            if (row.binding().contains(event.x(), event.y())) {
                openBindingMenu(row);
                return true;
            }
        }

        return false;
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
