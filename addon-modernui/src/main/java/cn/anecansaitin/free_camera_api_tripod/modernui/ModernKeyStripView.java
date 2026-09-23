package cn.anecansaitin.free_camera_api_tripod.modernui;

import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorSession;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import org.jspecify.annotations.Nullable;

/// 时间轴上单个轨道的关键帧条。
///
/// 只画图形——Modern UI 的 Canvas 画文字要自己拼 FontPaint，交给旁边的 TextView 更省事。
/// 横轴时间映射与会话里的缩放一致，因此各行、以及上方的标尺天然对齐。
///
/// 交互：左键点关键帧即选中并可拖动改时间，点空白只改选中轨道；
/// 右键点关键帧直接删除。
final class ModernKeyStripView extends View {
    private static final int KEY_WIDTH = 5;
    private static final int KEY_HEIGHT = 11;
    private static final int KEY_GRAB_RADIUS = 5;
    /// 鼠标事件里表示右键的位
    private static final int SECONDARY_BUTTON = 2;

    private static final Paint FILL = new Paint();
    private static final Paint LINE = new Paint();

    static {
        FILL.setStyle(Paint.FILL);
        FILL.setAntiAlias(false);
        LINE.setStyle(Paint.STROKE);
        LINE.setStrokeWidth(1.0F);
        LINE.setAntiAlias(false);
    }

    private final EditorSession session;
    private final AnimationTrack track;
    private final ModernUiRefresher.Gate gate = new ModernUiRefresher.Gate();
    private final RectF rect = new RectF();
    /// 正在拖动改时间的关键帧索引；没有拖动时为 -1
    private int draggingKey = -1;

    ModernKeyStripView(Context context, EditorSession session, AnimationTrack track) {
        super(context);
        this.session = session;
        this.track = track;
        setClickable(true);
    }

    // region 几何

    private float timeToX(float time) {
        return (time - session.viewStartTime()) * session.pixelsPerSecond();
    }

    private float xToTime(float x) {
        return session.viewStartTime() + x / session.pixelsPerSecond();
    }

    /// 命中鼠标下最近的关键帧；离得比抓取半径还远返回 -1
    private int keyAt(float x) {
        int best = -1;
        float bestDistance = KEY_GRAB_RADIUS;

        for (int index = 0; index < track.keyCount(); index++) {
            @Nullable TrackKey key = track.key(index);

            if (key == null) {
                continue;
            }

            float distance = Math.abs(x - timeToX(key.time()));

            if (distance <= bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }

        return best;
    }

    private boolean selectedTrack() {
        @Nullable AnimationTrack selected = session.selectedTrack();
        return selected != null && selected.id().equals(track.id());
    }

    // endregion

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        canvas.drawColor(ModernUiColors.CANVAS_BG);

        boolean selected = selectedTrack();

        if (selected) {
            FILL.setColor(ModernUiColors.ROW_SELECTED);
            rect.set(0.0F, 0.0F, width, height);
            canvas.drawRect(rect, FILL);
        }

        float top = (height - KEY_HEIGHT) / 2.0F;

        for (int index = 0; index < track.keyCount(); index++) {
            @Nullable TrackKey key = track.key(index);

            if (key == null) {
                continue;
            }

            float x = timeToX(key.time());

            if (x < -KEY_WIDTH || x > width + KEY_WIDTH) {
                continue;
            }

            // 选中的关键帧画大一圈并换成强调色，和内置界面保持同一种表达
            boolean current = selected && index == session.selectedKeyIndex();
            FILL.setColor(current ? ModernUiColors.ACCENT : track.color());
            float half = (current ? KEY_WIDTH + 2 : KEY_WIDTH) / 2.0F;
            rect.set(x - half, top, x + half, top + KEY_HEIGHT);
            canvas.drawRect(rect, FILL);
        }

        drawPlayhead(canvas, width, height);
    }

    /// 播放头压在关键帧之上，跨行的播放头因此是连续的
    private void drawPlayhead(Canvas canvas, int width, int height) {
        float x = timeToX(session.playheadTime());

        if (x < 0.0F || x > width) {
            return;
        }

        LINE.setColor(ModernUiColors.PLAYHEAD);
        canvas.drawLine(x, 0.0F, x, height, LINE);
    }

    /// 由刷新器逐帧调用；选中的轨道、关键帧数量、时间映射或播放头变了才重绘
    void refresh() {
        if (gate.changed(selectedTrack(), session.selectedKeyIndex(), track.keyCount(),
                session.viewStartTime(), session.pixelsPerSecond(), session.playheadTime())) {
            invalidate();
        }
    }

    // region 交互

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                int index = keyAt(event.getX());

                // 右键：命中哪个删哪个；没命中就什么也不做
                if ((event.getButtonState() & SECONDARY_BUTTON) != 0) {
                    if (index >= 0) {
                        session.selectTrack(track.id());
                        session.selectKey(index);

                        if (session.removeKey(index)) {
                            session.notify(ModernUiText.of("notify.key_removed"));
                        }
                    }

                    return true;
                }

                // 先选轨道（顺带清掉旧的关键帧选择），再落到具体关键帧上
                session.selectTrack(track.id());
                session.selectKey(index);
                draggingKey = index;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (draggingKey >= 0) {
                    int moved = session.moveKey(draggingKey, session.snapTime(xToTime(event.getX())));

                    if (moved >= 0) {
                        draggingKey = moved;
                    }

                    return true;
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingKey >= 0) {
                    draggingKey = -1;
                    performClick();
                    return true;
                }
            }
            default -> {
            }
        }

        return super.onTouchEvent(event);
    }

    // endregion
}
