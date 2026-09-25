package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.WeightedMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.NumberFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 关键帧面板：展示并编辑当前选中关键帧的属性，包含插值模式与贝塞尔控制点的对称设置。
public class KeyframePanel extends EditorPanel {
    public static final String ID = "keyframe";

    /// 行高与行内控件高度：统一取面板基类的值，与其它面板、各栏按钮同高
    private static final int ROW_HEIGHT = EditorPanel.ROW_HEIGHT;
    private static final int FIELD_HEIGHT = EditorPanel.CONTROL_HEIGHT;
    private static final int LABEL_WIDTH = 62;
    /// 一行两个字段时间隔的像素，以及半栏里标签的宽度
    private static final int PAIR_GAP = 6;
    private static final int HALF_LABEL_WIDTH = 34;
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

    public KeyframePanel(EditorContext context) {
        super(ID, EditorLang.t("panel.keyframe"));
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
        renderScrollbar(graphics, content);
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

        return builder.toString();
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        labels.clear();
        widgets.clear();
        refreshers.clear();

        int x = content.x() + 5;
        contentRight = content.right() - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int fieldWidth = Math.max(40, contentRight - x - LABEL_WIDTH);
        int y = content.y() + 4 - scrollY;

        AnimationTrack track = context.editor().selectedTrack();
        Keyframe key = context.editor().selectedKey() instanceof Keyframe keyframe ? keyframe : null;

        if (track == null || key == null) {
            labels.add(new LabelDraw(EditorLang.t("inspector.key.empty"), x, y + 3, Draw.TEXT_DISABLED, Math.max(8, contentRight - x)));
            totalHeight = y + scrollY - content.y() + ROW_HEIGHT + 8;
            return;
        }

        y = section(x, y, EditorLang.t("inspector.section.keyframe"));
        y = textRow(x, y, EditorLang.t("inspector.key.track"), track.label());
        // 数值类属性一行放两个，面板的垂直占位几乎减半
        int halfWidth = Math.max(30, (contentRight - x - PAIR_GAP) / 2);
        y = pairRow(x, y, halfWidth,
                new FieldSpec(EditorLang.t("inspector.key.time"), key.time(), 3,
                        value -> key.time(context.snapTime(value)), key::time),
                new FieldSpec(EditorLang.t("inspector.key.value"), key.value(), 3, key::value, key::value));
        y = pairRow(x, y, halfWidth,
                new FieldSpec(EditorLang.t("inspector.key.in_tangent"), key.inTangent(), 3, key::inTangent, key::inTangent),
                new FieldSpec(EditorLang.t("inspector.key.out_tangent"), key.outTangent(), 3, key::outTangent, key::outTangent));
        y = pairRow(x, y, halfWidth,
                new FieldSpec(EditorLang.t("inspector.key.in_weight"), key.inWeight(), 3, key::inWeight, key::inWeight),
                new FieldSpec(EditorLang.t("inspector.key.out_weight"), key.outWeight(), 3, key::outWeight, key::outWeight));
        y = modeRow(x, y, fieldWidth, EditorLang.t("inspector.key.evaluate"), EvaluateMode.values(), key.evaluateMode(), key::evaluateMode);
        y = modeRow(x, y, fieldWidth, EditorLang.t("inspector.key.weighted"), WeightedMode.values(), key.weightedMode(), key::weightedMode);

        // 只有贝塞尔曲线才会用到控制点，其余插值模式下该开关没有意义
        if (key.evaluateMode() == EvaluateMode.HERMITE) {
            y = symmetricRow(x, y, fieldWidth);
        }

        totalHeight = y + scrollY - content.y() + 8;
    }

    // region 行构建

    private int section(int x, int y, Component title) {
        labels.add(new LabelDraw(title, x, y + 2, Draw.ACCENT, -1));
        return y + ROW_HEIGHT;
    }

