package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.CurveSampler;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Scope;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/// 曲线图面板：展示并编辑选中曲线轨道的取值曲线与切线手柄。
public class GraphPanel extends EditorPanel {
    private static final int MARGIN_LEFT = 42;
    private static final int MARGIN_RIGHT = 12;
    private static final int MARGIN_TOP = 18;
    /// 底部要给时间刻度与图例各留一行
    private static final int MARGIN_BOTTOM = 26;
    private static final int KEY_RADIUS = 3;
    private static final int GRAB_RADIUS = 5;
    private static final int TANGENT_GRAB_RADIUS = 5;
    /// 曲柄最短能拖到相邻帧间隔的几分之一：留一点余量，免得斜率被零除后爆掉
    private static final float MIN_HANDLE_FACTOR = 0.02f;
    /// 适配时时间轴左右各留的像素
    private static final float FIT_TIME_PADDING = 12f;
    /// 适配时数值范围上下各留的比例
    private static final float FIT_VALUE_PADDING = 1.2f;
    /// 动态段的虚线长短（屏幕像素）
    private static final float DASH_LENGTH = 4f;
    /// 动态关键帧的点色与它那两段曲线的线色
    private static final int DYNAMIC_KEY_COLOR = 0xFF1A6EF2;
    private static final int DYNAMIC_CURVE_COLOR = 0xFF25D6F4;

    private final EditorContext context;
    /// 值域缩放系数，1 表示自动适配数据范围
    private float valueSpanScale = 1f;
    /// 数值视图的纵向平移量（数值单位），由中键拖动改变
    private float valueOffset;

    /// 屏幕上的一个点
    private record Point(int x, int y) {
    }

    private enum Drag {
        NONE,
        KEY,
        TANGENT_IN,
        TANGENT_OUT,
        /// 中键整体平移视图
        PAN
    }

    private Drag drag = Drag.NONE;
    private int dragKey = -1;

    public GraphPanel(EditorContext context) {
        super("graph", EditorLang.t("panel.graph"));
        this.context = context;
    }

    private @Nullable CurveTrack selectedTrack() {
        AnimationTrack track = context.editor().selectedTrack();
        return track instanceof CurveTrack curveTrack ? curveTrack : null;
    }

    private UiRect plotRect(UiRect content) {
        return new UiRect(
                content.x() + MARGIN_LEFT,
                content.y() + MARGIN_TOP,
                Math.max(1, content.width() - MARGIN_LEFT - MARGIN_RIGHT),
                Math.max(1, content.height() - MARGIN_TOP - MARGIN_BOTTOM)
        );
    }

    private int timeToX(UiRect plot, float time) {
        return Math.round(plot.x() + (time - context.viewStartTime()) * context.pixelsPerSecond());
    }

    private float xToTime(UiRect plot, double x) {
        return context.viewStartTime() + (float) (x - plot.x()) / context.pixelsPerSecond();
    }

    private int valueToY(UiRect plot, float value, float min, float max) {
        return Math.round(valueToYFloat(plot, value, min, max));
    }

    /// 浮点取值映射，供曲线绘制使用（整数取整会让曲线出现阶梯）
    private float valueToYFloat(UiRect plot, float value, float min, float max) {
        float ratio = (value - min) / Math.max(1.0E-6f, max - min);
        return plot.bottom() - Mth.clamp(ratio, -1f, 2f) * plot.height();
    }

    private float yToValue(UiRect plot, double y, float min, float max) {
        float ratio = (float) ((plot.bottom() - y) / plot.height());
        return min + ratio * (max - min);
    }

    /// 值域：依据关键帧取值自动适配，再乘以缩放系数
    private float[] valueRange(CurveTrack track) {
        Curve curve = track.curve();
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (int i = 0; i < curve.size(); i++) {
            Keyframe key = curve.key(i);

            if (key == null) {
                continue;
            }

            min = Math.min(min, key.value());
            max = Math.max(max, key.value());
        }

        if (min > max) {
            min = -1f;
            max = 1f;
        }

        float center = (min + max) / 2f + valueOffset;
        float half = Math.max((max - min) / 2f, 0.05f) * valueSpanScale;
        return new float[]{center - half, center + half};
    }

