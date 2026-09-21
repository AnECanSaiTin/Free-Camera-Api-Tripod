package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Evaluator;
import cn.anecansaitin.free_camera_api_tripod.core.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Clip;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Curve;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Path;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CameraPose;
import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;

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
    public void tick(float deltaSeconds) {
        if (state != State.PLAYING) {
            return;
        }

        float duration = Math.max(animation.duration(), 0f);
        time += deltaSeconds * speed;

        if (time < duration) {
            return;
        }

        if (loop && duration > 0) {
            time %= duration;
        } else {
            time = duration;
            state = State.PAUSED;
        }
    }

    /// 求值当前时间的相机姿态
    public CameraPose evaluatePose(CameraPose dest) {
        Clip clip = animation.clip();
        Path path = animation.path();

        if (path.size() > 0) {
            float distance = clip.evaluate(CameraAnimation.CHANNEL_POSITION, time);
            dest.position().set(path.evaluate(distance, posCache));
            dest.positionValid(true);
        } else {
            dest.positionValid(false);
        }

        dest.rotation().set(clip.evaluate(time, rotEvaluator));
        Curve fovCurve = clip.curve(CameraAnimation.CHANNEL_FOV);

        if (fovCurve != null && fovCurve.size() > 0) {
            dest.fov(clip.evaluate(CameraAnimation.CHANNEL_FOV, time));
            dest.fovValid(true);
        } else {
            // fov 通道没有关键帧时求值会得到 0，会把画面压成一个点；交给原版沿用玩家自己的 FOV
            dest.fovValid(false);
        }

        return dest;
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
