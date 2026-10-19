package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 第三方事件执行器注册表（主设计 §7.4）。
class EventExecutorRegistryContractTest {

    private static EventExecutor executor() {
        return (frame, events) -> new EventDispatcher.Result(events.size(), null);
    }

    @Test
    void registryRejectsDuplicateAndBuiltInTypes() {
        Identifier custom = Identifier.fromNamespaceAndPath("test", "event");
        EventExecutorRegistry registry = new EventExecutorRegistry(
                List.of(new EventExecutorRegistry.Entry(custom, executor())));

        assertTrue(registry.find(custom).isPresent());

        assertThrows(IllegalArgumentException.class, () -> new EventExecutorRegistry(
                List.of(new EventExecutorRegistry.Entry(custom, executor()),
                        new EventExecutorRegistry.Entry(custom, executor()))));

        assertThrows(IllegalArgumentException.class, () -> new EventExecutorRegistry(
                List.of(new EventExecutorRegistry.Entry(CurveTrack.TYPE, executor()))));
        assertThrows(NullPointerException.class, () -> new EventExecutorRegistry.Entry(custom, null));
    }

    @Test
    void executorResultCarriesDispatchedCount() {
        assertEquals(0, executor().execute(null, List.of()).dispatched());
    }
}
