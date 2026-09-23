package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
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
    private static final int MARGIN_BOTTOM = 14;
    private static final int KEY_RADIUS = 3;
    private static final int GRAB_RADIUS = 5;
    private static final int TANGENT_GRAB_RADIUS = 5;

    private final EditorContext context;
    /// 值域缩放系数，1 表示自动适配数据范围
    private float valueSpanScale = 1f;
    /// 数值视图的纵向平移量（数值单位），由中键拖动改变
    private float valueOffset;

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
        ButtonWidget fit = new ButtonWidget(new UiRect(content.right() - 16, content.y() + 2, 14, 12),
                Component.literal(Icons.FIT), this::fitView);
        fit.tooltip(EditorLang.t("graph.fit"));
        widgets.add(fit);
    }

    /// 适配视图：把时间视图调整为刚好容纳整段动画，数值范围还原为自动适配。
    /// 时间视图与时间轴共用同一套状态（{@link EditorContext#pixelsPerSecond()} /
    /// {@link EditorContext#viewStartTime()}），因此「适配」在曲线图与时间轴上的时间范围一致。
    private void fitView() {
        UiRect plot = plotRect(contentRect());
        float duration = Math.max(0.5f, context.duration());
        context.pixelsPerSecond((plot.width() - 24f) / duration);
        context.viewStartTime(-8f / context.pixelsPerSecond());
        valueSpanScale = 1f;
        valueOffset = 0f;
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
        String rangeText = Draw.num(range[0], 2) + " ~ " + Draw.num(range[1], 2);
        int rangeWidth = Draw.font().width(rangeText);
        int headerRight = content.right() - 34;

        // 轨道名与取值区间共用一行，名称过长时截断
        Draw.textEllipsized(graphics, track.label().getString(), content.x() + 5, content.y() + 4,
                Math.max(8, headerRight - rangeWidth - 4 - (content.x() + 5)), Draw.TEXT);
        Draw.text(graphics, rangeText, headerRight - rangeWidth, content.y() + 4, Draw.TEXT_DIM);

        renderGrid(graphics, content, plot, range);
        renderCurve(graphics, plot, track, range);
        renderKeys(graphics, plot, track, range, mouseX, mouseY);
        renderPlayhead(graphics, plot);
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

    private void renderCurve(GuiGraphicsExtractor graphics, UiRect plot, CurveTrack track, float[] range) {
        Curve curve = track.curve();

        if (curve.size() == 0) {
            return;
        }

        graphics.enableScissor(plot.x(), plot.y(), plot.right(), plot.bottom());

        // 浮点采样 + 浮点填充：整数取整会让曲线呈阶梯状
        float step = 0.5f;
        float halfThickness = 0.6f;
        float previousY = valueToYFloat(plot, curve.evaluate(xToTime(plot, plot.x())), range[0], range[1]);

        for (float x = plot.x() + step; x <= plot.right(); x += step) {
            float y = valueToYFloat(plot, curve.evaluate(xToTime(plot, x)), range[0], range[1]);
            Draw.floatFill(graphics, x - step, Math.min(previousY, y) - halfThickness, x, Math.max(previousY, y) + halfThickness, track.color());
            previousY = y;
        }

        graphics.disableScissor();
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
            Draw.diamond(graphics, x, y, isSelected || isHovered ? KEY_RADIUS + 1 : KEY_RADIUS, isSelected ? Draw.SELECTED : Draw.TEXT);
        }

        // 选中关键帧的贝塞尔控制点（厄米特插值以贝塞尔形式表现）
        Keyframe key = selected >= 0 && selected < curve.size() ? curve.key(selected) : null;

        if (key != null && key.evaluateMode() == EvaluateMode.HERMITE) {
            float inSpan = tangentSpan(plot, track, selected, false);
            float outSpan = tangentSpan(plot, track, selected, true);
            int kx = timeToX(plot, key.time());
            int ky = valueToY(plot, key.value(), range[0], range[1]);
            int inX = timeToX(plot, key.time() - inSpan);
            int inY = valueToY(plot, key.value() - key.inTangent() * inSpan, range[0], range[1]);
            int outX = timeToX(plot, key.time() + outSpan);
            int outY = valueToY(plot, key.value() + key.outTangent() * outSpan, range[0], range[1]);
            Draw.floatLine(graphics, kx, ky, inX, inY, 1.2f, 0xFFFF8A80);
            Draw.floatLine(graphics, kx, ky, outX, outY, 1.2f, 0xFF80D8FF);
            Draw.circle(graphics, inX, inY, 2, 0xFFFF8A80);
            Draw.circle(graphics, outX, outY, 2, 0xFF80D8FF);
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

    /// 贝塞尔控制点的时间跨度：相邻关键帧间隔的 1/3。
    /// 没有相邻关键帧时退回固定屏幕跨度，保证孤立关键帧的切线仍可拖拽。
    private float tangentSpan(UiRect plot, CurveTrack track, int keyIndex, boolean outgoing) {
        Curve curve = track.curve();
        int neighbor = outgoing ? keyIndex + 1 : keyIndex - 1;

        if (neighbor >= 0 && neighbor < curve.size()) {
            Keyframe key = curve.key(keyIndex);
            Keyframe other = curve.key(neighbor);

            if (key != null && other != null) {
                float span = Math.abs(other.time() - key.time()) / 3f;

                if (span > 1.0E-4f) {
                    return span;
                }
            }
        }

        return Math.max(1.0E-3f, (float) plot.width() / context.pixelsPerSecond() * 0.12f);
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

        // 先判断贝塞尔控制点，其优先级高于关键帧本体
        if (selectedKey != null && selectedKey.evaluateMode() == EvaluateMode.HERMITE) {
            float inSpan = tangentSpan(plot, track, selected, false);
            float outSpan = tangentSpan(plot, track, selected, true);
            int inX = timeToX(plot, selectedKey.time() - inSpan);
            int inY = valueToY(plot, selectedKey.value() - selectedKey.inTangent() * inSpan, range[0], range[1]);
            int outX = timeToX(plot, selectedKey.time() + outSpan);
            int outY = valueToY(plot, selectedKey.value() + selectedKey.outTangent() * outSpan, range[0], range[1]);

            if (Math.abs(event.x() - inX) <= TANGENT_GRAB_RADIUS && Math.abs(event.y() - inY) <= TANGENT_GRAB_RADIUS) {
                drag = Drag.TANGENT_IN;
                dragKey = selected;
                return true;
            }

            if (Math.abs(event.x() - outX) <= TANGENT_GRAB_RADIUS && Math.abs(event.y() - outY) <= TANGENT_GRAB_RADIUS) {
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

            if (context.editor().addKey(track, time) >= 0) {
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
            case TANGENT_IN -> {
                // 入切线的斜率与出切线同号：对称开启时把出切线一并拉到同一斜率，形成镜像控制点
                float slope = (key.value() - yToValue(plot, event.y(), range[0], range[1])) / tangentSpan(plot, track, dragKey, false);
                key.inTangent(slope);

                if (context.bezierSymmetric()) {
                    key.outTangent(slope);
                }
            }
            case TANGENT_OUT -> {
                float slope = (yToValue(plot, event.y(), range[0], range[1]) - key.value()) / tangentSpan(plot, track, dragKey, true);
                key.outTangent(slope);

                if (context.bezierSymmetric()) {
                    key.inTangent(slope);
                }
            }
            default -> {
                return false;
            }
        }

        return true;
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