    @Override
    protected void layoutWidgets(UiRect content) {
        ButtonWidget fit = new ButtonWidget(new UiRect(content.right() - CONTROL_HEIGHT - 2, content.y() + 2,
                CONTROL_HEIGHT, CONTROL_HEIGHT),
                Component.literal(Icons.FIT), this::fitView);
        fit.tooltip(EditorLang.t("graph.fit"));
        widgets.add(fit);
    }

    /// 适配视图：时间范围默认取整段动画，选中关键帧时收缩到它和左右相邻各一个；
    /// 上下左右都留出余量，曲线不会贴着边框。
    ///
    /// 时间视图与时间轴共用同一套状态（{@link EditorContext#pixelsPerSecond()} /
    /// {@link EditorContext#viewStartTime()}），数量范围则由本面板自己的缩放与平移表达
    private void fitView() {
        UiRect plot = plotRect(contentRect());
        CurveTrack track = selectedTrack();
        float start = 0f;
        float end = Math.max(0.5f, context.duration());
        float[] focus = track == null ? null : focusedRange(track);

        if (focus != null) {
            start = focus[0];
            end = focus[1];
        }

        float span = Math.max(0.1f, end - start);
        context.pixelsPerSecond(Math.max(1.0E-3f, (plot.width() - FIT_TIME_PADDING * 2) / span));
        context.viewStartTime(start - FIT_TIME_PADDING / context.pixelsPerSecond());

        // 数值范围：没有聚焦目标就还原为整条曲线的自动适配
        if (focus == null) {
            valueSpanScale = 1f;
            valueOffset = 0f;
            return;
        }

        Curve curve = track.curve();
        float dataMin = Float.MAX_VALUE;
        float dataMax = -Float.MAX_VALUE;

        for (int i = 0; i < curve.size(); i++) {
            Keyframe key = curve.key(i);

            if (key != null) {
                dataMin = Math.min(dataMin, key.value());
                dataMax = Math.max(dataMax, key.value());
            }
        }

        float dataHalf = Math.max((dataMax - dataMin) / 2f, 0.05f);
        float targetHalf = Math.max((focus[3] - focus[2]) / 2f, 0.05f) * FIT_VALUE_PADDING;
        valueSpanScale = targetHalf / dataHalf;
        valueOffset = (focus[2] + focus[3]) / 2f - (dataMin + dataMax) / 2f;
    }

