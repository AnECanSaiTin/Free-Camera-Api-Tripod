package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.DynamicField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathNodec;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.Selected;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.PathEditorScreen;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ExpressionFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// 路径面板：内容区分为两块——路径整体信息（绑定状态、节点数量、总长度、距离口径），
/// 以及当前选中节点的信息（节点下拉、位置、路径模式，贝塞尔模式下还有切线与自动平滑）。
///
/// 节点的增删改都在 {@link cn.anecansaitin.free_camera_api_tripod.core.editor.PathEditorScreen} 里进行。
public class PathNodePanel extends EditorPanel {
    public static final String ID = "path_node";

    /// 行高与行内控件高度：统一取面板基类的值，与其它面板、各栏按钮同高
    private static final int ROW_HEIGHT = EditorPanel.ROW_HEIGHT;
    private static final int FIELD_HEIGHT = EditorPanel.CONTROL_HEIGHT;
    private static final int LABEL_WIDTH = 62;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_MARGIN = 4;

    private final EditorContext context;
    private final List<LabelDraw> labels = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private @Nullable String lastRevision;
    private int scrollY;
    private int totalHeight;
    private int contentRight;
    /// 节点下拉选择器的矩形；没有选中节点时宽度为 0，既不绘制也不响应点击
    private UiRect nodeSelectorRect = new UiRect(0, 0, 0, 0);

    /// maxWidth 大于 0 时超出宽度会被省略号截断
    private record LabelDraw(Component text, int x, int y, int color, int maxWidth) {
    }

    public PathNodePanel(EditorContext context) {
        super(ID, EditorLang.t("panel.path"));
        this.context = context;
    }

    @Override
    protected void layoutWidgets(UiRect content) {
        rebuild(content);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, UiRect content, int mouseX, int mouseY) {
        Draw.canvas(graphics, content, Draw.CANVAS_BG);

        String revision = revision();

        if (!revision.equals(lastRevision)) {
            rebuild(content);
        }

        // 数值行每帧同步一次，外部（视口里的拖拽等）改动后显示不会过期
        for (Runnable refresher : refreshers) {
            refresher.run();
        }

        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());

