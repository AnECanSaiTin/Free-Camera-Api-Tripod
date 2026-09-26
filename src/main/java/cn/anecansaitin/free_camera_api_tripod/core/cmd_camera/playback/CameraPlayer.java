package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Evaluator;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Clip;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ExpressionContext;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.TickTrack;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CameraPose;
import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 播放器：只负责时间推进与姿态求值，不持有编辑状态。
///
/// 时间以秒为单位，由调用方按帧提供 delta 秒数推进。
@NullMarked
public class CameraPlayer {
    public enum State {
        STOPPED,
        PLAYING,
        PAUSED
    }

    private final CameraAnimation animation;
    private final Vector3f posCache = new Vector3f();
    private final RotEvaluator rotEvaluator = new RotEvaluator();
    private State state = State.STOPPED;
    private float time;
    private float speed = 1f;
    private boolean loop = false;

    public CameraPlayer(CameraAnimation animation) {
        this.animation = animation;
    }

    /// 推进时间；仅在播放状态下生效
    ///
    /// 推进时把这一帧走过的时间区间交给「经过即触发」的扩展轨道（命令、事件等），
    /// 循环回绕与末尾停住都不会漏掉或重复触发端点上的键
    public void tick(float deltaSeconds) {
        if (state != State.PLAYING) {
            return;
        }

        float duration = Math.max(animation.duration(), 0f);
        float previous = time;
        time += deltaSeconds * speed;

        if (time < duration) {
            advanceTracks(previous, time);
            return;
        }

        if (loop && duration > 0) {
            time %= duration;
            // 回绕：先走到结尾，再从头走到新位置，跨过 0 的键不会被跳过
            advanceTracks(previous, duration);
            advanceTracks(0f, time);
        } else {
            time = duration;
            state = State.PAUSED;
            // 停在末尾前把最后一段走完，末尾那一帧的键仍会触发
            advanceTracks(previous, duration);
        }
    }

    /// 把时间区间分发给实现了 {@link TickTrack} 的扩展轨道
    private void advanceTracks(float fromTime, float toTime) {
        if (!(toTime > fromTime)) {
            return;
        }

        for (AnimationTrack track : animation.extensionTracks()) {
            if (track instanceof TickTrack tickTrack) {
                tickTrack.advance(fromTime, toTime);
            }
        }
    }

    /// 求值当前时间的相机姿态
    public CameraPose evaluatePose(CameraPose dest) {
        Clip clip = animation.clip();
        Path path = animation.path();
        // 每帧一份求值上下文：挂了公式的关键帧 / 路径节点按当前时间算，变量也按当前时间取轨道读数
        ExpressionContext expression = new ExpressionContext(animation, time);

        if (animation.motionMode() == CameraAnimation.MotionMode.COORDINATE) {
            // 直接坐标模式：位置由三个坐标通道给出，与路径无关。
            // 某个轴上没有关键帧时不接管该轴，让相机沿用原本的坐标（等于不开启该轴的修改）。
            Curve x = clip.curve(CameraAnimation.CHANNEL_POSITION_X);
            Curve y = clip.curve(CameraAnimation.CHANNEL_POSITION_Y);
            Curve z = clip.curve(CameraAnimation.CHANNEL_POSITION_Z);
            boolean any = hasKeys(x) || hasKeys(y) || hasKeys(z);

            if (any) {
                Vector3f position = dest.position();

                if (hasKeys(x)) {
                    position.x = finiteOr(clip.evaluate(CameraAnimation.CHANNEL_POSITION_X, time, expression), position.x);
                }

                if (hasKeys(y)) {
                    position.y = finiteOr(clip.evaluate(CameraAnimation.CHANNEL_POSITION_Y, time, expression), position.y);
                }

                if (hasKeys(z)) {
                    position.z = finiteOr(clip.evaluate(CameraAnimation.CHANNEL_POSITION_Z, time, expression), position.z);
                }
            }

            dest.positionValid(any);
        } else if (path.size() > 0) {
            // 位置通道的取值口径由动画决定：绝对距离直接用，百分比先乘总长再采样
            float distance = animation.distanceToLength(clip.evaluate(CameraAnimation.CHANNEL_POSITION, time, expression));
            Vector3f evaluated = path.evaluate(distance, posCache, expression);

            // 路径数据异常（总长或切线非有限值）时算出的是 NaN，绝不能把它交给相机
            if (isFinite(evaluated)) {
                dest.position().set(evaluated);
                dest.positionValid(true);
            } else {
                dest.positionValid(false);
            }
        } else {
            dest.positionValid(false);
        }

        dest.rotation().set(clip.evaluate(time, rotEvaluator, expression));
        Curve fovCurve = clip.curve(CameraAnimation.CHANNEL_FOV);

        if (fovCurve != null && fovCurve.size() > 0) {
            dest.fov(clip.evaluate(CameraAnimation.CHANNEL_FOV, time, expression));
            dest.fovValid(true);
        } else {
            // fov 通道没有关键帧时求值会得到 0，会把画面压成一个点；交给原版沿用玩家自己的 FOV
            dest.fovValid(false);
        }

        return dest;
    }

    /// 曲线是否存在且至少有一个关键帧
    private static boolean hasKeys(@Nullable Curve curve) {
        return curve != null && curve.size() > 0;
    }

    /// 求值结果非有限值时沿用原值：坐标绝不能是 NaN / Infinity，否则相机与渲染都会出问题
    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static boolean isFinite(Vector3f vec) {
        return Float.isFinite(vec.x) && Float.isFinite(vec.y) && Float.isFinite(vec.z);
    }

    public void play() {
        if (state == State.PLAYING) {
            return;
        }

        if (time >= animation.duration()) {
            time = 0;
        }

        state = State.PLAYING;
    }

    public void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    public void toggle() {
        if (state == State.PLAYING) {
            pause();
        } else {
            play();
        }
    }

    public void stop() {
        state = State.STOPPED;
        time = 0;
    }

    /// 释放对相机的接管：暂停状态回到停止状态，但保留当前时间，便于再次打开编辑器时从原位置继续。
    /// 播放中不处理，让动画继续在世界中播放。
    public void release() {
        if (state == State.PAUSED) {
            state = State.STOPPED;
        }
    }

    /// 跳转到指定时间。
    ///
    /// 允许超出动画时长，方便在末尾之外继续排布新的关键帧。
    public void seek(float time) {
        this.time = Math.max(0f, time);
        state = State.PAUSED;
    }

    public State state() {
        return state;
    }

    public boolean playing() {
        return state == State.PLAYING;
    }

    public float time() {
        return time;
    }

    public void speed(float speed) {
        this.speed = speed;
    }

    public float speed() {
        return speed;
    }

    public boolean loop() {
        return loop;
    }

    public void loop(boolean loop) {
        this.loop = loop;
    }

    private static final class RotEvaluator implements Evaluator<Vector3f> {
        private static final String[] PROPERTIES = {
                CameraAnimation.CHANNEL_ROTATION_X,
                CameraAnimation.CHANNEL_ROTATION_Y,
                CameraAnimation.CHANNEL_ROTATION_Z
        };

        private final Vector3f vec = new Vector3f();

        @Override
        public String[] properties() {
            return PROPERTIES;
        }

        @Override
        public Vector3f build(float... values) {
            return vec.set(values[0], values[1], values[2]);
        }
    }
}