    /// 选中关键帧时返回 {开始时间, 结束时间, 最低取值, 最高取值}——范围是它和左右相邻各一个；
    /// 没有选中或轨道里只剩一两个键时返回 null，交给整段动画
    private @Nullable float[] focusedRange(CurveTrack track) {
        Curve curve = track.curve();
        int selected = context.editor().selectedKeyIndex();

        if (selected < 0 || selected >= curve.size()) {
            return null;
        }

        int from = Math.max(0, selected - 1);
        int to = Math.min(curve.size() - 1, selected + 1);
        Keyframe first = curve.key(from);
        Keyframe last = curve.key(to);

        if (first == null || last == null || from == to) {
            return null;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (int i = from; i <= to; i++) {
            Keyframe key = curve.key(i);

            if (key != null) {
                min = Math.min(min, key.value());
                max = Math.max(max, key.value());
            }
        }

        return new float[]{first.time(), last.time(), min, max};
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, UiRect content, int mouseX, int mouseY) {
        Draw.canvas(graphics, content, Draw.CANVAS_BG);
        CurveTrack track = selectedTrack();

        if (track == null) {
            // 右侧被「适配」按钮占用，提示文字需在此之前截断
            Draw.textEllipsized(graphics, EditorLang.t("graph.no_track").getString(), content.x() + 6, content.y() + 4,
                    Math.max(8, content.width() - 40), Draw.TEXT_DISABLED);
            return;
        }

        UiRect plot = plotRect(content);
        float[] range = valueRange(track);

        // 顶部只剩轨道名：右侧那点宽度留给「适配」按钮，取值范围不再写成数字，改由底部图例说明颜色
        Draw.textEllipsized(graphics, track.label().getString(), content.x() + 5, content.y() + 4,
                Math.max(8, content.width() - CONTROL_HEIGHT - 14), Draw.TEXT);

        renderGrid(graphics, content, plot, range);
        renderCurve(graphics, plot, track, range);
        renderKeys(graphics, plot, track, range, mouseX, mouseY);
        renderPlayhead(graphics, plot);
        renderLegend(graphics, content, track);
    }

    private void renderGrid(GuiGraphicsExtractor graphics, UiRect content, UiRect plot, float[] range) {
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());

        // 横向取值刻度
        int divisions = 4;

        for (int i = 0; i <= divisions; i++) {
            int y = plot.y() + plot.height() * i / divisions;
            float value = range[1] - (range[1] - range[0]) * i / divisions;
            Draw.hLine(graphics, plot.x(), plot.right(), y, Draw.GRID);
            Draw.textRight(graphics, Draw.num(value, 2), plot.x() - 3, y - 4, Draw.TEXT_DISABLED);
        }

        Draw.border(graphics, plot, Draw.BORDER);

        // 纵向时间刻度
        float step = context.majorTickStep();
        float viewEnd = xToTime(plot, plot.right());
        int first = (int) Math.floor(context.viewStartTime() / step) - 1;
        int last = (int) Math.ceil(viewEnd / step) + 1;

        for (int i = first; i <= last; i++) {
            float time = i * step;

            if (time < 0) {
                continue;
            }

            int x = timeToX(plot, time);

            if (x < plot.x()) {
                continue;
            }

            if (x > plot.right()) {
                break;
            }

            Draw.vLine(graphics, x, plot.y(), plot.bottom(), Draw.GRID);
            Draw.textCentered(graphics, context.formatTick(time, step), x, plot.bottom() + 3, Draw.TEXT_DISABLED);
        }

        graphics.disableScissor();
    }

    /// 曲线：整屏采样后按段上色。碰到挂公式（动态模式）的关键帧，那一段改用动态色画成虚线，
    /// 与静默段一眼可分——其余段仍用轨道自己的颜色。
    ///
    /// 采样走 [CurveSampler]，也就是**与播放同一个入口**：曲线上画的就是播放会走的那条线，
    /// 公式在这一步已经被算进去了。采样器复用解析缓存，整条曲线画下来每个关键帧只解析一次
    private void renderCurve(GuiGraphicsExtractor graphics, UiRect plot, CurveTrack track, float[] range) {
        Curve curve = track.curve();

        if (curve.size() == 0) {
            return;
        }

        graphics.enableScissor(plot.x(), plot.y(), plot.right(), plot.bottom());

        Scope scope = context.scope();
        CurveSampler sampler = context.sampler();
        // 浮点采样 + 浮点填充：整数取整会让曲线呈阶梯状
        float step = 0.5f;
        float halfThickness = 0.6f;
        float previousY = valueToYFloat(plot, sampler.sample(curve, xToTime(plot, plot.x()), scope), range[0], range[1]);
        int segment = 0;

        for (float x = plot.x() + step; x <= plot.right(); x += step) {
            float time = xToTime(plot, x);

            // 时间单调递增，段索引只向前推进
            while (segment + 1 < curve.size()) {
                Keyframe next = curve.key(segment + 1);

                if (next == null || next.time() > time) {
                    break;
                }

                segment++;
            }

            float y = valueToYFloat(plot, sampler.sample(curve, time, scope), range[0], range[1]);
            boolean dynamic = touchesDynamic(curve, segment);
            // 虚线就是一截画一截不画：按屏幕位置取方波，斜线看起来也是等距的
            boolean visible = !dynamic || (int) ((x - plot.x()) / DASH_LENGTH) % 2 == 0;

            if (visible) {
                Draw.floatFill(graphics, x - step, Math.min(previousY, y) - halfThickness, x, Math.max(previousY, y) + halfThickness,
                        dynamic ? DYNAMIC_CURVE_COLOR : track.color());
            }

            previousY = y;
        }

        graphics.disableScissor();
    }

