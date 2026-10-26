package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.RecoveryService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// 自动恢复的防抖 / 周期调度器（主设计 §8.4）。
///
/// 本类只决定“何时捕获”，不编码、不写文件；捕获交给 [RecoveryService#capture]。
/// 一次编辑后的防抖捕获与周期捕获可能同时到期，但实例内同一时刻只发起一次捕获：上一次
/// 异步写入完成前到达的触发被丢弃，完成后再接受下一次。关闭后不再调度，已提交的写入
/// 由恢复服务自行完成。
///
/// ## 可测试时间源
///
/// 构造时注入 [Timer]，测试可以手动推进任务，不依赖真实线程/睡眠；生产实现可用
/// [ScheduledTimer] 包装 `ScheduledExecutorService`。本类本身不在调用线程写磁盘。
@NullMarked
public final class CoreRecoveryScheduler implements AutoCloseable {

    private final Timer timer;
    private final Supplier<AnimationSnapshot> snapshots;
    private final Supplier<ResourceId> resources;
    private final RecoveryService recovery;
    private final Consumer<SaveResult> completion;
    private final long debounceMillis;
    private final long periodMillis;
    private final AtomicBoolean capturing = new AtomicBoolean();

    private Timer.@Nullable Handle debounce;
    private Timer.@Nullable Handle periodic;
    private boolean started;
    private boolean closed;

    /// @param timer           时间源；任务执行线程由实现决定
    /// @param snapshots       当前已发布快照提供者；返回 `null` 时跳过本次捕获
    /// @param resources       当前目标资源提供者；返回 `null` 时跳过本次捕获
    /// @param recovery        恢复写入服务
    /// @param completion      捕获完成回调；每个实际捕获恰好一次
    /// @param debounceMillis  编辑防抖延迟，非负
    /// @param periodMillis    周期捕获间隔，正数
    public CoreRecoveryScheduler(Timer timer,
                                 Supplier<AnimationSnapshot> snapshots,
                                 Supplier<ResourceId> resources,
                                 RecoveryService recovery,
                                 Consumer<SaveResult> completion,
                                 long debounceMillis,
                                 long periodMillis) {
        this.timer = Objects.requireNonNull(timer, "timer");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.completion = Objects.requireNonNull(completion, "completion");

        if (debounceMillis < 0) {
            throw new IllegalArgumentException("debounceMillis must be non-negative: " + debounceMillis);
        }

        if (periodMillis < 1) {
            throw new IllegalArgumentException("periodMillis must be positive: " + periodMillis);
        }

        this.debounceMillis = debounceMillis;
        this.periodMillis = periodMillis;
    }

    /// 已发布快照变化后调用：取消尚未到期的防抖任务，按延迟重新调度。
    public void onPublished() {
        if (closed) {
            return;
        }

        if (debounce != null) {
            debounce.cancel();
        }

        debounce = timer.schedule(this::captureOnce, debounceMillis);
    }

    /// 启动周期捕获；重复启动无副作用。
    public void start() {
        if (started || closed) {
            return;
        }

        started = true;
        periodic = timer.scheduleAtFixedRate(this::captureOnce, periodMillis, periodMillis);
    }

    /// 停止调度并取消待执行任务；重复关闭无副作用。
    @Override
    public void close() {
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

    private void captureOnce() {
        if (closed || !capturing.compareAndSet(false, true)) {
            return;
        }

        AnimationSnapshot snapshot;
        ResourceId resource;

        try {
            snapshot = snapshots.get();
            resource = resources.get();
        } catch (RuntimeException exception) {
            capturing.set(false);
            throw exception;
        }

        if (snapshot == null || resource == null) {
            capturing.set(false);
            return;
        }

        try {
            recovery.capture(snapshot, resource, result -> {
                try {
                    completion.accept(result);
                } finally {
                    capturing.set(false);
                }
            });
        } catch (RuntimeException exception) {
            capturing.set(false);
            throw exception;
        }
    }

    /// 可注入的时间源。
    @NullMarked
    public interface Timer {

        /// 延迟执行一次；返回可取消句柄。
        Handle schedule(Runnable task, long delayMillis);

        /// 固定间隔执行；返回可取消句柄。
        Handle scheduleAtFixedRate(Runnable task, long initialDelayMillis, long periodMillis);

        /// 取消句柄。
        interface Handle {
            void cancel();
        }
    }
}