    private int textRow(int x, int y, Component label, Component value) {
        labels.add(new LabelDraw(label, x, y + 3, Draw.TEXT_DIM, -1));
        labels.add(new LabelDraw(value, x + LABEL_WIDTH, y + 3, Draw.TEXT, Math.max(8, contentRight - LABEL_WIDTH - x)));
        return y + ROW_HEIGHT;
    }

    /// 一行两个数值字段：左右各占一半，标签宽度按半栏收缩
    private int pairRow(int x, int y, int halfWidth, FieldSpec left, FieldSpec right) {
        fieldCell(x, y, halfWidth, left);
        int rightX = x + halfWidth + PAIR_GAP;
        fieldCell(rightX, y, Math.max(1, contentRight - rightX), right);
        return y + ROW_HEIGHT;
    }

    private void fieldCell(int x, int y, int width, FieldSpec spec) {
        int labelWidth = Math.clamp(width / 2, 12, HALF_LABEL_WIDTH);
        labels.add(new LabelDraw(spec.label(), x, y + 3, Draw.TEXT_DIM, Math.max(8, labelWidth - 2)));
        NumberFieldWidget field = new NumberFieldWidget(new UiRect(x + labelWidth, y + 1,
                Math.max(1, width - labelWidth), FIELD_HEIGHT), spec.value(), spec.setter());
        field.decimals(spec.decimals());
        widgets.add(field);
        refreshers.add(() -> field.value(spec.getter().get()));
    }

    /// 一行里左半边或右半边的一个数值字段
    private record FieldSpec(Component label, float value, int decimals,
                             NumberFieldWidget.FloatSetter setter, FloatGetter getter) {
    }

    /// 枚举二选一 / 多选一：最后一格吃掉取整余量，右边界与其它行严格对齐
    private <T extends Enum<T>> int modeRow(int x, int y, int width, Component label, T[] values, T current, java.util.function.Consumer<T> setter) {
        labels.add(new LabelDraw(label, x, y + 3, Draw.TEXT_DIM, -1));
        int cell = Math.max(1, (width - (values.length - 1) * 2) / values.length);

        for (int i = 0; i < values.length; i++) {
            T value = values[i];
            int cellX = x + LABEL_WIDTH + i * (cell + 2);
            int cellWidth = i == values.length - 1 ? Math.max(1, contentRight - cellX) : cell;
            ButtonWidget button = new ButtonWidget(new UiRect(cellX, y + 1, cellWidth, FIELD_HEIGHT), modeLabel(value), () -> setter.accept(value));
            widgets.add(button);
            refreshers.add(() -> button.toggled(value == current));
        }

        return y + ROW_HEIGHT;
    }

    /// 贝塞尔曲线的两侧控制点是否镜像对称；对称时曲线图里拖一侧另一侧会跟着动
    private int symmetricRow(int x, int y, int width) {
        labels.add(new LabelDraw(EditorLang.t("inspector.key.symmetric"), x, y + 3, Draw.TEXT_DIM, -1));
        ButtonWidget button = new ButtonWidget(new UiRect(x + LABEL_WIDTH, y + 1, width, FIELD_HEIGHT), Component.empty(),
                () -> context.bezierSymmetric(!context.bezierSymmetric()));
        widgets.add(button);
        refreshers.add(() -> {
            button.label(EditorLang.t(context.bezierSymmetric() ? "common.symmetric" : "common.asymmetric"));
            button.toggled(context.bezierSymmetric());
        });
        return y + ROW_HEIGHT;
    }

    private Component modeLabel(Object mode) {
        return switch (mode) {
            case EvaluateMode value -> EditorLang.t("mode.evaluate." + value.name().toLowerCase(java.util.Locale.ROOT));
            case WeightedMode value -> EditorLang.t("mode.weighted." + value.name().toLowerCase(java.util.Locale.ROOT));
            default -> Component.literal(mode.toString());
        };
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

    @FunctionalInterface
    private interface FloatGetter {
        float get();
    }

    // endregion

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