    /// 这一段曲线上是否碰到动态关键帧：段由 [segment, segment + 1] 两个键界定，
    /// 轨道只有一个键时整条水平线都按那个键算
    private static boolean touchesDynamic(Curve curve, int segment) {
        int left = Mth.clamp(segment, 0, Math.max(0, curve.size() - 1));
        int right = Math.min(curve.size() - 1, left + 1);
        Keyframe first = curve.key(left);
        Keyframe second = curve.key(right);
        return (first != null && first.hasFormula()) || (second != null && second.hasFormula());
    }

    private void renderKeys(GuiGraphicsExtractor graphics, UiRect plot, CurveTrack track, float[] range, int mouseX, int mouseY) {
        Curve curve = track.curve();
        int selected = context.editor().selectedKeyIndex();
        graphics.enableScissor(plot.x(), plot.y(), plot.right(), plot.bottom());

        int hovered = keyIndexAt(plot, track, range, mouseX, mouseY);

        for (int i = 0; i < curve.size(); i++) {
            Keyframe key = curve.key(i);

            if (key == null) {
                continue;
            }

            int x = timeToX(plot, key.time());
            int y = valueToY(plot, key.value(), range[0], range[1]);
            boolean isSelected = i == selected;
            boolean isHovered = i == hovered;
            // 动态模式的关键帧用蓝点，与普通键的灰点分开
            int color = isSelected ? Draw.SELECTED : (key.hasFormula() ? DYNAMIC_KEY_COLOR : Draw.TEXT);
            Draw.diamond(graphics, x, y, isSelected || isHovered ? KEY_RADIUS + 1 : KEY_RADIUS, color);
        }

        // 选中关键帧的贝塞尔控制点（厄米特插值以贝塞尔形式表现）
        Keyframe key = selected >= 0 && selected < curve.size() ? curve.key(selected) : null;

        if (key != null && key.evaluateMode() == EvaluateMode.HERMITE) {
            Point in = handlePoint(plot, track, selected, key, range, false);
            Point out = handlePoint(plot, track, selected, key, range, true);
            int kx = timeToX(plot, key.time());
            int ky = valueToY(plot, key.value(), range[0], range[1]);
            Draw.floatLine(graphics, kx, ky, in.x(), in.y(), 1.2f, 0xFFFF8A80);
            Draw.floatLine(graphics, kx, ky, out.x(), out.y(), 1.2f, 0xFF80D8FF);
            Draw.circle(graphics, in.x(), in.y(), 2, 0xFFFF8A80);
            Draw.circle(graphics, out.x(), out.y(), 2, 0xFF80D8FF);
        }

        graphics.disableScissor();
    }

    private void renderPlayhead(GuiGraphicsExtractor graphics, UiRect plot) {
        int x = timeToX(plot, context.player().time());

        if (x < plot.x() || x > plot.right()) {
            return;
        }

        graphics.enableScissor(plot.x(), plot.y(), plot.right(), plot.bottom());
        Draw.vLine(graphics, x, plot.y(), plot.bottom(), Draw.PLAYHEAD);
        graphics.disableScissor();
    }

