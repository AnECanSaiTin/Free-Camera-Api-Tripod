package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.core.animation.track.CommandTrack;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ExpressionFieldWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.LabelWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.NumberFieldWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    /// 指令文本的输入上限：原版命令最长也就百来字符，留出余量
    private static final int COMMAND_MAX_LENGTH = 256;

    private final EditorContext context;
    private final List<Runnable> refreshers = new ArrayList<>();
    private @Nullable String lastRevision;
    /// 当前正在查看的轨道 id：面板里的公式字段都挂在它上面，表达式窗口靠它判断自嵌套
    private @Nullable String inspectedTrack;
    private int scrollY;
    private int totalHeight;
    private int contentRight;

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

        renderScrollbar(graphics, content);
    }

    /// 选择与模式变化时重建控件；数值变化通过刷新器同步，避免打断输入
    private String revision() {
        CameraEditorModel model = context.editor();
        StringBuilder builder = new StringBuilder();
        builder.append(model.selectedTrackId()).append('|').append(model.selectedKeyIndex());
        TrackKey key = model.selectedKey();

        if (key instanceof Keyframe keyframe) {
            builder.append('|').append(keyframe.evaluateMode());
        }

        return builder.toString();
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        widgets.clear();
        refreshers.clear();

        int x = content.x() + 5;
        contentRight = content.right() - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int fieldWidth = Math.max(40, contentRight - x - LABEL_WIDTH);
        int y = content.y() + 4 - scrollY;

        AnimationTrack track = context.editor().selectedTrack();
        TrackKey selected = context.editor().selectedKey();
        Keyframe key = selected instanceof Keyframe keyframe ? keyframe : null;
        inspectedTrack = track == null ? null : track.id();

        if (track == null || selected == null) {
            widgets.add(new LabelWidget(new UiRect(x, y, Math.max(8, contentRight - x), FIELD_HEIGHT),
                    EditorLang.t("inspector.key.empty")).color(Draw.TEXT_DISABLED));
            totalHeight = y + scrollY - content.y() + ROW_HEIGHT + 8;
            return;
        }

        // 曲线以外的键没有切线、权重这些参数，只显示时间与轨道自己的内容
        if (key == null) {
            rebuildKeyContent(track, content, x, y, fieldWidth);
            return;
        }

        y = section(x, y, EditorLang.t("inspector.section.keyframe"));
        y = textRow(x, y, EditorLang.t("inspector.key.track"), track.label());
        // 数值类属性一行放两个，面板的垂直占位几乎减半。
        // 时间是唯一不允许变成动态的字段：动态公式要按时间求值，时间本身再挂公式只会绕回自己
        int halfWidth = Math.max(30, (contentRight - x - PAIR_GAP) / 2);
        y = pairRow(x, y, halfWidth,
                new FieldSpec(EditorLang.t("inspector.key.time"), key.time(), 3,
                        value -> key.time(context.snapTime(value)), key::time, null),
                new FieldSpec(EditorLang.t("inspector.key.value"), key.value(), 3, key::value, key::value,
                        keySource(key::valueSource, key::valueSource)));
        y = modeRow(x, y, fieldWidth, EditorLang.t("inspector.key.evaluate"), EvaluateMode.values(), key.evaluateMode(), key::evaluateMode);

        // 两条曲柄与控制点对称都只服务于贝塞尔（HERMITE）插值：其余模式下求值根本不读这些数，
        // 摆着只会让人以为改了有用，所以整组一起跟着插值模式出现或消失
        if (key.evaluateMode() == EvaluateMode.HERMITE) {
            y = pairRow(x, y, halfWidth,
                    new FieldSpec(EditorLang.t("inspector.key.in_slope"), key.inSlope(), 3, key::inSlope, key::inSlope,
                            keySource(key::inSlopeSource, key::inSlopeSource)),
                    new FieldSpec(EditorLang.t("inspector.key.out_slope"), key.outSlope(), 3, key::outSlope, key::outSlope,
                            keySource(key::outSlopeSource, key::outSlopeSource)));
            y = pairRow(x, y, halfWidth,
                    new FieldSpec(EditorLang.t("inspector.key.in_length"), key.inLength(), 3, key::inLength, key::inLength,
                            keySource(key::inLengthSource, key::inLengthSource)),
                    new FieldSpec(EditorLang.t("inspector.key.out_length"), key.outLength(), 3, key::outLength, key::outLength,
                            keySource(key::outLengthSource, key::outLengthSource)));
            y = symmetricRow(x, y, fieldWidth);
        }

        totalHeight = y + scrollY - content.y() + 8;
    }

    /// 非曲线键的面板内容：时间 + 轨道自带的内容。
    /// 内容编辑按轨道类型分派，不认识的类型只显示时间，不影响时间轴上的其它操作
    private void rebuildKeyContent(AnimationTrack track, UiRect content, int x, int y, int width) {
        y = section(x, y, EditorLang.t("inspector.section.keyframe"));
        y = textRow(x, y, EditorLang.t("inspector.key.track"), track.label());
        y = timeRow(x, y, width, track);

        if (track instanceof CommandTrack commandTrack) {
            y = commandRow(x, y, width, commandTrack, context.editor().selectedKeyIndex());
        }

        totalHeight = y + scrollY - content.y() + 8;
    }

    /// 非曲线键的时间：键是不可变记录，只能让轨道把它挪到新时间；
    /// 移动后索引可能变化，所以取值一律现查当前选中的键
    private int timeRow(int x, int y, int width, AnimationTrack track) {
        widgets.add(new LabelWidget(new UiRect(x, y, Math.max(8, contentRight - x), FIELD_HEIGHT),
                EditorLang.t("inspector.key.time")).color(Draw.TEXT_DIM));
        NumberFieldWidget field = new NumberFieldWidget(new UiRect(x + LABEL_WIDTH, y + 1, Math.max(1, width - LABEL_WIDTH), FIELD_HEIGHT),
                selectedKeyTime(), value -> context.editor().moveKey(track, context.editor().selectedKeyIndex(), context.snapTime(value)));
        field.decimals(3);
        widgets.add(field);
        refreshers.add(() -> field.value(selectedKeyTime()));
        return y + ROW_HEIGHT;
    }

    /// 指令内容：回车或失焦时提交，由命令轨道自己规范化（去掉前导斜杠）后写回
    private int commandRow(int x, int y, int width, CommandTrack track, int index) {
        widgets.add(new LabelWidget(new UiRect(x, y, Math.max(8, contentRight - x), FIELD_HEIGHT),
                EditorLang.t("inspector.key.command")).color(Draw.TEXT_DIM));
        TextFieldWidget field = new TextFieldWidget(new UiRect(x + LABEL_WIDTH, y + 1, Math.max(1, width - LABEL_WIDTH), FIELD_HEIGHT),
                commandText(track, index), value -> track.command(index, value));
        field.maxLength(COMMAND_MAX_LENGTH);
        widgets.add(field);
        refreshers.add(() -> field.value(commandText(track, index)));
        return y + ROW_HEIGHT;
    }

    private float selectedKeyTime() {
        TrackKey key = context.editor().selectedKey();
        return key == null ? 0f : key.time();
    }

    private static String commandText(CommandTrack track, int index) {
        String command = track.command(index);
        return command == null ? "" : command;
    }

    // region 行构建

    private int section(int x, int y, Component title) {
        widgets.add(new LabelWidget(new UiRect(x, y - 1, Math.max(8, contentRight - x), FIELD_HEIGHT), title).color(Draw.ACCENT));
        return y + ROW_HEIGHT;
    }

    private int textRow(int x, int y, Component label, Component value) {
        widgets.add(new LabelWidget(new UiRect(x, y, Math.max(8, contentRight - x), FIELD_HEIGHT), label).color(Draw.TEXT_DIM));
        widgets.add(new LabelWidget(new UiRect(x + LABEL_WIDTH, y, Math.max(8, contentRight - LABEL_WIDTH - x), FIELD_HEIGHT), value)
                .color(Draw.TEXT));
        return y + ROW_HEIGHT;
    }

    /// 一行两个数值字段：左右各占一半，标签宽度按半栏收缩
    private int pairRow(int x, int y, int halfWidth, FieldSpec left, FieldSpec right) {
        fieldCell(x, y, halfWidth, left);
        int rightX = x + halfWidth + PAIR_GAP;
        fieldCell(rightX, y, Math.max(1, contentRight - rightX), right);
        return y + ROW_HEIGHT;
    }

    /// 一个数值字段。带值源入口的用 {@link ExpressionFieldWidget}（右侧带模式切换按钮），
    /// 没有的（时间是唯一一个）就是普通的数值输入框
    private void fieldCell(int x, int y, int width, FieldSpec spec) {
        int labelWidth = Math.clamp(width / 2, 12, HALF_LABEL_WIDTH);
        widgets.add(new LabelWidget(new UiRect(x, y, Math.max(8, labelWidth - 2), FIELD_HEIGHT), spec.label()).color(Draw.TEXT_DIM));
        UiRect rect = new UiRect(x + labelWidth, y + 1, Math.max(1, width - labelWidth), FIELD_HEIGHT);

        if (spec.dynamic() == null) {
            NumberFieldWidget field = new NumberFieldWidget(rect, spec.value(), spec.setter());
            field.decimals(spec.decimals());
            widgets.add(field);
            refreshers.add(() -> field.value(spec.getter().get()));
            return;
        }

        ExpressionFieldWidget field = new ExpressionFieldWidget(context, rect, spec.label(), spec.dynamic()).track(inspectedTrack);
        field.decimals(spec.decimals());
        widgets.add(field);
        refreshers.add(field::refresh);
    }

    /// 一条数值字段的读写入口：值源本身（固定值 / 公式都在里面）
    private static ExpressionFieldWidget.Accessor keySource(Supplier<ValueSource> getter, Consumer<ValueSource> setter) {
        return new ExpressionFieldWidget.Accessor() {
            @Override
            public ValueSource source() {
                return getter.get();
            }

            @Override
            public void source(ValueSource source) {
                setter.accept(source);
            }
        };
    }

    /// 一行里左半边或右半边的一个数值字段；{@code dynamic} 为 null 表示该字段不允许变成动态
    private record FieldSpec(Component label, float value, int decimals,
                             NumberFieldWidget.FloatSetter setter, FloatGetter getter,
                             ExpressionFieldWidget.Accessor dynamic) {
    }

    /// 枚举二选一 / 多选一：最后一格吃掉取整余量，右边界与其它行严格对齐
    private <T extends Enum<T>> int modeRow(int x, int y, int width, Component label, T[] values, T current, java.util.function.Consumer<T> setter) {
        widgets.add(new LabelWidget(new UiRect(x, y, Math.max(8, contentRight - x), FIELD_HEIGHT), label).color(Draw.TEXT_DIM));
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
        widgets.add(new LabelWidget(new UiRect(x, y, Math.max(8, contentRight - x), FIELD_HEIGHT),
                EditorLang.t("inspector.key.symmetric")).color(Draw.TEXT_DIM));
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
