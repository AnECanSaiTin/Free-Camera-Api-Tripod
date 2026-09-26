package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.DynamicField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathNodec;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ExpressionFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// 路径节点详情面板：编辑选中节点的位置、入/出切线、路径模式与自动平滑。
///
/// 数值行通过刷新器每帧同步，外部（视口里的拖拽等）改动后显示不会过期。
public class PathNodeDetailPanel extends EditorPanel {
    public static final String ID = "path_detail";

    /// 行高与行内控件高度：统一取面板基类的值，与其它面板、各栏按钮同高
    private static final int ROW_HEIGHT = EditorPanel.ROW_HEIGHT;
    private static final int FIELD_HEIGHT = EditorPanel.CONTROL_HEIGHT;
    private static final int LABEL_WIDTH = 56;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_MARGIN = 4;

    private final EditorContext context;
    private final List<LabelDraw> labels = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private @Nullable String lastRevision;
    private int scrollY;
    private int totalHeight;
    private int contentRight;

    /// maxWidth 大于 0 时超出宽度会被省略号截断
    private record LabelDraw(Component text, int x, int y, int color, int maxWidth) {
    }

    public PathNodeDetailPanel(EditorContext context) {
        super(ID, EditorLang.t("path_editor.detail"));
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

    /// 选中项与节点模式变化时重建控件；数值变化通过刷新器同步，避免打断输入
    private String revision() {
        Path path = context.editor().path();
        int index = context.editor().selectedPathNode().index();
        StringBuilder builder = new StringBuilder();
        builder.append(index).append('/').append(context.editor().selectedPathNode().type()).append('|').append(path.size());

        if (index >= 0 && index < path.size()) {
            PathNodec node = path.node(index);
            builder.append('|').append(node.pathMode()).append('/').append(node.smooth());
        }

        return builder.toString();
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        labels.clear();
        // 控件同样要清空，避免重建时不断叠加导致重叠与点不中
        widgets.clear();
        refreshers.clear();

        contentRight = content.right() - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int x = content.x() + 5;
        int y = content.y() + 4 - scrollY;
        int fieldWidth = Math.max(30, contentRight - x - LABEL_WIDTH);
        Path path = context.editor().path();
        int index = context.editor().selectedPathNode().index();

        if (index < 0 || index >= path.size()) {
            labels.add(new LabelDraw(EditorLang.t("path_editor.no_selection"), x, y + 3, Draw.TEXT_DISABLED,
                    Math.max(8, contentRight - x)));
            totalHeight = y + scrollY - content.y() + ROW_HEIGHT + 8;
            return;
        }

        PathNodec node = path.node(index);
        y = textRow(x, y, EditorLang.t("inspector.path.node"),
                Component.literal("#" + index + " " + context.editor().selectedPathNode().type()));
        y = textRow(x, y, EditorLang.t("inspector.path.node_distance"),
                Component.literal(Draw.num((float) path.nodeDistance(index), 2)));
        y = vectorRow(x, y, fieldWidth, EditorLang.t("inspector.path.position"),
                (axis, value) -> setPosition(index, axis, value), () -> path.node(index).position(),
                new DynamicField[]{DynamicField.NODE_X, DynamicField.NODE_Y, DynamicField.NODE_Z});
        y = modeRow(x, y, fieldWidth, index, path);

        // 切线与自动平滑只对贝塞尔段有意义，其它模式下隐藏，避免摆一堆不起作用的输入框
        if (node.pathMode() == PathMode.BEZIER) {
            y = vectorRow(x, y, fieldWidth, EditorLang.t("inspector.path.in_tangent"),
                    (axis, value) -> setTangent(index, true, axis, value), () -> path.node(index).inTangent(),
                    new DynamicField[]{DynamicField.NODE_IN_X, DynamicField.NODE_IN_Y, DynamicField.NODE_IN_Z});
            y = vectorRow(x, y, fieldWidth, EditorLang.t("inspector.path.out_tangent"),
                    (axis, value) -> setTangent(index, false, axis, value), () -> path.node(index).outTangent(),
                    new DynamicField[]{DynamicField.NODE_OUT_X, DynamicField.NODE_OUT_Y, DynamicField.NODE_OUT_Z});
            y = toggleRow(x, y, fieldWidth, index, path);
        }

        totalHeight = y + scrollY - content.y() + 8;
    }

    // region 行构建

    private int textRow(int x, int y, Component label, Component value) {
        labels.add(new LabelDraw(label, x, y + 3, Draw.TEXT_DIM, -1));
        labels.add(new LabelDraw(value, x + LABEL_WIDTH, y + 3, Draw.TEXT, Math.max(8, contentRight - LABEL_WIDTH - x)));
        return y + ROW_HEIGHT;
    }

    /// 三分量数值行：X / Y / Z 各一个输入框，右侧都带数值 / 动态模式切换按钮，
    /// 刷新器每帧从节点同步当前值。格间留 2 像素，最后一格吃掉取整余量，右边界与其它行严格对齐
    private int vectorRow(int x, int y, int width, Component label, AxisSetter setter,
                          VectorGetter getter, DynamicField[] fields) {
        labels.add(new LabelDraw(label, x, y + 3, Draw.TEXT_DIM, -1));
        int cell = Math.max(20, (width - 4) / 3);
        String[] axes = {"X", "Y", "Z"};

        for (int axis = 0; axis < 3; axis++) {
            int cellX = x + LABEL_WIDTH + axis * (cell + 2);
            int cellWidth = axis == 2 ? Math.max(1, contentRight - cellX) : cell;
            Component title = Component.empty().append(label).append(" " + axes[axis]);
            ExpressionFieldWidget field = new ExpressionFieldWidget(context,
                    new UiRect(cellX, y + 1, cellWidth, FIELD_HEIGHT), title,
                    nodeAccessor(context.editor().selectedPathNode().index(), axis, fields[axis], setter, getter));
            field.decimals(2);
            widgets.add(field);
            refreshers.add(field::refresh);
            labels.add(new LabelDraw(Component.literal(axes[axis]), cellX + 2, y + 3, Draw.TEXT_DISABLED, -1));
        }

        return y + ROW_HEIGHT;
    }

    /// 路径节点某个分量的读写入口：固定数值走面板原有的 getter / setter，公式存在节点自己身上
    private ExpressionFieldWidget.Accessor nodeAccessor(int index, int axis, DynamicField field,
                                                        AxisSetter setter, VectorGetter getter) {
        return new ExpressionFieldWidget.Accessor() {
            @Override
            public float value() {
                Vector3fc current = getter.get();
                return axis == 0 ? current.x() : axis == 1 ? current.y() : current.z();
            }

            @Override
            public void value(float value) {
                setter.set(axis, value);
            }

            @Override
            public @Nullable String expression() {
                Path path = context.editor().path();
                return index >= 0 && index < path.size() ? path.node(index).expressions().get(field) : null;
            }

            @Override
            public void expression(@Nullable String expression) {
                context.editor().updatePathNode(index, node -> node.expression(field, expression));
            }
        };
    }

    /// 路径模式：线性 / 贝塞尔 / 卡蒙罗姆三选一。最后一格吃掉余量，右边界与其它行对齐
    private int modeRow(int x, int y, int width, int index, Path path) {
        labels.add(new LabelDraw(EditorLang.t("inspector.path.mode"), x, y + 3, Draw.TEXT_DIM, -1));
        PathMode[] values = PathMode.values();
        int cell = Math.max(1, (width - (values.length - 1) * 2) / values.length);

        for (int i = 0; i < values.length; i++) {
            PathMode value = values[i];
            int cellX = x + LABEL_WIDTH + i * (cell + 2);
            int cellWidth = i == values.length - 1 ? Math.max(1, contentRight - cellX) : cell;
            ButtonWidget button = new ButtonWidget(new UiRect(cellX, y + 1, cellWidth, FIELD_HEIGHT),
                    modeLabel(value), () -> context.editor().updatePathNode(index, node -> node.pathMode(value)));
            widgets.add(button);
            refreshers.add(() -> button.toggled(path.node(index).pathMode() == value));
        }

        return y + ROW_HEIGHT;
    }

    private int toggleRow(int x, int y, int width, int index, Path path) {
        labels.add(new LabelDraw(EditorLang.t("inspector.path.smooth"), x, y + 3, Draw.TEXT_DIM, -1));
        ButtonWidget button = new ButtonWidget(new UiRect(x + LABEL_WIDTH, y + 1, width, FIELD_HEIGHT), Component.empty(),
                () -> context.editor().updatePathNode(index, node -> node.smooth(!node.smooth())));
        button.tooltip(EditorLang.t("inspector.path.smooth.tip"));
        widgets.add(button);
        refreshers.add(() -> {
            boolean smooth = path.node(index).smooth();
            button.label(smooth ? EditorLang.t("common.on") : EditorLang.t("common.off"));
            button.toggled(smooth);
        });
        return y + ROW_HEIGHT;
    }

    private Component modeLabel(PathMode mode) {
        return EditorLang.t("mode.path." + mode.name().toLowerCase(Locale.ROOT));
    }

    @FunctionalInterface
    private interface AxisSetter {
        void set(int axis, float value);
    }

    @FunctionalInterface
    private interface VectorGetter {
        Vector3fc get();
    }

    // endregion

    // region 路径编辑

    private void setPosition(int index, int axis, float value) {
        Path path = context.editor().path();

        if (index < 0 || index >= path.size()) {
            return;
        }

        Vector3f position = new Vector3f(path.node(index).position());

        switch (axis) {
            case 0 -> position.x = value;
            case 1 -> position.y = value;
            default -> position.z = value;
        }

        context.editor().updatePathNode(index, node -> node.position(position.x, position.y, position.z));
    }

    private void setTangent(int index, boolean in, int axis, float value) {
        Path path = context.editor().path();

        if (index < 0 || index >= path.size()) {
            return;
        }

        Vector3f tangent = new Vector3f(in ? path.node(index).inTangent() : path.node(index).outTangent());

        switch (axis) {
            case 0 -> tangent.x = value;
            case 1 -> tangent.y = value;
            default -> tangent.z = value;
        }

        context.editor().updatePathNode(index, node -> {
            if (in) {
                node.inTangent(tangent.x, tangent.y, tangent.z);
            } else {
                node.outTangent(tangent.x, tangent.y, tangent.z);
            }
        });
    }

    // endregion

    /// 内容超出高度时在右侧绘制滚动条指示器
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

        int updated = Mth.clamp(this.scrollY - delta, 0, maxScroll);

        if (updated != this.scrollY) {
            this.scrollY = updated;
            rebuild(content);
        }

        return true;
    }
}
