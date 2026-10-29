package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrameResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 轨道池播放控制器：时钟、播放/暂停、定位与逐帧准备。
///
/// 只驱动新轨道池模型；真实宿主每帧调用、相机应用与渲染不在这里。循环使用快照的
/// `playbackStart/End`，手动区间与自动时长由文档自身决定。
@NullMarked
public final class TrackPoolPlaybackController {

    private final TrackPoolDocumentStore store;
    private final TrackPoolFrameFactory frames;
    private final cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource samples;
    private float time;
    private boolean playing;

    public TrackPoolPlaybackController(TrackPoolDocumentStore store) {
        this(store, new TrackPoolFrameFactory());
    }

    public TrackPoolPlaybackController(TrackPoolDocumentStore store, TrackPoolFrameFactory frames) {
        this(store, frames,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource.NONE);
    }

    public TrackPoolPlaybackController(TrackPoolDocumentStore store,
                                       TrackPoolFrameFactory frames,
                                       cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource samples) {
        this.store = Objects.requireNonNull(store, "store");
        this.frames = Objects.requireNonNull(frames, "frames");
        this.samples = Objects.requireNonNull(samples, "samples");
        this.time = store.snapshot().document().playbackStartSeconds();
    }

    public boolean playing() {
        return playing;
    }

    public float time() {
        return time;
    }

    public void play() {
        playing = true;
    }

    public void pause() {
        playing = false;
    }

    /// 定位到指定时间；负值夹到 0。
    public void seek(float seconds) {
        if (!Float.isFinite(seconds)) {
            throw new IllegalArgumentException("seek time must be finite");
        }

        time = Math.max(0f, seconds);
    }

    /// 推进一帧并准备结果；暂停时不推进。
    public TrackPoolFrameResult tick(float deltaSeconds) {
        return tick(deltaSeconds, 0.0);
    }

    /// 推进一帧，并携带冻结世界时间。
    ///
    /// 动画进度由当前文档播放区间与当前时间计算；区间长度为 0 时进度为 0。
    public TrackPoolFrameResult tick(float deltaSeconds, double worldTime) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("deltaSeconds must be finite and non-negative");
        }

        if (!Double.isFinite(worldTime)) {
            throw new IllegalArgumentException("worldTime must be finite");
        }

        if (playing) {
            advance(deltaSeconds);
        }

        TrackPoolSnapshot snapshot = store.snapshot();
        float start = snapshot.document().playbackStartSeconds();
        float end = snapshot.document().playbackEndSeconds();
        float duration = end - start;
        double progress = duration > 0f
                ? Math.clamp((time - start) / duration, 0.0, 1.0)
                : 0.0;

        return frames.prepare(snapshot,
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs(
                        time, progress, worldTime),
                samples);
    }

    private void advance(float deltaSeconds) {
        TrackPoolSnapshot snapshot = store.snapshot();
        float start = snapshot.document().playbackStartSeconds();
        float end = snapshot.document().playbackEndSeconds();
        time += deltaSeconds;

        if (time < start) {
            time = start;
        }

        float duration = end - start;

        if (duration > 0f && time >= end) {
            float overflow = (time - start) % duration;
            time = start + overflow;
        }
    }
}
