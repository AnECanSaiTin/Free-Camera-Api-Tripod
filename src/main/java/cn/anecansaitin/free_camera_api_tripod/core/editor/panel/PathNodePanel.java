package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathNodec;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.Selected;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.PathEditorScreen;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.NumberFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    private static final int ROW_HEIGHT = 15;
    private static final int FIELD_HEIGHT = 13;
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

        int x = content.x() + 5;
        contentRight = content.right() - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int y = content.y() + 4 - scrollY;
        int fieldWidth = Math.max(30, contentRight - x - LABEL_WIDTH);

        // 路径节点的增删改都在独立界面里，这里放进入口与绑定入口；与是否选中节点无关，始终可用
        y = actionRow(x, y, contentRight - x);
        // 区域一：路径整体信息
        y = pathSection(x, y, fieldWidth);
        // 区域二：当前选中节点的信息
        y = nodeSection(x, y, fieldWidth);

        totalHeight = y + scrollY - content.y() + 8;
    }

    // region 区域构建

    /// 区域一：路径整体信息。未绑定路径时只说清状态，不再展示没有意义的摘要
    private int pathSection(int x, int y, int width) {
        y = section(x, y, EditorLang.t("inspector.section.path"));

        if (!bound()) {
            labels.add(new LabelDraw(EditorLang.t("inspector.path.unbound"), x, y + 3, Draw.TEXT_DISABLED,
                    Math.max(8, contentRight - x)));
            return y + ROW_HEIGHT;
        }

        Path path = context.editor().path();
        y = textRow(x, y, EditorLang.t("inspector.path.count"), Component.literal(String.valueOf(path.size())));
        y = textRow(x, y, EditorLang.t("inspector.path.length"), Component.literal(Draw.num((float) path.totalLength(), 2)));
        return distanceModeRow(x, y, width);
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
        y = nodeDropdownRow(x, y, width, index, node);
        y = vectorRow(x, y, width, EditorLang.t("inspector.path.position"), node.position(),
                (axis, value) -> setPosition(index, axis, value), () -> path.node(index).position());
        y = modeRow(x, y, width, index, path);

        // 切线与自动平滑只对贝塞尔段有意义，其它模式下隐藏，避免摆一堆不起作用的输入框
        if (node.pathMode() == PathMode.BEZIER) {
            y = vectorRow(x, y, width, EditorLang.t("inspector.path.in_tangent"), node.inTangent(),
                    (axis, value) -> setTangent(index, true, axis, value), () -> path.node(index).inTangent());
            y = vectorRow(x, y, width, EditorLang.t("inspector.path.out_tangent"), node.outTangent(),
                    (axis, value) -> setTangent(index, false, axis, value), () -> path.node(index).outTangent());
            y = smoothRow(x, y, width, index, path);
        }

        return y;
    }

    // endregion

    // region 行构建

    /// 节点行：下拉按钮显示当前节点摘要，点开后列出路径里的全部节点供切换
    private int nodeDropdownRow(int x, int y, int width, int index, PathNodec node) {
        labels.add(new LabelDraw(EditorLang.t("inspector.path.node"), x, y + 3, Draw.TEXT_DIM, -1));
        int buttonX = x + LABEL_WIDTH;
        int buttonWidth = Math.max(1, width - LABEL_WIDTH);
        ButtonWidget dropdown = new ButtonWidget(new UiRect(buttonX, y + 1, buttonWidth, FIELD_HEIGHT),
                nodeLabel(index, node.pathMode()), () -> openNodeMenu(buttonX, y + FIELD_HEIGHT + 2));
        widgets.add(dropdown);
        return y + ROW_HEIGHT;
    }

    /// 距离显示形式：绝对距离 / 百分比
    private int distanceModeRow(int x, int y, int width) {
        labels.add(new LabelDraw(EditorLang.t("inspector.path.distance_mode"), x, y + 3, Draw.TEXT_DIM, -1));
        int cell = Math.max(1, (width - LABEL_WIDTH - 2) / 2);
        boolean percent = context.distancePercent();
        ButtonWidget absolute = new ButtonWidget(new UiRect(x + LABEL_WIDTH, y + 1, cell, FIELD_HEIGHT),
                EditorLang.t("inspector.path.distance.absolute"), () -> setDistancePercent(false));
        ButtonWidget percentage = new ButtonWidget(new UiRect(x + LABEL_WIDTH + cell + 2, y + 1, cell, FIELD_HEIGHT),
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

    /// 进入路径编辑界面；未绑定路径时先给一条空路径，进去就能直接取点
    private int actionRow(int x, int y, int width) {
        int gap = 3;
        int bindWidth = Math.min(64, Math.max(28, width / 3));
        int editorWidth = Math.max(1, width - bindWidth - gap);

        ButtonWidget editor = new ButtonWidget(new UiRect(x, y + 1, editorWidth, FIELD_HEIGHT),
                EditorLang.t("inspector.path.open_editor"), this::openPathEditor);
        editor.accent(true);
        widgets.add(editor);

        ButtonWidget bind = new ButtonWidget(new UiRect(x + editorWidth + gap, y + 1, bindWidth, FIELD_HEIGHT),
                EditorLang.t("inspector.path.bind"), context::chooseAndBindPath);
        bind.tooltip(EditorLang.t("inspector.path.bind.tip"));
        widgets.add(bind);
        return y + ROW_HEIGHT;
    }

    private void openPathEditor() {
        Path path = context.editor().path();

        // 未绑定且路径为空时给一条干净的新路径，进去就能直接取点
        if (!bound() && path.size() == 0) {
            path.clear();
            context.editor().pathReplaced();
        }

        Minecraft.getInstance().setScreen(new PathEditorScreen(context));
    }

    private int section(int x, int y, Component title) {
        labels.add(new LabelDraw(title, x, y + 2, Draw.ACCENT, -1));
        return y + ROW_HEIGHT;
    }

    private int textRow(int x, int y, Component label, Component value) {
        labels.add(new LabelDraw(label, x, y + 3, Draw.TEXT_DIM, -1));
        labels.add(new LabelDraw(value, x + LABEL_WIDTH, y + 3, Draw.TEXT, Math.max(8, contentRight - LABEL_WIDTH - x)));
        return y + ROW_HEIGHT;
    }

    /// 三分量数值行：X / Y / Z 各一个输入框，刷新器每帧从节点同步当前值
    private int vectorRow(int x, int y, int width, Component label, Vector3fc value, AxisSetter setter, VectorGetter getter) {
        labels.add(new LabelDraw(label, x, y + 3, Draw.TEXT_DIM, -1));
        int total = width - 4;
        int cell = Math.max(20, total / 3);
        String[] axes = {"X", "Y", "Z"};

        for (int axis = 0; axis < 3; axis++) {
            int cellX = x + LABEL_WIDTH + axis * (cell + 2);
            float initial = axis == 0 ? value.x() : axis == 1 ? value.y() : value.z();
            int capturedAxis = axis;
            NumberFieldWidget field = new NumberFieldWidget(new UiRect(cellX, y + 1, cell, FIELD_HEIGHT), initial, v -> setter.set(capturedAxis, v));
            field.decimals(2);
            widgets.add(field);
            refreshers.add(() -> {
                Vector3fc current = getter.get();
                field.value(capturedAxis == 0 ? current.x() : capturedAxis == 1 ? current.y() : current.z());
            });
            labels.add(new LabelDraw(Component.literal(axes[axis]), cellX + 2, y + 3, Draw.TEXT_DISABLED, -1));
        }

        return y + ROW_HEIGHT;
    }

    /// 路径模式：线性 / 贝塞尔 / 卡蒙罗姆三选一
    private int modeRow(int x, int y, int width, int index, Path path) {
        labels.add(new LabelDraw(EditorLang.t("inspector.path.mode"), x, y + 3, Draw.TEXT_DIM, -1));
        PathMode[] values = PathMode.values();
        int cell = Math.max(1, (width - (values.length - 1) * 2) / values.length);

        for (int i = 0; i < values.length; i++) {
            PathMode value = values[i];
            ButtonWidget button = new ButtonWidget(new UiRect(x + LABEL_WIDTH + i * (cell + 2), y + 1, cell, FIELD_HEIGHT),
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

    /// 在按钮下方展开节点菜单：列出路径里的全部节点，点某一项即切换选中节点。
    /// 菜单由基类持有，屏幕会在所有面板绘制完之后统一绘制并优先派发点击。
    private void openNodeMenu(int anchorX, int anchorY) {
        Path path = context.editor().path();
        int selectedIndex = context.editor().selectedPathNode().index();
        ContextMenu menu = new ContextMenu();

        for (int i = 0; i < path.size(); i++) {
            int index = i;
            menu.toggle("", nodeLabel(index, path.node(index).pathMode()), () -> index == selectedIndex, () -> selectNode(index));
        }

        openMenu(menu, anchorX, anchorY);
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
