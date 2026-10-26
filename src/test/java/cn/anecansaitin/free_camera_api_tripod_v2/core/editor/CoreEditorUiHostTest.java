package cn.anecansaitin.free_camera_api_tripod_v2.core.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorBackendSelector;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorUiBackend;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 编辑器 UI 后端选择、拒绝接管与内置回退（主设计 §9.3）。
class CoreEditorUiHostTest {

    private static final Identifier DEFAULT_ID = id("default");

    @Test
    void autoChoosesPriorityThenStableIdAndSkipsDecliners() {
        TestBackend low = new TestBackend(id("z-low"), 5, true);
        TestBackend highZ = new TestBackend(id("z-high"), 10, true);
        TestBackend highA = new TestBackend(id("a-high"), 10, true);
        TestBackend declining = new TestBackend(id("a-decline"), 20, false);
        TestBackend defaultBackend = new TestBackend(DEFAULT_ID, 0, true);
        Recorder recorder = new Recorder();

        CoreEditorUiHost host = new CoreEditorUiHost(
                List.of(low, highZ, highA, declining), defaultBackend, recorder);

        host.open(session(), EditorBackendSelector.auto());

        assertTrue(highA.opened);
        assertFalse(highZ.opened);
        assertFalse(low.opened);
        assertFalse(declining.opened);
        assertFalse(defaultBackend.opened);
        assertTrue(recorder.warnings.isEmpty());
        assertTrue(recorder.failures.isEmpty());
    }

    @Test
    void autoFallsBackToDefaultWhenAllThirdPartyBackendsDecline() {
        TestBackend first = new TestBackend(id("first"), 1, false);
        TestBackend second = new TestBackend(id("second"), 2, false);
        TestBackend defaultBackend = new TestBackend(DEFAULT_ID, 0, true);
        Recorder recorder = new Recorder();

        CoreEditorUiHost host = new CoreEditorUiHost(
                List.of(first, second), defaultBackend, recorder);

        host.open(session(), EditorBackendSelector.auto());

        assertTrue(defaultBackend.opened);
        assertFalse(first.opened);
        assertFalse(second.opened);
        assertTrue(recorder.failures.isEmpty());
    }

    @Test
    void specifiedBackendOpensWithoutConsultingDefault() {
        TestBackend selected = new TestBackend(id("selected"), 1, true);
        TestBackend defaultBackend = new TestBackend(DEFAULT_ID, 0, true);
        Recorder recorder = new Recorder();

        CoreEditorUiHost host = new CoreEditorUiHost(
                List.of(selected), defaultBackend, recorder);

        host.open(session(), EditorBackendSelector.of(selected.id()));

        assertTrue(selected.opened);
        assertFalse(defaultBackend.opened);
        assertTrue(recorder.failures.isEmpty());
    }

    @Test
    void specifiedBackendDecliningDoesNotFallBack() {
        TestBackend selected = new TestBackend(id("selected"), 1, false);
        TestBackend defaultBackend = new TestBackend(DEFAULT_ID, 0, true);
        Recorder recorder = new Recorder();

        CoreEditorUiHost host = new CoreEditorUiHost(
                List.of(selected), defaultBackend, recorder);

        host.open(session(), EditorBackendSelector.of(selected.id()));

        assertFalse(selected.opened);
        assertFalse(defaultBackend.opened);
        assertEquals(1, recorder.failures.size());
    }

    @Test
    void unknownBackendWarnsAndOpensDefault() {
        TestBackend defaultBackend = new TestBackend(DEFAULT_ID, 0, true);
        Recorder recorder = new Recorder();
        CoreEditorUiHost host = new CoreEditorUiHost(defaultBackend, recorder);

        host.open(session(), EditorBackendSelector.of(id("missing")));

        assertTrue(defaultBackend.opened);
        assertEquals(1, recorder.warnings.size());
        assertTrue(recorder.failures.isEmpty());
    }

