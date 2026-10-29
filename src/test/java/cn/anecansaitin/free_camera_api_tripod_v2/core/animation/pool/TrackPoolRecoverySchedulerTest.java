package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.storage.CoreRecoveryScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池恢复调度：防抖、周期、立即保存与关闭。
class TrackPoolRecoverySchedulerTest {

    @TempDir
    Path directory;

    @Test
    void debounceAndPeriodicSaveAtomically() {
        ManualTimer timer = new ManualTimer();
        DocumentRegistries registries = registries();
        TrackPoolDocumentStore store = new TrackPoolDocumentStore(document());
        Path target = directory.resolve("recovery.json");
        TrackPoolRecoveryScheduler scheduler = new TrackPoolRecoveryScheduler(
                store, new TrackPoolFileService(), registries, target, timer, 10, 100);

        scheduler.start();
        scheduler.onPublished();
        timer.runAll();

        assertTrue(Files.isRegularFile(target));
        assertTrue(new TrackPoolFileService().read(target, registries).result().isPresent());

        timer.runPeriodic();
        assertTrue(Files.isRegularFile(target));

        scheduler.close();
        assertFalse(scheduler.captureNow());
    }

    @Test
    void captureNowWritesWithoutTimer() {
        DocumentRegistries registries = registries();
        Path target = directory.resolve("now.json");
        TrackPoolRecoveryScheduler scheduler = new TrackPoolRecoveryScheduler(
                new TrackPoolDocumentStore(document()), new TrackPoolFileService(), registries,
                target, new ManualTimer(), 0, 1000);

        assertTrue(scheduler.captureNow());
        assertTrue(Files.isRegularFile(target));
    }

    private static TrackPoolAnimation document() {
        return TrackPoolAnimation.create("doc", PooledRig.create(new RigId("rig"), "rig"));
    }

    private static DocumentRegistries registries() {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        anchors.freeze();
        tracks.freeze();
        samples.freeze();
        return new DocumentRegistries(anchors, tracks, samples);
    }

    private static final class ManualTimer implements CoreRecoveryScheduler.Timer {
        private final List<Runnable> scheduled = new ArrayList<>();
        private final List<Runnable> periodic = new ArrayList<>();

        @Override
        public Handle schedule(Runnable task, long delayMillis) {
            scheduled.add(task);
            return () -> scheduled.remove(task);
        }

        @Override
        public Handle scheduleAtFixedRate(Runnable task, long initialDelayMillis, long periodMillis) {
            periodic.add(task);
            return () -> periodic.remove(task);
        }

        void runAll() {
            List<Runnable> tasks = new ArrayList<>(scheduled);
            scheduled.clear();
            tasks.forEach(Runnable::run);
        }

        void runPeriodic() {
            new ArrayList<>(periodic).forEach(Runnable::run);
        }
    }
}
