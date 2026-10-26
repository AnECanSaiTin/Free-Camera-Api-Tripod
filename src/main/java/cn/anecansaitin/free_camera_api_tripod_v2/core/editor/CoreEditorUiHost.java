package cn.anecansaitin.free_camera_api_tripod_v2.core.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorBackendSelector;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorUiBackend;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorUiHost;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// 编辑器 UI 宿主的 core 编排实现（主设计 §9.3）。
///
/// 只负责后端选择与 `canOpen` / `open` 调用顺序，不持有屏幕、不依赖客户端渲染类型，也不
/// 关闭会话。内置后端由宿主构造时显式注入，自动选择始终把它放在最后；同优先级按 ID 的
/// 稳定字符串顺序。
///
/// ## 选择规则
///
/// | `editor.backend` | 行为 |
/// | --- | --- |
/// | `auto` | 非内置后端按优先级降序、同优先级按 ID 稳定排序；逐个 `canOpen`，第一个接受者打开；都不接受时打开内置 |
/// | 指定已注册且接受 | 使用指定后端 |
/// | 指定已注册但拒绝 | 报告失败，不调用内置、不自动切换 |
/// | 指定未注册 | 报告警告并打开内置 |
///
/// `canOpen` 抛异常视为拒绝；`auto` 继续尝试下一个，指定 ID 则报告失败且不回退。`open`
/// 抛异常只报告失败，不关闭会话、不遗留令牌由后端自己保证。所有回调在调用线程同步执行。
@NullMarked
public final class CoreEditorUiHost implements EditorUiHost {

    private final List<EditorUiBackend> backends;
    private final EditorUiBackend defaultBackend;
    private final Reporter reporter;

    /// @param backends       第三方 / 可选后端；不得与内置后端 ID 重复
    /// @param defaultBackend 内置默认后端；自动选择的最后候选
    /// @param reporter       警告 / 失败接收者；不得为 null
    public CoreEditorUiHost(List<EditorUiBackend> backends,
                            EditorUiBackend defaultBackend,
                            Reporter reporter) {
        this.backends = List.copyOf(Objects.requireNonNull(backends, "backends"));
        this.defaultBackend = Objects.requireNonNull(defaultBackend, "defaultBackend");
        this.reporter = Objects.requireNonNull(reporter, "reporter");

        Set<Identifier> ids = new HashSet<>();

        for (EditorUiBackend backend : this.backends) {
            Objects.requireNonNull(backend, "backend");

            if (!ids.add(backend.id())) {
                throw new IllegalArgumentException(
                        "Duplicate editor UI backend id: " + backend.id());
            }
        }

        if (!ids.add(defaultBackend.id())) {
            throw new IllegalArgumentException(
                    "Default editor UI backend id duplicates a registered backend: "
                            + defaultBackend.id());
        }
    }

    /// 无第三方后端、只打开内置实现。
    public CoreEditorUiHost(EditorUiBackend defaultBackend, Reporter reporter) {
        this(List.of(), defaultBackend, reporter);
    }

    @Override
    public void open(EditorSession session, EditorBackendSelector selector) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(selector, "selector");

        if (selector.isAuto()) {
            openAuto(session);
            return;
        }

        Identifier id = Objects.requireNonNull(selector.backendId(), "backendId");
        EditorUiBackend selected = find(id);

        if (selected == null) {
            reporter.warning(LocalizedMessage.literal(
                    "Unknown editor UI backend '" + id + "'; opening the built-in backend"));
            openDefault(session);
            return;
        }

        if (!canOpen(selected, session)) {
            reporter.failure(LocalizedMessage.literal(
                    "Editor UI backend '" + id + "' declined to open the session"), null);
            return;
        }

        openBackend(selected, session);
    }

    @Override
    public void openDefault(EditorSession session) {
        Objects.requireNonNull(session, "session");

        if (!canOpen(defaultBackend, session)) {
            reporter.failure(LocalizedMessage.literal(
                    "Built-in editor UI backend declined to open the session"), null);
            return;
        }

        openBackend(defaultBackend, session);
    }

    private void openAuto(EditorSession session) {
        List<EditorUiBackend> candidates = new ArrayList<>(backends);
        candidates.sort(Comparator
                .comparingInt(EditorUiBackend::priority).reversed()
                .thenComparing(backend -> backend.id().toString()));

        for (EditorUiBackend backend : candidates) {
            if (canOpen(backend, session)) {
                openBackend(backend, session);
                return;
            }
        }

        openDefault(session);
    }

    private @Nullable EditorUiBackend find(Identifier id) {
        if (defaultBackend.id().equals(id)) {
            return defaultBackend;
        }

        for (EditorUiBackend backend : backends) {
            if (backend.id().equals(id)) {
                return backend;
            }
        }

        return null;
    }

    private boolean canOpen(EditorUiBackend backend, EditorSession session) {
        try {
            return backend.canOpen(session);
        } catch (RuntimeException exception) {
            reporter.failure(LocalizedMessage.literal(
                    "Editor UI backend '" + backend.id() + "' failed in canOpen"), exception);
            return false;
        }
    }

    private void openBackend(EditorUiBackend backend, EditorSession session) {
        try {
            backend.open(session);
        } catch (RuntimeException exception) {
            reporter.failure(LocalizedMessage.literal(
                    "Editor UI backend '" + backend.id() + "' failed to open"), exception);
        }
    }

    /// 后端选择过程中产生的警告 / 失败回调。
    ///
    /// 失败允许携带异常；警告的 `cause` 为 `null`。实现不得关闭会话或修改文档。
    @NullMarked
    public interface Reporter {

        /// 未注册 ID 等可继续的警告。
        void warning(LocalizedMessage message);

        /// 拒绝接管或打开失败；`cause` 可为空。
        void failure(LocalizedMessage message, @Nullable RuntimeException cause);

        /// 忽略所有报告。
        static Reporter noop() {
            return new Reporter() {
                @Override
                public void warning(LocalizedMessage message) {
                }

                @Override
                public void failure(LocalizedMessage message, @Nullable RuntimeException cause) {
                }
            };
        }
    }
}
