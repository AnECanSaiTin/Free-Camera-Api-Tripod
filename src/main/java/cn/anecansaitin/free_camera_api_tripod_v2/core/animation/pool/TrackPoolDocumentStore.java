package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.TrackPoolEditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/// 全局轨道池文档存储：原子提交、发布序号、撤销/重做与监听。
///
/// 与旧 `CoreAnimationDocumentStore` 隔离，用于新模型接线。提交先做过期检查与统一校验，
/// 任何错误都不发布；撤销恢复旧内容时生成新 Revision。监听异常隔离，不阻止后续监听者。
@NullMarked
public final class TrackPoolDocumentStore {

    private static final int HISTORY_LIMIT = 64;

    private final TrackPoolEditApplier applier = new TrackPoolEditApplier();
    private final TrackPoolValidator validator = new TrackPoolValidator();
    private final List<Consumer<TrackPoolSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final Deque<TrackPoolSnapshot> undo = new ArrayDeque<>();
    private final Deque<TrackPoolSnapshot> redo = new ArrayDeque<>();
    private volatile TrackPoolSnapshot snapshot;
    private boolean notifying;

    /// 从初始文档建立存储。
    public TrackPoolDocumentStore(TrackPoolAnimation initial) {
        Objects.requireNonNull(initial, "initial");
        List<Diagnostic> diagnostics = validator.validate(initial, Revision.INITIAL);

        if (!diagnostics.isEmpty()) {
            throw new IllegalArgumentException(
                    "Initial track pool document is invalid: " + diagnostics.getFirst().message());
        }

        this.snapshot = new TrackPoolSnapshot(Revision.INITIAL, initial);
    }

    /// 当前快照；一次读取同时得到文档与 Revision。
    public TrackPoolSnapshot snapshot() {
        return snapshot;
    }

    /// 提交纯操作。
    public TrackPoolEditResult submit(TrackPoolEditOp op, Revision baseRevision) {
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(baseRevision, "baseRevision");

        synchronized (this) {
            TrackPoolSnapshot current = snapshot;

            if (notifying) {
                return rejected(new Diagnostic(DiagnosticCode.REENTRANT_SUBMIT, Severity.ERROR,
                        LocalizedMessage.literal("Cannot submit while notifying listeners"),
                        new DiagnosticScope.Document(current.revision()), null, null));
            }

            if (!current.revision().equals(baseRevision)) {
                return rejected(stale(current.revision()));
            }

            TrackPoolAnimation candidate;

            try {
                candidate = applier.apply(current.document(), op, current.revision());
            } catch (IllegalArgumentException exception) {
                return rejected(invalid(exception.getMessage(), current.revision()));
            }

            List<Diagnostic> diagnostics = validator.validate(candidate, current.revision());

            if (!diagnostics.isEmpty()) {
                return new TrackPoolEditResult.Rejected(diagnostics);
            }

            publish(candidate, true);
            return new TrackPoolEditResult.Applied(snapshot);
        }
    }

    /// 撤销最近一次提交。
    public boolean undo() {
        synchronized (this) {
            if (notifying || undo.isEmpty()) {
                return false;
            }

            redo.push(snapshot);
            TrackPoolSnapshot previous = undo.pop();
            publish(previous.document(), false);
            return true;
        }
    }

    /// 重做最近一次撤销。
    public boolean redo() {
        synchronized (this) {
            if (notifying || redo.isEmpty()) {
                return false;
            }

            undo.push(snapshot);
            TrackPoolSnapshot next = redo.pop();
            publish(next.document(), false);
            return true;
        }
    }

    public boolean canUndo() {
        synchronized (this) {
            return !undo.isEmpty();
        }
    }

    public boolean canRedo() {
        synchronized (this) {
            return !redo.isEmpty();
        }
    }

    public void addListener(Consumer<TrackPoolSnapshot> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Consumer<TrackPoolSnapshot> listener) {
        listeners.remove(listener);
    }

    private void publish(TrackPoolAnimation document, boolean recordHistory) {
        TrackPoolSnapshot current = snapshot;
        TrackPoolSnapshot next = new TrackPoolSnapshot(current.revision().next(), document);

        if (recordHistory) {
            undo.push(current);

            while (undo.size() > HISTORY_LIMIT) {
                undo.removeLast();
            }

            redo.clear();
        }

        snapshot = next;
        notifying = true;

        try {
            for (Consumer<TrackPoolSnapshot> listener : listeners) {
                try {
                    listener.accept(next);
                } catch (RuntimeException ignored) {
                    // 单个监听器失败不能阻止发布或其他监听器。
                }
            }
        } finally {
            notifying = false;
        }
    }

    private static Diagnostic stale(Revision current) {
        return new Diagnostic(DiagnosticCode.STALE_REVISION, Severity.ERROR,
                LocalizedMessage.literal("Base revision is stale; current revision is " + current.value()),
                new DiagnosticScope.Document(current), null, null);
    }

    private static Diagnostic invalid(String message, Revision revision) {
        return new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                LocalizedMessage.literal(message), new DiagnosticScope.Document(revision), null, null);
    }

    private static TrackPoolEditResult rejected(Diagnostic diagnostic) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        diagnostics.add(diagnostic);
        return new TrackPoolEditResult.Rejected(diagnostics);
    }
}
