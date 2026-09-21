package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.PathMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.WeightedMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Path;
import cn.anecansaitin.free_camera_api_tripod.core.animation.PathNodec;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.Selected;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.NumberFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Inspector 面板：展示并编辑当前选中关键帧、路径节点以及动画整体信息。
public class InspectorPanel extends EditorPanel {
    private static final int ROW_HEIGHT = 15;
    private static final int FIELD_HEIGHT = 13;
    private static final int LABEL_WIDTH = 62;

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

    public InspectorPanel(EditorContext context) {
        super("inspector", EditorLang.t("panel.inspector"));
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
    }

    /// 选择与模式变化时重建控件；数值变化通过刷新器同步，避免打断输入
    private String revision() {
        CameraEditorModel model = context.editor();
        StringBuilder builder = new StringBuilder();
        builder.append(model.selectedTrackId()).append('|').append(model.selectedKeyIndex());

        TrackKey key = model.selectedKey();

        if (key instanceof Keyframe keyframe) {
            builder.append('|').append(keyframe.evaluateMode()).append('/').append(keyframe.weightedMode());
        }

        builder.append('|').append(model.selectedPathNode().index()).append('/').append(model.selectedPathNode().type());
        Path path = model.path();
        Selected selected = model.selectedPathNode();

        if (selected.index() >= 0 && selected.index() < path.size()) {
            PathNodec node = path.node(selected.index());
            builder.append('|').append(node.pathMode()).append('/').append(node.smooth());
        }

        builder.append('|').append(path.size());
        return builder.toString();
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        labels.clear();
        widgets.clear();
        refreshers.clear();

        int x = content.x() + 5;
        int fieldWidth = Math.max(40, content.width() - LABEL_WIDTH - 14);
        int y = content.y() + 4 - scrollY;
        contentRight = content.right() - 5;

        y = section(x, y, EditorLang.t("inspector.section.animation"));
        y = textRow(x, y, EditorLang.t("inspector.animation.name"), Component.literal(context.animation().name()));
        y = textRow(x, y, EditorLang.t("inspector.animation.duration"), Component.literal(Draw.num(context.duration(), 2) + "s"));
        y = textRow(x, y, EditorLang.t("inspector.animation.tracks"), context.info().trackSummary());
        y = textRow(x, y, EditorLang.t("inspector.animation.path"), context.info().pathSummary());

        AnimationTrack track = context.editor().selectedTrack();
        Keyframe key = context.editor().selectedKey() instanceof Keyframe keyframe ? keyframe : null;

        if (track != null && key != null) {
            y += 4;
            y = section(x, y, EditorLang.t("inspector.section.keyframe"));
            y = textRow(x, y, EditorLang.t("inspector.key.track"), track.label());
            y = fieldRow(x, y, fieldWidth, EditorLang.t("inspector.key.time"), key.time(), 3,
                    value -> key.time(context.snapTime(value)), key::time);
            y = fieldRow(x, y, fieldWidth, EditorLang.t("inspector.key.value"), key.value(), 3,
                    key::value, key::value);
            y = fieldRow(x, y, fieldWidth, EditorLang.t("inspector.key.in_tangent"), key.inTangent(), 3,
                    key::inTangent, key::inTangent);
            y = fieldRow(x, y, fieldWidth, EditorLang.t("inspector.key.out_tangent"), key.outTangent(), 3,
                    key::outTangent, key::outTangent);
            y = fieldRow(x, y, fieldWidth, EditorLang.t("inspector.key.in_weight"), key.inWeight(), 3,
                    key::inWeight, key::inWeight);
            y = fieldRow(x, y, fieldWidth, EditorLang.t("inspector.key.out_weight"), key.outWeight(), 3,
                    key::outWeight, key::outWeight);
            y = modeRow(x, y, fieldWidth, EditorLang.t("inspector.key.evaluate"), EvaluateMode.values(), key.evaluateMode(), key::evaluateMode);
            y = modeRow(x, y, fieldWidth, EditorLang.t("inspector.key.weighted"), WeightedMode.values(), key.weightedMode(), key::weightedMode);
        }

        y += 4;
        y = section(x, y, EditorLang.t("inspector.section.path"));
        y = pathSection(x, y, fieldWidth);

        totalHeight = y + scrollY - content.y() + 8;
    }

