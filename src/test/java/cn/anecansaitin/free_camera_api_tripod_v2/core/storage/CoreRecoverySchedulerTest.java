package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.RecoveryDecision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.RecoveryService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.RecoverySlot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 恢复调度：防抖取消、周期、关闭与在途去重。
class CoreRecoverySchedulerTest {

    private static final ResourceId RESOURCE = new ResourceId("animations/test.json");

    @Test
    void debounceCancelsPreviousAndCapturesOnce() {
        ManualTimer timer = new ManualTimer();
        FakeRecovery recovery = new FakeRecovery();
        AtomicInteger completed = new AtomicInteger();
        CoreRecoveryScheduler scheduler = scheduler(timer, recovery, completed, 50, 1000);

        scheduler.onPublished();
        scheduler.onPublished();
        timer.runAll();

        assertEquals(1, recovery.captures.get());
        assertEquals(1, completed.get());
    }

    @Test
    void periodicCaptureAndCloseCancelFutureTasks() {
        ManualTimer timer = new ManualTimer();
        FakeRecovery recovery = new FakeRecovery();
        AtomicInteger completed = new AtomicInteger();
        CoreRecoveryScheduler scheduler = scheduler(timer, recovery, completed, 10, 100);

        scheduler.start();
        scheduler.start();
        timer.runAll();
        assertEquals(1, recovery.captures.get());

        scheduler.close();
        scheduler.onPublished();
        timer.runAll();
        assertEquals(1, recovery.captures.get());
        assertEquals(1, completed.get());
    }

    @Test
    void inFlightCaptureSuppressesOverlappingTriggers() {
        ManualTimer timer = new ManualTimer();
        FakeRecovery recovery = new FakeRecovery();
        recovery.hold = true;
        AtomicInteger completed = new AtomicInteger();
        CoreRecoveryScheduler scheduler = scheduler(timer, recovery, completed, 10, 100);

        scheduler.onPublished();
        timer.runAll();
        assertEquals(1, recovery.captures.get());

        scheduler.onPublished();
        timer.runAll();
        assertEquals(1, recovery.captures.get());

        recovery.finish();
        assertEquals(1, completed.get());
        recovery.hold = false;

        scheduler.onPublished();
        timer.runAll();
        assertEquals(2, recovery.captures.get());
        assertEquals(2, completed.get());
    }

    @Test
    void nullSnapshotSkipsCapture() {
        ManualTimer timer = new ManualTimer();
        FakeRecovery recovery = new FakeRecovery();
        AtomicInteger completed = new AtomicInteger();
        CoreRecoveryScheduler scheduler = new CoreRecoveryScheduler(
                timer, () -> null, () -> RESOURCE, recovery, result -> completed.incrementAndGet(),
                10, 100);

        scheduler.onPublished();
        timer.runAll();

        assertEquals(0, recovery.captures.get());
        assertEquals(0, completed.get());
    }

    private static CoreRecoveryScheduler scheduler(ManualTimer timer,
                                                   FakeRecovery recovery,
                                                   AtomicInteger completed,
                                                   long debounce,
                                                   long period) {
        AnimationSnapshot snapshot = snapshot();
        return new CoreRecoveryScheduler(timer, () -> snapshot, () -> RESOURCE, recovery,
                result -> completed.incrementAndGet(), debounce, period);
    }

    private static AnimationSnapshot snapshot() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"));
        return new AnimationSnapshot(Revision.INITIAL, document);
    }

    private static final class ManualTimer implements CoreRecoveryScheduler.Timer {
        private final List<Task> tasks = new ArrayList<>();

        @Override
        public Handle schedule(Runnable task, long delayMillis) {
            Task entry = new Task(task);
            tasks.add(entry);
            return entry;
        }

        @Override
        public Handle scheduleAtFixedRate(Runnable task, long initialDelayMillis, long periodMillis) {
            Task entry = new Task(task);
            tasks.add(entry);
            return entry;
        }

        private void runAll() {
            List<Task> snapshot = List.copyOf(tasks);
            tasks.clear();

            for (Task task : snapshot) {
                if (!task.cancelled) {
                    task.runnable.run();
                }
            }
        }

        private static final class Task implements Handle {
            private final Runnable runnable;
            private boolean cancelled;

            private Task(Runnable runnable) {
                this.runnable = runnable;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        }
    }

    private static final class FakeRecovery implements RecoveryService {
        private final AtomicInteger captures = new AtomicInteger();
        private boolean hold;
        private Runnable pending;

        @Override
        public List<RecoverySlot> slots() {
            return List.of();
        }

        @Override
        public void capture(AnimationSnapshot snapshot, ResourceId resource,
                            Consumer<SaveResult> completion) {
            captures.incrementAndGet();

            if (hold) {
                pending = () -> completion.accept(null);
            } else {
                completion.accept(null);
            }
        }

        private void finish() {
            assertTrue(pending != null);
            Runnable runnable = pending;
            pending = null;
            runnable.run();
        }

        @Override
        public LoadResult restore(RecoverySlot slot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void discard(RecoverySlot slot) {
        }

        @Override
        public void decide(RecoverySlot slot, RecoveryDecision decision) {
        }
    }
}
