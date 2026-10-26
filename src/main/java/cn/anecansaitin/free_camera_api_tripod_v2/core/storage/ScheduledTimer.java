package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/// 用 [ScheduledExecutorService] 驱动的恢复调度时间源。
///
/// 只负责把毫秒延迟/周期转成 JDK 调度；异常由任务自身处理，本类不吞掉任务异常。
@NullMarked
public final class ScheduledTimer implements CoreRecoveryScheduler.Timer {

    private final ScheduledExecutorService executor;

    /// @param executor 调度线程池，非空；生命周期由调用方管理
    public ScheduledTimer(ScheduledExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public Handle schedule(Runnable task, long delayMillis) {
        Objects.requireNonNull(task, "task");
        ScheduledFuture<?> future = executor.schedule(
                task, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public Handle scheduleAtFixedRate(Runnable task, long initialDelayMillis, long periodMillis) {
        Objects.requireNonNull(task, "task");
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                task, Math.max(0L, initialDelayMillis), Math.max(1L, periodMillis),
                TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }
}
