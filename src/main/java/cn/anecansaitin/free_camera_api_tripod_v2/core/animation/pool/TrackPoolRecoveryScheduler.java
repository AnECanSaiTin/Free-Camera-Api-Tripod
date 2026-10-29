package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.core.storage.CoreRecoveryScheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/// 轨道池自动恢复：发布后防抖保存 + 固定周期保存。
///
/// 写入走 [TrackPoolFileService] 的原子替换；保存失败不发布、不删除原文件。时间源由调用方
/// 注入，测试不需要真实线程/真实时间。
@NullMarked
public final class TrackPoolRecoveryScheduler implements AutoCloseable {

    private final TrackPoolDocumentStore store;
    private final TrackPoolFileService files;
    private final DocumentRegistries registries;
    private final Path target;
    private final CoreRecoveryScheduler.Timer timer;
    private final long debounceMillis;
    private final long periodMillis;
    private final AtomicBoolean capturing = new AtomicBoolean();
    private CoreRecoveryScheduler.Timer.@Nullable Handle debounce;
    private CoreRecoveryScheduler.Timer.@Nullable Handle periodic;
    private boolean started;
    private boolean closed;

    public TrackPoolRecoveryScheduler(TrackPoolDocumentStore store,
                                      TrackPoolFileService files,
                                      DocumentRegistries registries,
                                      Path target,
                                      CoreRecoveryScheduler.Timer timer,
                                      long debounceMillis,
                                      long periodMillis) {
        this.store = Objects.requireNonNull(store, "store");
        this.files = Objects.requireNonNull(files, "files");
        this.registries = Objects.requireNonNull(registries, "registries");
        this.target = Objects.requireNonNull(target, "target");
        this.timer = Objects.requireNonNull(timer, "timer");

        if (debounceMillis < 0 || periodMillis <= 0) {
            throw new IllegalArgumentException("Recovery delays must be non-negative / positive");
        }

        this.debounceMillis = debounceMillis;
        this.periodMillis = periodMillis;
    }

    /// 启动周期保存。
    public synchronized void start() {
        if (closed || started) {
            return;
        }

        started = true;
        periodic = timer.scheduleAtFixedRate(this::captureOnce, periodMillis, periodMillis);
    }

    /// 文档发布后安排一次防抖保存。
    public synchronized void onPublished() {
        if (closed || !started) {
            return;
        }

        if (debounce != null) {
            debounce.cancel();
        }

        debounce = timer.schedule(this::captureOnce, debounceMillis);
    }

    /// 立即保存一次。
    public boolean captureNow() {
        return captureOnce();
    }

    private boolean captureOnce() {
        if (closed || !capturing.compareAndSet(false, true)) {
            return false;
        }

        try {
            return files.write(target, store.snapshot().document(), registries).error().isEmpty();
        } catch (RuntimeException exception) {
            return false;
        } finally {
            capturing.set(false);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;

        if (debounce != null) {
            debounce.cancel();
            debounce = null;
        }

        if (periodic != null) {
            periodic.cancel();
            periodic = null;
        }
    }
}