    private int pathSection(int x, int y, int fieldWidth) {
        Path path = context.editor().path();
        Selected selected = context.editor().selectedPathNode();

        y = buttonRow(x, y, fieldWidth,
                EditorLang.t("inspector.path.add_from_camera"), this::addPathNodeFromCamera,
                EditorLang.t("inspector.path.remove"), this::removeSelectedPathNode);

        if (selected.index() < 0 || selected.index() >= path.size()) {
            labels.add(new LabelDraw(EditorLang.t("inspector.path.empty"), x, y + 3, Draw.TEXT_DISABLED, Math.max(8, contentRight - x)));
            return y + ROW_HEIGHT;
        }

        PathNodec node = path.node(selected.index());
        y = textRow(x, y, EditorLang.t("inspector.path.node"), Component.literal("#" + selected.index() + " " + selected.type()));

        int index = selected.index();
        y = vectorRow(x, y, fieldWidth, EditorLang.t("inspector.path.position"), node.position().x(), node.position().y(), node.position().z(),
                (axis, value) -> setPathPosition(index, axis, value),
                () -> path.node(index).position());
        y = vectorRow(x, y, fieldWidth, EditorLang.t("inspector.path.in_tangent"), node.inTangent().x(), node.inTangent().y(), node.inTangent().z(),
                (axis, value) -> setPathTangent(index, true, axis, value),
                () -> path.node(index).inTangent());
        y = vectorRow(x, y, fieldWidth, EditorLang.t("inspector.path.out_tangent"), node.outTangent().x(), node.outTangent().y(), node.outTangent().z(),
                (axis, value) -> setPathTangent(index, false, axis, value),
                () -> path.node(index).outTangent());
        y = modeRow(x, y, fieldWidth, EditorLang.t("inspector.path.mode"), PathMode.values(), node.pathMode(),
                mode -> context.editor().updatePathNode(index, n -> n.pathMode(mode)));
        y = toggleRow(x, y, fieldWidth, EditorLang.t("inspector.path.smooth"), node.smooth(),
                value -> context.editor().updatePathNode(index, n -> n.smooth(value)));
        return y;
    }

    // region 行构建

    private void addLabel(Component text, int x, int y, int color, int maxWidth) {
        labels.add(new LabelDraw(text, x, y, color, maxWidth));
    }

    private int section(int x, int y, Component title) {
        addLabel(title, x, y + 2, Draw.ACCENT, -1);
        return y + ROW_HEIGHT;
    }

    private int textRow(int x, int y, Component label, Component value) {
        addLabel(label, x, y + 3, Draw.TEXT_DIM, -1);
        addLabel(value, x + LABEL_WIDTH, y + 3, Draw.TEXT, Math.max(8, contentRight - LABEL_WIDTH - x));
        return y + ROW_HEIGHT;
    }

    /// 数值行：取值经 getter 每帧同步，避免外部（如曲线图拖拽）改动后显示过期数据
    private int fieldRow(int x, int y, int width, Component label, float value, int decimals,
                         NumberFieldWidget.FloatSetter setter, FloatGetter getter) {
        addLabel(label, x, y + 3, Draw.TEXT_DIM, -1);
        NumberFieldWidget field = new NumberFieldWidget(new UiRect(x + LABEL_WIDTH, y + 1, width, FIELD_HEIGHT), value, setter);
        field.decimals(decimals);
        widgets.add(field);
        refreshers.add(() -> field.value(getter.get()));
        return y + ROW_HEIGHT;
    }

