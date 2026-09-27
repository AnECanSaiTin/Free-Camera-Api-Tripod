package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Expression;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.ExpressionEditorWindow;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.LabelWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 函数面板：定义表达式里可以按名字调用的函数，与变量面板并列。
///
/// 每个函数一行：选中标记 + 名字 + 参数名 + 函数体，三列都是输入框：
/// - **名字**：要能被表达式识别成标识符（字母、下划线或非 ASCII 字符开头），也不能与内置函数重名
/// - **参数名**：用逗号分隔；函数体里这些名字就当变量写，实参在调用处求值后按顺序喂进来
/// - **函数体**：一段公式，可以引用变量与内置变量，也可以调用别的函数（含自定义函数）
///
/// 公式是按名字调用的，改完名字旧公式里的调用会取不到值，所以改名会提示一次。
/// 参数个数由列表长度决定，调用时个数不符会算不出值（退回固定数值），不会报错崩掉。
public class FunctionPanel extends EditorPanel {
    public static final String ID = "functions";

    private static final int ROW_HEIGHT = EditorPanel.ROW_HEIGHT;
    private static final int FIELD_HEIGHT = EditorPanel.CONTROL_HEIGHT;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_MARGIN = 4;
    /// 行首选中标记的宽度
    private static final int MARKER_WIDTH = 9;
    /// 行内三列之间的间隙
    private static final int GAP = 2;
    /// 行内名字列占行宽的比重，剩下的都给预览
    private static final float NAME_RATIO = 0.3f;
    /// 名字的长度上限
    private static final int NAME_MAX_LENGTH = 24;

    private final EditorContext context;
    private final List<Runnable> refreshers = new ArrayList<>();
    private @Nullable String selectedName;
    private @Nullable String lastRevision;
    private int scrollY;
    private int totalHeight;
    private int contentRight;

    public FunctionPanel(EditorContext context) {
        super(ID, EditorLang.t("panel.functions"));
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

        renderScrollbar(graphics, content);
    }

    /// 内容摘要：名字、参数与函数体都拼进来，任何一个改了都会触发重建
    private String revision() {
        StringBuilder builder = new StringBuilder();

        for (CustomFunction function : context.animation().functions()) {
            builder.append(function.name()).append('(').append(String.join(",", function.parameters()))
                    .append(')').append('=').append(function.body()).append('|');
        }

        return builder.toString();
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        widgets.clear();
        refreshers.clear();

        int x = content.x() + 5;
        contentRight = content.right() - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int width = Math.max(20, contentRight - x);
        int y = content.y() + 4 - scrollY;

        y = actionRow(x, y, width);
        List<CustomFunction> functions = context.animation().functions();

        if (functions.isEmpty()) {
            y = hintRow(x, y, EditorLang.t("functions.empty"));
        }

        for (CustomFunction function : functions) {
            y = functionRow(x, y, function);
        }

        // 提示分两行：一行名字规则、一行怎么改参数与函数体。挤成一行会被截断，看不到后半截
        y = hintRow(x, y, EditorLang.t("functions.hint_name"));
        y = hintRow(x, y, EditorLang.t("functions.hint_body"));
        totalHeight = y + scrollY - content.y();
    }

    /// 一行说明文字
    private int hintRow(int x, int y, Component text) {
        widgets.add(new LabelWidget(new UiRect(x, y + 1, Math.max(8, contentRight - x), FIELD_HEIGHT), text).color(Draw.TEXT_DIM));
        return y + ROW_HEIGHT;
    }

    // region 行构建

    /// 顶部两个动作按钮：新建 / 删除选中
    private int actionRow(int x, int y, int width) {
        int cell = Math.max(1, (width - 2) / 2);
        ButtonWidget add = new ButtonWidget(new UiRect(x, y + 1, cell, FIELD_HEIGHT),
                EditorLang.t("functions.add"), this::addFunction);
        ButtonWidget remove = new ButtonWidget(new UiRect(x + cell + 2, y + 1, Math.max(1, contentRight - x - cell - 2), FIELD_HEIGHT),
                EditorLang.t("functions.remove"), this::removeSelected);
        widgets.add(add);
        widgets.add(remove);
        refreshers.add(() -> remove.enabled(selectedFunction() != null));
        return y + ROW_HEIGHT;
    }

