package cn.anecansaitin.free_camera_api_tripod.modernui;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorSession;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import org.jspecify.annotations.Nullable;

/// 曲线图：横轴时间、纵轴数值，折线连接各关键帧，关键帧处画方块。
///
/// 没有选中轨道或该轨道不含曲线时留空背景（提示文字由面板里的 TextView 承担）。
/// 点击关键帧方块即选中对应的关键帧——曲线上的下标与轨道关键帧下标一一对应。
final class ModernCurveView extends View {
    private static final int PADDING = 10;
    private static final int KEY_SIZE = 5;
    /// 命中半径（像素）
    private static final float GRAB_RADIUS = 6.0F;

    private static final Paint FILL = new Paint();
    private static final Paint LINE = new Paint();

    static {
        FILL.setStyle(Paint.FILL);
        FILL.setAntiAlias(true);
        LINE.setStyle(Paint.STROKE);
        LINE.setStrokeWidth(1.5F);
        LINE.setAntiAlias(true);
    }

    private final EditorSession session;
    private final ModernUiRefresher.Gate gate = new ModernUiRefresher.Gate();
    private final RectF rect = new RectF();

    ModernCurveView(Context context, EditorSession session) {
        super(context);
        this.session = session;
        setClickable(true);
    }

    /// 一次绘制的几何：横轴 0~maxTime 映射到 left~right，纵轴 minValue~(minValue+span) 映射到 bottom~top
    private record Geometry(Curve curve, float left, float right, float top, float bottom,
                            float minValue, float span, float maxTime) {
        float x(float time) {
            return left + time / maxTime * (right - left);
        }

        float y(float value) {
            return bottom - (value - minValue) / span * (bottom - top);
        }
    }

    /// 当前选中轨道的曲线几何；没有可画的曲线时返回 null
    private @Nullable Geometry geometry() {
        @Nullable AnimationTrack track = session.selectedTrack();
        @Nullable Curve curve = track == null ? null : track.curve();

        if (curve == null || curve.size() < 2) {
            return null;
        }

        float minValue = Float.MAX_VALUE;
        float maxValue = -Float.MAX_VALUE;
        float maxTime = 0.0F;

        for (int index = 0; index < curve.size(); index++) {
            @Nullable Keyframe key = curve.key(index);

            if (key == null) {
                continue;
            }

            minValue = Math.min(minValue, key.value());
            maxValue = Math.max(maxValue, key.value());
            maxTime = Math.max(maxTime, key.time());
        }

        if (maxTime <= 0.0F || minValue > maxValue) {
            return null;
        }

        float span = maxValue - minValue;

        if (span <= 0.0F) {
            // 各关键帧数值相同：压成中线，避免除零
            span = 1.0F;
            minValue -= 0.5F;
        }

        return new Geometry(curve, PADDING, getWidth() - PADDING, PADDING, getHeight() - PADDING,
                minValue, span, maxTime);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        canvas.drawColor(ModernUiColors.CANVAS_BG);

        @Nullable Geometry geometry = geometry();

        if (geometry == null) {
            return;
        }

        Curve curve = geometry.curve();
        LINE.setColor(ModernUiColors.BORDER);
        rect.set(geometry.left(), geometry.top(), geometry.right(), geometry.bottom());
        canvas.drawRect(rect, LINE);

        LINE.setColor(ModernUiColors.ACCENT);
        float previousX = 0.0F;
        float previousY = 0.0F;
        boolean first = true;

        for (int index = 0; index < curve.size(); index++) {
            @Nullable Keyframe key = curve.key(index);

            if (key == null) {
                continue;
            }

            float x = geometry.x(key.time());
            float y = geometry.y(key.value());

            if (first) {
                first = false;
            } else {
                canvas.drawLine(previousX, previousY, x, y, LINE);
            }

            previousX = x;
            previousY = y;
        }

        int selected = session.selectedKeyIndex();
        FILL.setColor(ModernUiColors.NODE);

        for (int index = 0; index < curve.size(); index++) {
            @Nullable Keyframe key = curve.key(index);

            if (key == null) {
                continue;
            }

            float x = geometry.x(key.time());
            float y = geometry.y(key.value());
            boolean current = index == selected;
            FILL.setColor(current ? ModernUiColors.ACCENT : ModernUiColors.NODE);
            float half = (current ? KEY_SIZE + 3 : KEY_SIZE) / 2.0F;
            rect.set(x - half, y - half, x + half, y + half);
            canvas.drawRect(rect, FILL);
        }
    }

    /// 由刷新器逐帧调用；选中轨道、关键帧下标或控件尺寸变了才重绘
    void refresh() {
        @Nullable AnimationTrack track = session.selectedTrack();

        if (gate.changed(track == null ? null : track.id(), track == null ? 0 : track.keyCount(),
                session.selectedKeyIndex(), getWidth(), getHeight())) {
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event);
        }

        @Nullable AnimationTrack track = session.selectedTrack();
        @Nullable Geometry geometry = geometry();

        if (track == null || geometry == null) {
            return true;
        }

        int index = keyAt(geometry, event.getX(), event.getY());

        if (index >= 0) {
            session.selectTrack(track.id());
            session.selectKey(index);
        }

        return true;
    }

    /// 命中离点击点最近的关键帧；超出抓取半径返回 -1
    private int keyAt(Geometry geometry, float x, float y) {
        int best = -1;
        float bestDistance = GRAB_RADIUS * GRAB_RADIUS;

        for (int index = 0; index < geometry.curve().size(); index++) {
            @Nullable Keyframe key = geometry.curve().key(index);

            if (key == null) {
                continue;
            }

            float dx = x - geometry.x(key.time());
            float dy = y - geometry.y(key.value());
            float distance = dx * dx + dy * dy;

            if (distance <= bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }

        return best;
    }
}