    private int vectorRow(int x, int y, int width, Component label, float x0, float y0, float z0, AxisSetter setter, VectorGetter getter) {
        addLabel(label, x, y + 3, Draw.TEXT_DIM, -1);
        int total = width - 4;
        int cell = Math.max(20, total / 3);
        String[] axes = {"X", "Y", "Z"};

        for (int axis = 0; axis < 3; axis++) {
            int cellX = x + LABEL_WIDTH + axis * (cell + 2);
            float initial = axis == 0 ? x0 : axis == 1 ? y0 : z0;
            int capturedAxis = axis;
            NumberFieldWidget field = new NumberFieldWidget(new UiRect(cellX, y + 1, cell, FIELD_HEIGHT), initial, value -> setter.set(capturedAxis, value));
            field.decimals(2);
            widgets.add(field);
            refreshers.add(() -> {
                Vector3f current = new Vector3f(getter.get());
                field.value(capturedAxis == 0 ? current.x : capturedAxis == 1 ? current.y : current.z);
            });
            labels.add(new LabelDraw(Component.literal(axes[axis]), cellX + 2, y + 3, Draw.TEXT_DISABLED, -1));
        }

        return y + ROW_HEIGHT;
    }

    private <T extends Enum<T>> int modeRow(int x, int y, int width, Component label, T[] values, T current, java.util.function.Consumer<T> setter) {
        addLabel(label, x, y + 3, Draw.TEXT_DIM, -1);
        int cell = Math.max(1, (width - (values.length - 1) * 2) / values.length);

        for (int i = 0; i < values.length; i++) {
            T value = values[i];
            ButtonWidget button = new ButtonWidget(new UiRect(x + LABEL_WIDTH + i * (cell + 2), y + 1, cell, FIELD_HEIGHT), modeLabel(value), () -> setter.accept(value));
            widgets.add(button);
            refreshers.add(() -> button.toggled(value == current));
        }

        return y + ROW_HEIGHT;
    }

    private int toggleRow(int x, int y, int width, Component label, boolean value, java.util.function.Consumer<Boolean> setter) {
        addLabel(label, x, y + 3, Draw.TEXT_DIM, -1);
        ButtonWidget button = new ButtonWidget(new UiRect(x + LABEL_WIDTH, y + 1, width, FIELD_HEIGHT), Component.empty(), () -> setter.accept(!value));
        button.toggled(value);
        widgets.add(button);
        refreshers.add(() -> {
            button.label(value ? EditorLang.t("common.on") : EditorLang.t("common.off"));
            button.toggled(value);
        });
        return y + ROW_HEIGHT;
    }

    private int buttonRow(int x, int y, int width, Component left, Runnable leftAction, Component right, Runnable rightAction) {
        int cell = Math.max(1, (width - 2) / 2);
        widgets.add(new ButtonWidget(new UiRect(x + LABEL_WIDTH, y + 1, cell, FIELD_HEIGHT), left, leftAction));
        widgets.add(new ButtonWidget(new UiRect(x + LABEL_WIDTH + cell + 2, y + 1, cell, FIELD_HEIGHT), right, rightAction));
        return y + ROW_HEIGHT;
    }

    private Component modeLabel(Object mode) {
        return switch (mode) {
            case EvaluateMode value -> EditorLang.t("mode.evaluate." + value.name().toLowerCase(java.util.Locale.ROOT));
            case WeightedMode value -> EditorLang.t("mode.weighted." + value.name().toLowerCase(java.util.Locale.ROOT));
            case PathMode value -> EditorLang.t("mode.path." + value.name().toLowerCase(java.util.Locale.ROOT));
            default -> Component.literal(mode.toString());
        };
    }

    // endregion

    // region 路径编辑

    private void addPathNodeFromCamera() {
        context.editor().addPathNodeAt(context.currentCameraPosition(), context.player().time());
        context.notify(EditorLang.t("notify.path_node_added"));
    }

    private void removeSelectedPathNode() {
        if (context.editor().removePathNode(context.editor().selectedPathNode().index())) {
            context.notify(EditorLang.t("notify.path_node_removed"));
        }
    }

    private void setPathPosition(int index, int axis, float value) {
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

    private void setPathTangent(int index, boolean in, int axis, float value) {
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

    @FunctionalInterface
    private interface AxisSetter {
        void set(int axis, float value);
    }

    @FunctionalInterface
    private interface FloatGetter {
        float get();
    }

    @FunctionalInterface
    private interface VectorGetter {
        Vector3fc get();
    }

    // endregion
}