    /// 一个函数：标记 + 名字 + 预览。
    ///
    /// 参数与函数体都不在这一行直接改——它们要配着变量表与函数表才写得顺，
    /// 所以这一行只留名字，预览写全「参数 -> 函数体」，点预览打开表达式编辑窗口
    private int functionRow(int x, int y, CustomFunction function) {
        UiRect marker = new UiRect(x, y + 1, MARKER_WIDTH, FIELD_HEIGHT);
        int total = Math.max(0, contentRight - marker.right());
        int nameWidth = Math.max(20, Math.round(total * NAME_RATIO));
        UiRect nameRect = new UiRect(marker.right(), y + 1, nameWidth, FIELD_HEIGHT);
        UiRect previewRect = new UiRect(nameRect.right() + GAP, y + 1, Math.max(16, total - nameWidth - GAP), FIELD_HEIGHT);

        // 选中标记：点它就是选中这一行，「删除函数」删的正是它
        LabelWidget marker2 = new LabelWidget(marker, Component.empty()).onClick(() -> selectedName = function.name());
        widgets.add(marker2);
        refreshers.add(() -> {
            boolean selected = function.name().equals(selectedName);
            marker2.text(Component.literal(selected ? Icons.MARKED : Icons.UNMARKED));
            marker2.color(selected ? Draw.ACCENT : Draw.TEXT_DISABLED);
        });

        TextFieldWidget name = new TextFieldWidget(nameRect, function.name(), value -> renameFunction(function, value));
        name.maxLength(NAME_MAX_LENGTH);
        widgets.add(name);
        refreshers.add(() -> name.value(function.name()));

        // 预览：写全参数与函数体，点它选中这一行并打开编辑窗口；编译不过时转红
        LabelWidget preview = new LabelWidget(previewRect, Component.empty()).field(true)
                .tooltip(EditorLang.t("functions.preview_tip"));
        preview.onClick(() -> {
            selectedName = function.name();
            openBodyEditor(function);
        });
        widgets.add(preview);
        refreshers.add(() -> {
            preview.text(Component.literal(previewText(function)));
            preview.color(Expression.compile(function.body()) == null ? Draw.WARNING : Draw.TEXT);
        });

        return y + ROW_HEIGHT;
    }

    /// 预览文本形如 `(a, b) -> a + b`：参数也写出来，外部一行就能看全
    private static String previewText(CustomFunction function) {
        return "(" + String.join(", ", function.parameters()) + ") -> " + function.body();
    }

    // endregion

    /// 打开表达式编辑窗口改这个函数：参数与函数体都在窗口里改。
    /// 参数交给窗口当预览用的局部量（一律取 1），窗口也据此判断这个函数还能不能用
    private void openBodyEditor(CustomFunction function) {
        context.openExpressionEditor(EditorLang.t("functions.edit_title", function.name()), function.body(), null,
                new ExpressionEditorWindow.Parameters() {
                    @Override
                    public List<String> get() {
                        return function.parameters();
                    }

                    @Override
                    public void set(List<String> parameters) {
                        function.parameters(parameters);
                    }
                }, function::body);
    }

    private @Nullable CustomFunction selectedFunction() {
        return selectedName == null ? null : context.animation().function(selectedName);
    }

    private void addFunction() {
        CustomFunction function = context.animation().addFunction();

        if (function != null) {
            selectedName = function.name();
            context.notify(EditorLang.t("notify.function_added", function.name()));
        }
    }

    private void removeSelected() {
        CustomFunction function = selectedFunction();

        if (function == null) {
            context.notify(EditorLang.t("notify.no_function_selected"));
            return;
        }

        // 记下删除前的位置：删完之后这个下标正好落在"后一个"上
        List<CustomFunction> functions = context.animation().functions();
        int index = indexOf(functions, function.name());
        context.animation().removeFunction(function.name());
        List<CustomFunction> remaining = context.animation().functions();

        if (remaining.isEmpty()) {
            selectedName = null;
        } else {
            // 没有后一个就选前一个，与变量面板一致
            selectedName = remaining.get(Math.max(0, Math.min(index, remaining.size() - 1))).name();
        }
    }

    private void renameFunction(CustomFunction function, String newName) {
        String trimmed = newName == null ? "" : newName.strip();

        if (trimmed.equals(function.name())) {
            return;
        }

        if (!Expression.validName(trimmed)) {
            context.notify(EditorLang.t("notify.function_name_invalid", trimmed));
            return;
        }

        String previous = function.name();

        if (!context.animation().renameFunction(previous, trimmed)) {
            context.notify(EditorLang.t("notify.function_rename_failed", trimmed));
            return;
        }

        if (previous.equals(selectedName)) {
            selectedName = trimmed;
        }

        context.notify(EditorLang.t("notify.function_renamed", previous, trimmed));
    }

    private static int indexOf(List<CustomFunction> functions, String name) {
        for (int i = 0; i < functions.size(); i++) {
            if (functions.get(i).name().equals(name)) {
                return i;
            }
        }

        return -1;
    }

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
