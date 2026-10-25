package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationDocumentStore;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.DocumentListener;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditSubmission;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.SessionSwitch;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.UndoHistory;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DocumentValidator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.validation.UnifiedDocumentValidator;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/// 纯内核文档存储：发布、历史、换档与延迟提交通道。
///
/// 这是 [AnimationDocumentStore] 的第一份实现，负责把“应用编辑操作 → 强制校验 →
/// 更新快照/历史 → 通知监听者”的顺序固定下来。它不依赖客户端线程实现，也不执行 IO；
/// 宿主应把调用限制在文档线程，尽管本类用同步方法保护内部状态，避免通知期间的并发撕裂。
///
/// ## 已落实的边界
///
/// - 过期提交返回 `STALE_REVISION`，不修改文档、历史或序号。
/// - 通知期间同步提交返回 `REENTRANT_SUBMIT`；延迟提交进入队列，通知结束后按提交顺序
///   重新检查基版本。
/// - 撤销/重做与换档都生成新的 [Revision]，不重置、不回退。
/// - 内容无变化的提交不产生新快照、不写历史。
/// - 校验存在 `ERROR` 时整体拒绝，不发布部分候选。
///
/// ## 尚未实现
///
/// 历史项只按 64 项上限裁剪；文档规模上限与真实内存估算留给后续宿主批次。该类不负责
/// 结构解码、路径安全、资源 IO 或跨线程调度。
@NullMarked
public final class CoreAnimationDocumentStore implements AnimationDocumentStore {

    /// 历史项默认上限（主设计 §4.5）。
    public static final int MAX_HISTORY_ENTRIES = 64;

    private final DocumentValidatorFactory validatorFactory;
    private final CopyOnWriteArrayList<DocumentListener> listeners = new CopyOnWriteArrayList<>();
    private final Deque<UndoHistory.Entry> undoStack = new ArrayDeque<>();
    private final Deque<UndoHistory.Entry> redoStack = new ArrayDeque<>();
    private final Deque<PendingSubmission> pending = new ArrayDeque<>();

    private volatile AnimationSnapshot snapshot;
    private boolean notifying;
    private boolean draining;
    private long nextRequestId;

    /// 以一个已通过解码与校验的初始文档建立存储。
    ///
    /// @param initialDocument  初始文档；必须非空
    /// @param validatorFactory 按候选发布序号构造统一校验器
    public CoreAnimationDocumentStore(CameraAnimation initialDocument,
                                      DocumentValidatorFactory validatorFactory) {
        Objects.requireNonNull(initialDocument, "initialDocument");
        this.validatorFactory = Objects.requireNonNull(validatorFactory, "validatorFactory");
        this.snapshot = new AnimationSnapshot(Revision.INITIAL, initialDocument);
        this.undoStack.push(new UndoHistory.Entry(
                snapshot, LocalizedMessage.literal("initial document")));
    }

    /// 使用固定校验器（不依赖发布序号）的便捷构造。
    public static CoreAnimationDocumentStore withFixedValidator(CameraAnimation initialDocument,
                                                                DocumentValidator validator) {
        return new CoreAnimationDocumentStore(initialDocument,
                DocumentValidatorFactory.fixed(validator));
    }

    /// 使用统一文档校验器；每条诊断按本次候选的基版本构造。
    public static CoreAnimationDocumentStore withUnifiedValidation(CameraAnimation initialDocument,
                                                                    DocumentRegistries registries) {
        return new CoreAnimationDocumentStore(initialDocument,
                revision -> new UnifiedDocumentValidator(registries, revision));
    }

    @Override
    public AnimationSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public synchronized EditResult submit(EditSubmission submission) {
        Objects.requireNonNull(submission, "submission");

        if (notifying) {
            return reentrantRejection();
        }

        return submitInternal(submission);
    }

    @Override
    public synchronized long submitAfterNotifications(EditSubmission submission,
                                                      Consumer<EditResult> completion) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(completion, "completion");

        long requestId = nextRequestId++;

        if (!notifying) {
            completion.accept(submitInternal(submission));
            return requestId;
        }