    /// 底部图例：说明点与线的颜色各自代表什么。
    /// 曲线色块用轨道自己的颜色，与上面画的曲线对得上；放不下就不画剩下的项，不硬挤成一行
    private void renderLegend(GuiGraphicsExtractor graphics, UiRect content, CurveTrack track) {
        int y = content.bottom() - 12;
        int limit = content.right() - 3;
        int x = content.x() + 6;
        x = legendPoint(graphics, x, y, limit, Draw.TEXT, EditorLang.t("graph.legend.key"));
        x = legendPoint(graphics, x, y, limit, DYNAMIC_KEY_COLOR, EditorLang.t("graph.legend.dynamic"));
        x = legendLine(graphics, x, y, limit, track.color(), false, EditorLang.t("graph.legend.curve"));
        legendLine(graphics, x, y, limit, DYNAMIC_CURVE_COLOR, true, EditorLang.t("graph.legend.dynamic_curve"));
    }

    /// 图例里的点标记；放不下时返回一个画不到的位置，后面的项自然也不再画
    private int legendPoint(GuiGraphicsExtractor graphics, int x, int y, int limit, int color, Component text) {
        if (x + legendWidth(text) > limit) {
            return limit + 1;
        }

        Draw.diamond(graphics, x + 3, y + 4, 3, color);
        Draw.text(graphics, text, x + 10, y, Draw.TEXT_DIM);
        return x + legendWidth(text);
    }

    /// 图例里的线标记；dashed 为真画成两截，跟曲线图上的虚线对得上
    private int legendLine(GuiGraphicsExtractor graphics, int x, int y, int limit, int color, boolean dashed, Component text) {
        if (x + legendWidth(text) > limit) {
            return limit + 1;
        }

        if (dashed) {
            Draw.hLine(graphics, x, x + 3, y + 4, color);
            Draw.hLine(graphics, x + 5, x + 8, y + 4, color);
        } else {
            Draw.hLine(graphics, x, x + 8, y + 4, color);
        }

        Draw.text(graphics, text, x + 10, y, Draw.TEXT_DIM);
        return x + legendWidth(text);
    }

    /// 一项图例的宽度：标记 + 文字 + 与下一项的间距
    private static int legendWidth(Component text) {
        return 10 + Draw.font().width(text) + 10;
    }

    /// 曲柄的基准长度：相邻关键帧间隔的 1/3。
    ///
    /// 某一侧没有相邻关键帧时借用另一侧的间隔——两侧长度倍数一样却画得一长一短，会让人以为数值没生效。
    /// 那一侧本来也不参与求值（最外侧的曲柄不属于任何一段曲线），所以借用不影响曲线形状；
    /// 两侧都没有（轨道里只有一个键）才退回固定屏幕跨度，保证孤立的曲柄仍可拖拽
    private float tangentSpan(UiRect plot, CurveTrack track, int keyIndex, boolean outgoing) {
        float span = neighborSpan(track, keyIndex, outgoing);

        if (span <= 0) {
            span = neighborSpan(track, keyIndex, !outgoing);
        }

        return span > 0 ? span : Math.max(1.0E-3f, (float) plot.width() / context.pixelsPerSecond() * 0.12f);
    }

    /// 某一侧相邻间隔的 1/3；没有相邻关键帧或间隔为零时返回 0
    private static float neighborSpan(CurveTrack track, int keyIndex, boolean outgoing) {
        Curve curve = track.curve();
        int neighbor = outgoing ? keyIndex + 1 : keyIndex - 1;

        if (neighbor < 0 || neighbor >= curve.size()) {
            return 0f;
        }

        Keyframe key = curve.key(keyIndex);
        Keyframe other = curve.key(neighbor);

        if (key == null || other == null) {
            return 0f;
        }

        float span = Math.abs(other.time() - key.time()) / 3f;
        return span > 1.0E-4f ? span : 0f;
    }

    /// 曲柄（贝塞尔控制点）在屏幕上的位置。
    ///
    /// 横向长度 = 相邻帧间隔的 1/3 乘关键帧上的长度倍数，纵向偏移 = 斜率乘同一个长度。
    /// 曲柄的横竖两个方向各自对应一个数据，位置与求值用的那对控制点重合，拖到哪曲线就变到哪
    private Point handlePoint(UiRect plot, CurveTrack track, int keyIndex, Keyframe key, float[] range, boolean outgoing) {
        float length = handleLength(plot, track, keyIndex, key, outgoing);
        int x = timeToX(plot, outgoing ? key.time() + length : key.time() - length);
        float offset = slopeOf(key, outgoing) * length;
        int y = valueToY(plot, outgoing ? key.value() + offset : key.value() - offset, range[0], range[1]);
        return new Point(x, y);
    }