        // 下拉选择器的框画在文字之下：悬停或候选列表展开时高亮，与输入框同一套外观
        if (nodeSelectorRect.width() > 0) {
            Draw.field(graphics, nodeSelectorRect,
                    contextMenu() != null || nodeSelectorRect.contains(mouseX, mouseY));
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

    /// 路径规模、选中节点、节点模式与距离口径变化时重建控件与摘要文本
    private String revision() {
        Path path = context.editor().path();
        Selected selected = context.editor().selectedPathNode();
        StringBuilder builder = new StringBuilder();
        builder.append(selected.index()).append('/').append(selected.type());
        builder.append('|').append(context.animation().motionMode()).append('|').append(context.distancePercent());

        if (selected.index() >= 0 && selected.index() < path.size()) {
            PathNodec node = path.node(selected.index());
            builder.append('|').append(node.pathMode()).append('/').append(node.smooth());
        }

        builder.append('|').append(path.size()).append('|').append(Draw.num((float) path.totalLength(), 2));
        // 路径名要进 revision，绑定或另存为之后面板上的名字才会跟着变
        builder.append('|').append(path.name());
        return builder.toString();
    }

    /// 是否已绑定路径：直接坐标模式下位置由坐标关键帧给出，此时视为未绑定
    private boolean bound() {
        return context.animation().motionMode() == CameraAnimation.MotionMode.PATH;
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        labels.clear();
        // 控件同样要清空：否则每次重建（滚动、数据变化）都会再叠一层按钮，出现重叠与点不中的问题
        widgets.clear();
        refreshers.clear();
        nodeSelectorRect = new UiRect(0, 0, 0, 0);

        int x = content.x() + 5;
        contentRight = content.right() - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int y = content.y() + 4 - scrollY;
        int fieldWidth = Math.max(30, contentRight - x - LABEL_WIDTH);

        // 区域一：路径整体信息（路径名、进入路径编辑的入口、规模与距离口径）
        y = pathSection(x, y);
        // 区域二：当前选中节点的信息
        y = nodeSection(x, y, fieldWidth);

        totalHeight = y + scrollY - content.y() + 8;
    }

    // region 区域构建

    /// 区域一：路径整体信息。未绑定路径时只说清状态，不再展示没有意义的摘要
    private int pathSection(int x, int y) {
        y = section(x, y, EditorLang.t("inspector.section.path"));

        if (!bound()) {
            labels.add(new LabelDraw(EditorLang.t("inspector.path.unbound"), x, y + 3, Draw.TEXT_DISABLED,
                    Math.max(8, contentRight - x)));
            return y + ROW_HEIGHT;
        }

        Path path = context.editor().path();
        y = openEditorRow(x, y);
        y = textRow(x, y, EditorLang.t("path_editor.name"), Component.literal(path.name()));
        y = textRow(x, y, EditorLang.t("inspector.path.count"), Component.literal(String.valueOf(path.size())));
        y = textRow(x, y, EditorLang.t("inspector.path.length"), Component.literal(Draw.num((float) path.totalLength(), 2)));
        return distanceModeRow(x, y);
    }

    /// 区域二：当前选中节点的信息；没有选中节点时只给一句提示
    private int nodeSection(int x, int y, int width) {
        y = section(x, y, EditorLang.t("inspector.section.node"));
        Path path = context.editor().path();
        int index = context.editor().selectedPathNode().index();

        if (index < 0 || index >= path.size()) {
            labels.add(new LabelDraw(EditorLang.t("inspector.path.empty"), x, y + 3, Draw.TEXT_DISABLED,
                    Math.max(8, contentRight - x)));
            return y + ROW_HEIGHT;
        }

        PathNodec node = path.node(index);
        y = nodeDropdownRow(x, y, index, node);
        y = distanceToNodeRow(x, y);
        y = vectorRow(x, y, width, EditorLang.t("inspector.path.position"),
                (axis, value) -> setPosition(index, axis, value), () -> path.node(index).position(),
                new DynamicField[]{DynamicField.NODE_X, DynamicField.NODE_Y, DynamicField.NODE_Z});
        y = modeRow(x, y, width, index, path);

        // 切线与自动平滑只对贝塞尔段有意义，其它模式下隐藏，避免摆一堆不起作用的输入框
        if (node.pathMode() == PathMode.BEZIER) {
            y = vectorRow(x, y, width, EditorLang.t("inspector.path.in_tangent"),
                    (axis, value) -> setTangent(index, true, axis, value), () -> path.node(index).inTangent(),
                    new DynamicField[]{DynamicField.NODE_IN_X, DynamicField.NODE_IN_Y, DynamicField.NODE_IN_Z});
            y = vectorRow(x, y, width, EditorLang.t("inspector.path.out_tangent"),
                    (axis, value) -> setTangent(index, false, axis, value), () -> path.node(index).outTangent(),
                    new DynamicField[]{DynamicField.NODE_OUT_X, DynamicField.NODE_OUT_Y, DynamicField.NODE_OUT_Z});
            y = smoothRow(x, y, width, index, path);
        }

        return y;
    }

    // endregion

    // region 行构建

    /// 节点行：下拉选择器显示当前节点的摘要（#下标 模式），点开列出路径里的全部节点供切换。
    ///
    /// 选择器是自绘的：值左对齐、展开箭头右对齐，整框铺满到内容区右边界。
    /// 候选列表复用面板的右键菜单（由屏幕统一绘制在最上层），所以列表不会被内容区裁剪
    private int nodeDropdownRow(int x, int y, int index, PathNodec node) {
        labels.add(new LabelDraw(EditorLang.t("inspector.path.node"), x, y + 3, Draw.TEXT_DIM, -1));
        int fieldX = x + LABEL_WIDTH;
        nodeSelectorRect = new UiRect(fieldX, y + 1, Math.max(1, contentRight - fieldX), FIELD_HEIGHT);
        labels.add(new LabelDraw(nodeLabel(index, node.pathMode()), fieldX + 4, y + 3, Draw.TEXT,
                Math.max(8, nodeSelectorRect.width() - 22)));
        labels.add(new LabelDraw(Component.literal(Icons.COLLAPSE), nodeSelectorRect.right() - 13, y + 3, Draw.TEXT_DIM, -1));
        return y + ROW_HEIGHT;
    }

    /// 距离显示形式：绝对距离 / 百分比，两个按钮各占标签右侧可用宽度的一半。
    ///
    /// 可用宽度从标签右沿算到内容区右边界，除不尽的 1 像素给右边那个，避免两格比例不同
    private int distanceModeRow(int x, int y) {
        labels.add(new LabelDraw(EditorLang.t("inspector.path.distance_mode"), x, y + 3, Draw.TEXT_DIM, -1));
        int left = x + LABEL_WIDTH;
        int total = Math.max(2, contentRight - left);
        int cell = Math.max(1, (total - 2) / 2);
        boolean percent = context.distancePercent();
        ButtonWidget absolute = new ButtonWidget(new UiRect(left, y + 1, cell, FIELD_HEIGHT),
                EditorLang.t("inspector.path.distance.absolute"), () -> setDistancePercent(false));
        int percentX = left + cell + 2;
        ButtonWidget percentage = new ButtonWidget(new UiRect(percentX, y + 1,
                Math.max(1, contentRight - percentX), FIELD_HEIGHT),
                EditorLang.t("inspector.path.distance.percent"), () -> setDistancePercent(true));
        absolute.toggled(!percent);
        percentage.toggled(percent);
        widgets.add(absolute);
        widgets.add(percentage);
        return y + ROW_HEIGHT;
    }

    private void setDistancePercent(boolean percent) {
        // 显示形式进了 revision，下一帧的重建会自己带上新读数
        context.distancePercent(percent);
    }

    /// 进入路径编辑界面的入口。只在绑定之后才出现：没绑定时这条路径既没有名字也没有文件，
    /// 进去编辑完也无处可存。
    ///
    /// 这一行没有左侧标签，按钮要一直铺到内容区右边界，与其它行的控件右端对齐
    private int openEditorRow(int x, int y) {
        ButtonWidget editor = new ButtonWidget(new UiRect(x, y + 1, Math.max(1, contentRight - x), FIELD_HEIGHT),
                EditorLang.t("inspector.path.open_editor"), this::openPathEditor);
        editor.accent(true);
        widgets.add(editor);
        return y + ROW_HEIGHT;
    }

    private void openPathEditor() {
        Minecraft.getInstance().setScreen(new PathEditorScreen(context));
    }

    private int section(int x, int y, Component title) {
        labels.add(new LabelDraw(title, x, y + 2, Draw.ACCENT, -1));
        return y + ROW_HEIGHT;
    }

    /// 起点沿路径走到该节点的弧长；第 0 个节点恒为 0
    private int distanceToNodeRow(int x, int y) {
        int index = context.editor().selectedPathNode().index();
        return textRow(x, y, EditorLang.t("inspector.path.node_distance"),
                Component.literal(Draw.num((float) context.editor().path().nodeDistance(index), 2)));
    }

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

    /// 自动平滑开关
    private int smoothRow(int x, int y, int width, int index, Path path) {
        labels.add(new LabelDraw(EditorLang.t("inspector.path.smooth"), x, y + 3, Draw.TEXT_DIM, -1));
        ButtonWidget button = new ButtonWidget(new UiRect(x + LABEL_WIDTH, y + 1, width - LABEL_WIDTH, FIELD_HEIGHT), Component.empty(),
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

    /// 节点摘要文本：「#下标 模式」
    private Component nodeLabel(int index, PathMode mode) {
        return EditorLang.t("path_editor.node_row", index, modeLabel(mode));
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

    // region 节点下拉

    /// 点开节点选择器：在它下方列出路径里的全部节点，当前节点带勾，点某一项即切换选中。
    /// 列表由基类持有，屏幕会在所有面板绘制完之后统一绘制并优先派发点击。
    private void openNodeSelector() {
        Path path = context.editor().path();
        int selectedIndex = context.editor().selectedPathNode().index();
        ContextMenu menu = new ContextMenu();

        for (int i = 0; i < path.size(); i++) {
            int index = i;
            menu.toggle("", nodeLabel(index, path.node(index).pathMode()), () -> index == selectedIndex, () -> selectNode(index));
        }

        openMenu(menu, nodeSelectorRect.x(), nodeSelectorRect.bottom() + 1);
    }

    /// 点在下拉选择器上就展开候选列表（左右键都一样），其它位置照常交给面板
    @Override
    protected boolean contentMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (nodeSelectorRect.width() > 0 && nodeSelectorRect.contains(event.x(), event.y())) {
            openNodeSelector();
            return true;
        }

        return false;
    }

    private void selectNode(int index) {
        context.editor().selectPathNode(new Selected(index, Selected.Type.NODE));
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