        pending.addLast(new PendingSubmission(requestId, submission, completion));
        return requestId;
    }

    @Override
    public synchronized EditResult switchDocument(CameraAnimation document,
                                                  Revision expectedRevision,
                                                  SessionSwitch reason) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        Objects.requireNonNull(reason, "reason");

        if (notifying) {
            return reentrantRejection();
        }

        if (!snapshot.revision().equals(expectedRevision)) {
            return stale(expectedRevision);
        }

        List<Diagnostic> diagnostics = validate(document, snapshot.revision());

        if (hasError(diagnostics)) {
            return new EditResult.Rejected(diagnostics);
        }

        AnimationSnapshot next = new AnimationSnapshot(snapshot.revision().next(), document);
        snapshot = next;
        undoStack.clear();
        redoStack.clear();
        undoStack.push(new UndoHistory.Entry(next, LocalizedMessage.literal("switch: " + reason.name())));
        notifyListeners(next);

        return new EditResult.Applied(next, diagnostics);
    }

    @Override
    public synchronized EditResult undo(Revision expectedRevision) {
        Objects.requireNonNull(expectedRevision, "expectedRevision");

        if (notifying) {
            return reentrantRejection();
        }

        if (!snapshot.revision().equals(expectedRevision)) {
            return stale(expectedRevision);
        }

        if (undoStack.size() <= 1) {
            return reject(DiagnosticCode.INVALID_EDIT, "No document state to undo");
        }

        UndoHistory.Entry current = undoStack.pop();
        UndoHistory.Entry previous = undoStack.pop();
        AnimationSnapshot next = new AnimationSnapshot(
                snapshot.revision().next(), previous.snapshot().document());

        undoStack.push(new UndoHistory.Entry(next, previous.label()));
        redoStack.push(current);
        snapshot = next;
        notifyListeners(next);

        return new EditResult.Applied(next, List.of());
    }

    @Override
    public synchronized EditResult redo(Revision expectedRevision) {
        Objects.requireNonNull(expectedRevision, "expectedRevision");

        if (notifying) {
            return reentrantRejection();
        }

        if (!snapshot.revision().equals(expectedRevision)) {
            return stale(expectedRevision);
        }

        if (redoStack.isEmpty()) {
            return reject(DiagnosticCode.INVALID_EDIT, "No document state to redo");
        }

        UndoHistory.Entry nextEntry = redoStack.pop();
        AnimationSnapshot next = new AnimationSnapshot(
                snapshot.revision().next(), nextEntry.snapshot().document());

        snapshot = next;
        undoStack.push(new UndoHistory.Entry(next, nextEntry.label()));
        notifyListeners(next);

        return new EditResult.Applied(next, List.of());
    }

    @Override
    public UndoHistory history() {
        return new UndoHistory() {
            @Override
            public boolean canUndo() {
                synchronized (CoreAnimationDocumentStore.this) {
                    return undoStack.size() > 1;
                }
            }

            @Override
            public boolean canRedo() {
                synchronized (CoreAnimationDocumentStore.this) {
                    return !redoStack.isEmpty();
                }
            }

            @Override
            public List<Entry> entries() {
                synchronized (CoreAnimationDocumentStore.this) {
                    return List.copyOf(undoStack);
                }
            }
        };
    }

    @Override
    public void addListener(DocumentListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeListener(DocumentListener listener) {
        listeners.remove(listener);
    }

    // region 发布内核

    private EditResult submitInternal(EditSubmission submission) {
        Revision expected = submission.expectedRevision();

        if (!snapshot.revision().equals(expected)) {
            return stale(expected);
        }

        CameraAnimation candidate;

        try {
            candidate = EditOpApplier.apply(snapshot.document(), submission.operation());
        } catch (EditOpApplier.EditApplyException exception) {
            return reject(exception.code(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return reject(classify(exception), messageOf(exception));
        } catch (RuntimeException exception) {
            return reject(DiagnosticCode.INVALID_EDIT,
                    "Edit operation failed: " + messageOf(exception));
        }

        if (candidate.equals(snapshot.document())) {
            return new EditResult.Applied(snapshot, List.of());
        }

        List<Diagnostic> diagnostics = validate(candidate, snapshot.revision());

        if (hasError(diagnostics)) {
            return new EditResult.Rejected(diagnostics);
        }

        AnimationSnapshot next = new AnimationSnapshot(snapshot.revision().next(), candidate);
        snapshot = next;
        redoStack.clear();
        pushHistory(new UndoHistory.Entry(next, submission.label()));
        notifyListeners(next);

        return new EditResult.Applied(next, diagnostics);
    }

    private void pushHistory(UndoHistory.Entry entry) {
        undoStack.push(entry);

        while (undoStack.size() > MAX_HISTORY_ENTRIES) {
            undoStack.removeLast();
        }
    }

    private void notifyListeners(AnimationSnapshot next) {
        notifying = true;

        try {
            for (DocumentListener listener : listeners) {
                listener.onDocumentChanged(next);
            }
        } finally {
            notifying = false;
            drainPending();
        }
    }

    private void drainPending() {
        if (draining) {
            return;
        }

        draining = true;

        try {
            while (!pending.isEmpty()) {
                PendingSubmission request = pending.removeFirst();
                EditResult result = submitInternal(request.submission());

                try {
                    request.completion().accept(result);
                } catch (RuntimeException ignored) {
                    // 回调是调用方代码；一个回调失败不能阻断后续已排队的提交。
                }
            }
        } finally {
            draining = false;
        }
    }

    private List<Diagnostic> validate(CameraAnimation candidate, Revision revision) {
        try {
            DocumentValidator validator = validatorFactory.forRevision(revision);
            List<Diagnostic> diagnostics = validator.validate(candidate);
            return diagnostics == null ? List.of() : List.copyOf(diagnostics);
        } catch (RuntimeException exception) {
            return List.of(diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                    "Document validation failed: " + messageOf(exception), revision));
        }
    }

    private static boolean hasError(List<Diagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }

    private EditResult reject(DiagnosticCode code, String message) {
        return new EditResult.Rejected(List.of(
                diagnostic(code, Severity.ERROR, message, snapshot.revision())));
    }

    private EditResult stale(Revision expected) {
        return reject(DiagnosticCode.STALE_REVISION,
                "Expected revision " + expected + " but current revision is " + snapshot.revision());
    }

    private EditResult reentrantRejection() {
        return reject(DiagnosticCode.REENTRANT_SUBMIT,
                "Cannot mutate the document synchronously while listeners are being notified; "
                        + "use submitAfterNotifications");
    }

    private static DiagnosticCode classify(IllegalArgumentException exception) {
        String message = messageOf(exception).toLowerCase();

        if (message.contains("unique") || message.contains("duplicate")) {
            return DiagnosticCode.DUPLICATE_KEY_TIME;
        }

        if (message.contains("finite and non-negative") || message.contains("non-negative")) {
            return DiagnosticCode.ILLEGAL_KEY_TIME;
        }

        return DiagnosticCode.INVALID_EDIT;
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static Diagnostic diagnostic(DiagnosticCode code,
                                         Severity severity,
                                         String message,
                                         Revision revision) {
        return new Diagnostic(code, severity, LocalizedMessage.literal(message),
                new DiagnosticScope.Document(revision), null, null);
    }

    // endregion

    private record PendingSubmission(long requestId,
                                     EditSubmission submission,
                                     Consumer<EditResult> completion) {
    }
}