    /// 曲柄的长度（时间单位）：基准长度（相邻帧间隔的 1/3）乘关键帧上的长度倍数
    private float handleLength(UiRect plot, CurveTrack track, int keyIndex, Keyframe key, boolean outgoing) {
        float span = tangentSpan(plot, track, keyIndex, outgoing);
        return span * Curve.handleLength(lengthOf(key, outgoing));
    }

    private static float lengthOf(Keyframe key, boolean outgoing) {
        return outgoing ? key.outLength() : key.inLength();
    }

    private static void setLength(Keyframe key, boolean outgoing, float length) {
        if (outgoing) {
            key.outLength(length);
        } else {
            key.inLength(length);
        }
    }

    private static float slopeOf(Keyframe key, boolean outgoing) {
        return outgoing ? key.outSlope() : key.inSlope();
    }

    private static void setSlope(Keyframe key, boolean outgoing, float slope) {
        if (outgoing) {
            key.outSlope(slope);
        } else {
            key.inSlope(slope);
        }
    }

    private int keyIndexAt(UiRect plot, CurveTrack track, float[] range, double mouseX, double mouseY) {
        Curve curve = track.curve();
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < curve.size(); i++) {
            Keyframe key = curve.key(i);

            if (key == null) {
                continue;
            }

            int dx = (int) Math.abs(mouseX - timeToX(plot, key.time()));
            int dy = (int) Math.abs(mouseY - valueToY(plot, key.value(), range[0], range[1]));

            if (dx <= GRAB_RADIUS && dy <= GRAB_RADIUS && dx + dy < bestDistance) {
                bestDistance = dx + dy;
                best = i;
            }
        }

