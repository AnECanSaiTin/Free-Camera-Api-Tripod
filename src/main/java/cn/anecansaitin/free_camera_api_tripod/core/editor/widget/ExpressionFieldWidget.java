package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// 数值输入框 + 模式切换按钮：默认数值模式（编辑固定数值），可切到动态模式挂一条公式。
///
/// 动态模式下数值框退化为**预览框**：左边是公式文本，右边是它按当前时刻算出的值；
/// 点击预览框即打开表达式编辑窗口。公式非法（或引用了不存在的变量）时数值显示为红色感叹号，
/// 求值失败本身不影响播放——求值链会回退到原来的固定数值。
///
/// 时间这类不允许变成动态的字段直接用 {@link NumberFieldWidget}，不经这里。
public class ExpressionFieldWidget extends EditorWidget {
    /// 右侧模式切换按钮的宽度
    private static final int MODE_BUTTON_WIDTH = 12;

    /// 字段的读写入口：固定数值与公式各一对
    public interface Accessor {
        /// 固定数值（数值模式下编辑它）
        float value();

        void value(float value);

        /// 挂着的公式；没挂返回 null
        @Nullable String expression();

        /// 挂公式；null 或空白表示回到固定数值
        void expression(@Nullable String expression);
    }

    private final EditorContext context;
    private final Component label;
    private final Accessor accessor;
    /// 数值模式的输入框；动态模式下既不绘制也不接收事件
    private final NumberFieldWidget field;
    private boolean dynamic;
    private int decimals = 3;

    public ExpressionFieldWidget(EditorContext context, UiRect rect, Component label, Accessor accessor) {
        super(rect);
        this.context = context;
        this.label = label;
        this.accessor = accessor;
        this.field = new NumberFieldWidget(fieldRect(), accessor.value(), accessor::value);
        this.dynamic = accessor.expression() != null;
    }

    public ExpressionFieldWidget decimals(int decimals) {
        this.decimals = decimals;
        this.field.decimals(decimals);
        return this;
    }

    /// 每帧同步一次：外部改了固定数值、或者读档换掉了公式，都要跟着变
    public void refresh() {
        boolean current = accessor.expression() != null;

        if (current != dynamic) {
            // 切走时把正在编辑的输入框提交掉，免得留在编辑态
            if (dynamic) {
                field.focused(false);
            }

            dynamic = current;
        }

        if (!dynamic) {
            field.value(accessor.value());
        }
    }

    /// 输入框占左边，右侧留给模式切换按钮
    private UiRect fieldRect() {
        return new UiRect(rect().x(), rect().y(), Math.max(1, rect().width() - MODE_BUTTON_WIDTH - 1), rect().height());
    }

    private UiRect modeButtonRect() {
        int width = Math.min(MODE_BUTTON_WIDTH, Math.max(1, rect().width()));
        return new UiRect(rect().right() - width, rect().y(), width, rect().height());
    }

    @Override
    public void focused(boolean focused) {
        // 焦点落到别处时让输入框把内容提交掉（与 NumberFieldWidget 的行为一致）
        if (!focused) {
            field.focused(false);
        }

        super.focused(focused);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (dynamic) {
            renderPreview(graphics, mouseX, mouseY);
        } else {
            field.updateHovered(mouseX, mouseY);
            field.render(graphics, mouseX, mouseY);
        }

        renderModeButton(graphics, mouseX, mouseY);
    }

    /// 预览框：公式文本 + 当前取值
    private void renderPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect area = fieldRect();
        String expression = accessor.expression();
        expression = expression == null ? "" : expression;
        float evaluated = context.evaluateExpression(expression);
        boolean valid = !Float.isNaN(evaluated);
        boolean hovered = area.contains(mouseX, mouseY);
        Draw.field(graphics, area, hovered);
        String value = valid ? Draw.num(evaluated, decimals) : Icons.INVALID;
        int valueWidth = Draw.font().width(value) + 4;
        Draw.textEllipsized(graphics, expression, area.x() + 3, area.centerY() - 4,
                Math.max(8, area.width() - 6 - valueWidth), valid ? Draw.TEXT : Draw.WARNING);
        Draw.textRight(graphics, value, area.right() - 2, area.centerY() - 4, valid ? Draw.ACCENT : Draw.WARNING);

        if (hovered) {
            graphics.setTooltipForNextFrame(Draw.font(), EditorLang.t("field.dynamic.tip"), mouseX, mouseY);
        }
    }

    private void renderModeButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect button = modeButtonRect();
        boolean hovered = button.contains(mouseX, mouseY);
        Draw.button(graphics, button, hovered, dynamic);
        Draw.textCentered(graphics, dynamic ? Icons.FORMULA : Icons.NUMBER, button.centerX(),
                button.centerY() - 4, dynamic ? Draw.TEXT : Draw.TEXT_DIM);

        if (hovered) {
            graphics.setTooltipForNextFrame(Draw.font(),
                    EditorLang.t(dynamic ? "field.mode.to_static" : "field.mode.to_dynamic"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active()) {
            return false;
        }

        if (modeButtonRect().contains(event.x(), event.y()) && event.button() == 0) {
            toggleMode();
            return true;
        }

        if (dynamic && fieldRect().contains(event.x(), event.y()) && event.button() == 0) {
            openEditor();
            return true;
        }

        // 数值模式下把点击转给内部输入框；控件本身没有矩形判定，这里先按矩形筛过
        return !dynamic && fieldRect().contains(event.x(), event.y()) && field.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return field.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return !dynamic && field.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return !dynamic && field.charTyped(event);
    }

    /// 切换数值 / 动态模式。
    ///
    /// 切到动态模式先用当前数值填一条公式，接着直接打开编辑窗口，省掉「切过去再点一下」；
    /// 切回数值模式会把公式清掉——「数值模式」的含义就是用这个固定数值，留着公式反而会被继续求值。
    private void toggleMode() {
        if (dynamic) {
            accessor.expression(null);
            dynamic = false;
            field.value(accessor.value());
            return;
        }

        float value = accessor.value();
        accessor.expression(Draw.num(value, decimals));
        dynamic = true;
        field.focused(false);
        openEditor();
    }

    private void openEditor() {
        context.openExpressionEditor(label, accessor.expression(), accessor::expression);
    }
}
