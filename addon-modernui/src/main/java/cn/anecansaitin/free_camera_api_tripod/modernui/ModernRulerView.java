package cn.anecansaitin.free_camera_api_tripod.modernui;

import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorSession;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

/// 时间轴标尺：主/次刻度线、时间标签与播放头，同时承担时间轴的水平操作。
///
/// 刻度线和播放头画在自己的 {@code onDraw} 里（在子控件之下），时间标签则是绝对定位的
/// TextView——Modern UI 的 Canvas 画文字要自己拼 FontPaint，交给 TextView 省事得多；
/// 标签位置随视图范围重建，因此和刻度线严格对齐。
///
/// 交互：拖动移动播放头；Ctrl + 滚轮缩放；Shift + 滚轮平移。缩放与适配也供时间轴的按钮调用。
final class ModernRulerView extends FrameLayout {
    static final int HEIGHT = 24;
    private static final int LABEL_HEIGHT = 16;
    private static final int TICK_MAJOR = 6;
    private static final int TICK_MINOR = 3;
    /// 次刻度离得比这还近就不画了，否则缩小时会糊成一片
    private static final float MIN_MINOR_SPACING = 6.0F;
    private static final float ZOOM_STEP = 1.15F;
    private static final float PAN_STEP = 24.0F;

    private static final Paint LINE = new Paint();
    private static final Paint FILL = new Paint();

    static {
        LINE.setStyle(Paint.STROKE);
        LINE.setStrokeWidth(1.0F);
        LINE.setAntiAlias(false);
        FILL.setStyle(Paint.FILL);
        FILL.setAntiAlias(false);
    }

    private final EditorSession session;
    /// 标签布局是否过期（视图范围或宽度变了）
    private final ModernUiRefresher.Gate layoutGate = new ModernUiRefresher.Gate();
    /// 是否需要重绘（除了范围，播放头也会让它动）
    private final ModernUiRefresher.Gate drawGate = new ModernUiRefresher.Gate();
    private final RectF rect = new RectF();
    private boolean dragging;

    ModernRulerView(Context context, EditorSession session) {
        super(context);
        this.session = session;
        setWillNotDraw(false);
        setClickable(true);
    }

    // region 绘制

    @Override
    protected void onSizeChanged(int width, int height, int previousWidth, int previousHeight) {
        super.onSizeChanged(width, height, previousWidth, previousHeight);
        rebuildLabels();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        canvas.drawColor(ModernUiColors.HEADER_BG);
        drawTicks(canvas, width, height);
        drawPlayhead(canvas, width, height);
    }

    private void drawTicks(Canvas canvas, int width, int height) {
        float step = session.majorTickStep();
        float pixelsPerSecond = session.pixelsPerSecond();
        float start = session.viewStartTime();
        float minorStep = step / 5.0F;
        boolean minor = minorStep * pixelsPerSecond >= MIN_MINOR_SPACING;

        if (minor) {
            LINE.setColor(ModernUiColors.GRID);

            for (int index = (int) Math.floor(start / minorStep); ; index++) {
                float time = index * minorStep;
                float x = (time - start) * pixelsPerSecond;

                if (x > width) {
                    break;
                }

                if (x >= 0.0F && !isMajor(time, step)) {
                    canvas.drawLine(x, height - TICK_MINOR, x, height, LINE);
                }
            }
        }

        LINE.setColor(ModernUiColors.BORDER);

        for (int index = (int) Math.ceil(start / step); ; index++) {
            float x = (index * step - start) * pixelsPerSecond;

            if (x > width) {
                break;
            }

            canvas.drawLine(x, height - TICK_MAJOR, x, height, LINE);
        }
    }

    private static boolean isMajor(float time, float step) {
        return Math.abs(time / step - Math.round(time / step)) < 1E-4F;
    }

    private void drawPlayhead(Canvas canvas, int width, int height) {
        float x = (session.playheadTime() - session.viewStartTime()) * session.pixelsPerSecond();

        if (x < 0.0F || x > width) {
            return;
        }

        FILL.setColor(ModernUiColors.PLAYHEAD);
        rect.set(x - 1.0F, 0.0F, x + 2.0F, height);
        canvas.drawRect(rect, FILL);
    }

    /// 按当前视图范围重建时间标签；主刻度间距保证不小于约 56 像素，标签不会挤在一起
    private void rebuildLabels() {
        removeAllViews();
        int width = getWidth();

        if (width <= 0) {
            return;
        }

        Context context = getContext();
        float step = session.majorTickStep();
        float start = session.viewStartTime();
        float pixelsPerSecond = session.pixelsPerSecond();
        int first = (int) Math.ceil(start / step);
        int last = first + (int) Math.ceil(width / (step * pixelsPerSecond)) + 1;

        for (int index = first; index <= last; index++) {
            float time = index * step;
            int x = Math.round((time - start) * pixelsPerSecond);

            if (x > width) {
                break;
            }

            TextView label = ModernUiWidgets.dim(context, session.formatTick(time, step));
            label.setPadding(0, 0, 0, 0);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, LABEL_HEIGHT);
            params.gravity = Gravity.LEFT | Gravity.TOP;
            params.leftMargin = x + 2;
            addView(label, params);
        }
    }

    // endregion

    // region 刷新

    /// 由刷新器逐帧调用：视图范围变了才重建标签，范围或播放头动了才重绘
    void refresh() {
        boolean layoutChanged = layoutGate.changed(
                session.viewStartTime(), session.pixelsPerSecond(), session.majorTickStep(), getWidth());

        if (layoutChanged) {
            rebuildLabels();
        }

        if (layoutChanged || drawGate.changed(session.playheadTime())) {
            invalidate();
        }
    }

    // endregion

    // region 交互

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                dragging = true;
                seekTo(event.getX());
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    seekTo(event.getX());
                    return true;
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false;
                    performClick();
                    return true;
                }
            }
            default -> {
            }
        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) {
            return super.onGenericMotionEvent(event);
        }

        float scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);

        if (scroll == 0.0F) {
            return super.onGenericMotionEvent(event);
        }

        // 滚轮只在按住 Ctrl / Shift 时才归标尺用，否则留给上层的轨道列表滚动
        if (event.isControlPressed()) {
            zoomAt(event.getX(), scroll > 0.0F ? ZOOM_STEP : 1.0F / ZOOM_STEP);
            return true;
        }

        if (event.isShiftPressed()) {
            session.viewStartTime(session.viewStartTime()
                    - Math.signum(scroll) * PAN_STEP / session.pixelsPerSecond());
            return true;
        }

        return super.onGenericMotionEvent(event);
    }

    private void seekTo(float x) {
        session.seek(session.snapTime(session.viewStartTime() + x / session.pixelsPerSecond()));
    }

    /// 以可见区中心为基准缩放
    void zoom(float factor) {
        zoomAt(getWidth() / 2.0F, factor);
    }

    /// 以某个像素位置为中心缩放，指针下的时刻保持不动
    private void zoomAt(float x, float factor) {
        float pixelsPerSecond = session.pixelsPerSecond();
        float timeAtPointer = session.viewStartTime() + x / pixelsPerSecond;
        session.pixelsPerSecond(pixelsPerSecond * factor);
        session.viewStartTime(timeAtPointer - x / session.pixelsPerSecond());
    }

    /// 缩放到刚好容纳整段动画
    void fitView() {
        int width = getWidth();

        if (width <= 0) {
            return;
        }

        float duration = Math.max(0.5F, session.duration());
        session.pixelsPerSecond((width - 16.0F) / duration);
        session.viewStartTime(0.0F);
    }

    // endregion
}