    @Test
    void openFailureIsReportedWithoutFallback() {
        TestBackend selected = new TestBackend(id("selected"), 1, true);
        selected.openFailure = new IllegalStateException("open failed");
        TestBackend defaultBackend = new TestBackend(DEFAULT_ID, 0, true);
        Recorder recorder = new Recorder();
        CoreEditorUiHost host = new CoreEditorUiHost(
                List.of(selected), defaultBackend, recorder);

        host.open(session(), EditorBackendSelector.of(selected.id()));

        assertFalse(defaultBackend.opened);
        assertEquals(1, recorder.failures.size());
        assertEquals(selected.openFailure, recorder.failures.getFirst().cause());
    }

    @Test
    void canOpenFailureInAutoContinuesToNextBackend() {
        TestBackend broken = new TestBackend(id("broken"), 10, true);
        broken.canOpenFailure = new IllegalArgumentException("cannot inspect");
        TestBackend fallback = new TestBackend(id("fallback"), 1, true);
        TestBackend defaultBackend = new TestBackend(DEFAULT_ID, 0, true);
        Recorder recorder = new Recorder();
        CoreEditorUiHost host = new CoreEditorUiHost(
                List.of(broken, fallback), defaultBackend, recorder);

        host.open(session(), EditorBackendSelector.auto());

        assertTrue(fallback.opened);
        assertFalse(broken.opened);
        assertFalse(defaultBackend.opened);
        assertEquals(1, recorder.failures.size());
    }

    @Test
    void duplicateBackendIdsAreRejected() {
        TestBackend first = new TestBackend(id("same"), 1, true);
        TestBackend second = new TestBackend(id("same"), 2, true);
        TestBackend defaultBackend = new TestBackend(DEFAULT_ID, 0, true);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CoreEditorUiHost(List.of(first, second), defaultBackend,
                        new Recorder()));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }

    private static EditorSession session() {
        return (EditorSession) Proxy.newProxyInstance(
                EditorSession.class.getClassLoader(),
                new Class<?>[]{EditorSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "test-editor-session";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static @Nullable Object defaultValue(Class<?> type) {
        if (type == void.class) {
            return null;
        }

        if (!type.isPrimitive()) {
            return null;
        }

        if (type == boolean.class) {
            return false;
        }

        if (type == char.class) {
            return '\0';
        }

        if (type == float.class) {
            return 0f;
        }

        if (type == double.class) {
            return 0d;
        }

        if (type == long.class) {
            return 0L;
        }

        if (type == byte.class) {
            return (byte) 0;
        }

        if (type == short.class) {
            return (short) 0;
        }

        return 0;
    }

    private static final class TestBackend implements EditorUiBackend {
        private final Identifier id;
        private final int priority;
        private final boolean canOpen;
        private boolean opened;
        private @Nullable RuntimeException canOpenFailure;
        private @Nullable RuntimeException openFailure;

        private TestBackend(Identifier id, int priority, boolean canOpen) {
            this.id = id;
            this.priority = priority;
            this.canOpen = canOpen;
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean canOpen(EditorSession session) {
            if (canOpenFailure != null) {
                throw canOpenFailure;
            }

            return canOpen;
        }

        @Override
        public void open(EditorSession session) {
            if (openFailure != null) {
                throw openFailure;
            }

            opened = true;
        }
    }

    private record Failure(LocalizedMessage message, @Nullable RuntimeException cause) {
    }

    private static final class Recorder implements CoreEditorUiHost.Reporter {
        private final List<LocalizedMessage> warnings = new ArrayList<>();
        private final List<Failure> failures = new ArrayList<>();

        @Override
        public void warning(LocalizedMessage message) {
            warnings.add(message);
        }

        @Override
        public void failure(LocalizedMessage message, @Nullable RuntimeException cause) {
            failures.add(new Failure(message, cause));
        }
    }
}