        return best;
    }

    // region 交互

    @Override
    protected boolean contentMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 中键：整体平移视图（左右移动时间、上下移动数值）
        if (event.button() == 2) {
            drag = Drag.PAN;
            return true;
        }

        CurveTrack track = selectedTrack();

        if (track == null) {
            return false;
        }

        UiRect plot = plotRect(contentRect());
        float[] range = valueRange(track);
        Curve curve = track.curve();
        int selected = context.editor().selectedKeyIndex();
        Keyframe selectedKey = selected >= 0 && selected < curve.size() ? curve.key(selected) : null;

        // 先判断贝塞尔曲柄，其优先级高于关键帧本体
        if (selectedKey != null && selectedKey.evaluateMode() == EvaluateMode.HERMITE) {
            Point in = handlePoint(plot, track, selected, selectedKey, range, false);
            Point out = handlePoint(plot, track, selected, selectedKey, range, true);

            if (Math.abs(event.x() - in.x()) <= TANGENT_GRAB_RADIUS && Math.abs(event.y() - in.y()) <= TANGENT_GRAB_RADIUS) {
                drag = Drag.TANGENT_IN;
                dragKey = selected;
                return true;
            }

            if (Math.abs(event.x() - out.x()) <= TANGENT_GRAB_RADIUS && Math.abs(event.y() - out.y()) <= TANGENT_GRAB_RADIUS) {
                drag = Drag.TANGENT_OUT;
                dragKey = selected;
                return true;
            }
        }

        int keyIndex = keyIndexAt(plot, track, range, event.x(), event.y());

        if (keyIndex >= 0) {
            context.editor().selectTrack(track.id());
            context.editor().selectKey(keyIndex);
            drag = Drag.KEY;
            dragKey = keyIndex;
            return true;
        }

        if (doubleClick) {
            float time = context.snapTime(xToTime(plot, event.x()));

            if (context.editor().addKey(track, time, context.scope()) >= 0) {
                context.notify(EditorLang.t("notify.key_added", Draw.num(time, 2)));
            }
        }

        return true;
    }

    @Override
    protected boolean contentMouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (drag == Drag.NONE) {
            return false;
        }

        CurveTrack track = selectedTrack();
        UiRect plot = plotRect(contentRect());

        // 中键整体平移：不依赖选中的关键帧
        if (drag == Drag.PAN) {
            context.viewStartTime(context.viewStartTime() - (float) deltaX / context.pixelsPerSecond());

            if (track != null) {
                float[] range = valueRange(track);
                valueOffset += (float) deltaY / plot.height() * (range[1] - range[0]);
            }

            return true;
        }

        if (track == null) {
            return false;
        }

        float[] range = valueRange(track);
        Curve curve = track.curve();
        Keyframe key = dragKey >= 0 && dragKey < curve.size() ? curve.key(dragKey) : null;

        if (key == null) {
            return false;
        }

        switch (drag) {
            case KEY -> {
                int moved = context.editor().moveKey(track, dragKey, context.snapTime(xToTime(plot, event.x())));

                if (moved >= 0) {
                    dragKey = moved;
                }

                Keyframe movedKey = dragKey >= 0 && dragKey < curve.size() ? curve.key(dragKey) : null;

                if (movedKey != null) {
                    movedKey.value(yToValue(plot, event.y(), range[0], range[1]));
                }
            }
            case TANGENT_IN -> dragTangent(event, plot, track, key, range, false);
            case TANGENT_OUT -> dragTangent(event, plot, track, key, range, true);
            default -> {
                return false;
            }
        }

        return true;
    }

    /// 拖拽曲柄：曲柄的屏幕位置就是它的两个数据，鼠标拖到哪、曲柄就到哪。
    ///
    /// 横向：鼠标到关键帧的时间距离就是曲柄长度倍数；纵向：按这个长度反解斜率，
    /// 等于把曲柄拉到鼠标所在的高度。横向最长到基准的 {@link Curve#MAX_HANDLE_LENGTH} 倍——
    /// 再长这段曲线的横坐标就不再单调，值会随时间折返
    private void dragTangent(MouseButtonEvent event, UiRect plot, CurveTrack track, Keyframe key, float[] range, boolean outgoing) {
        float span = tangentSpan(plot, track, dragKey, outgoing);
        // 曲柄只能在自己那一侧伸缩：出侧朝右、入侧朝左。鼠标越过关键帧时长度取最短而不是取绝对值——
        // 取绝对值会让曲柄瞬间翻到另一侧去
        float reach = xToTime(plot, event.x()) - key.time();
        float length = Math.clamp(outgoing ? reach : -reach,
                span * MIN_HANDLE_FACTOR, span * Curve.MAX_HANDLE_LENGTH);
        setLength(key, outgoing, length / span);
        float value = yToValue(plot, event.y(), range[0], range[1]);
        // 入曲柄的斜率与出曲柄同号：对称开启时把另一侧一并拉到同一斜率，形成镜像曲柄
        float slope = (outgoing ? value - key.value() : key.value() - value) / length;
        setSlope(key, outgoing, slope);

        if (context.bezierSymmetric()) {
            setLength(key, !outgoing, lengthOf(key, outgoing));
            setSlope(key, !outgoing, slope);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            dragKey = -1;
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    protected boolean contentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 滚轮不再改变时间轴比例：仅保留 Ctrl 纵向缩放与 Shift 横向平移
        if (isCtrlDown()) {
            valueSpanScale = Mth.clamp(valueSpanScale * (scrollY > 0 ? 0.85f : 1.18f), 0.05f, 40f);
            return true;
        }

        if (isShiftDown()) {
            context.viewStartTime(context.viewStartTime() - (float) scrollY * 20f / context.pixelsPerSecond());
            return true;
        }

        return false;
    }

    private boolean isCtrlDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private boolean isShiftDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    // endregion
}
