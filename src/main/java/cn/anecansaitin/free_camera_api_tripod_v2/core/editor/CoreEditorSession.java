package cn.anecansaitin.free_camera_api_tripod_v2.core.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationDocumentStore;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.DocumentListener;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditSubmission;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTransaction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.PreviewState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.SessionSwitch;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditDraftState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.InteractionId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.InteractionRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.SessionViewState;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document.DocumentPreviewer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 第一版纯 JVM 编辑器会话：文档提交、事务草稿、预览、视图状态、交互与播放控制。
///
/// ## 写入边界
///
/// 所有文档写入都经 [AnimationDocumentStore]：本类不直接修改 [CameraAnimation]，也不绕过
/// 校验、历史或通知。草稿候选只用于 [preview]，[commit] 仍以 [EditSubmission] 交给 store
/// 重新校验与发布；失败时保留事务以便修正，成功后移除事务。
///
/// ## 线程
///
/// 构造时记录宿主线程。所有公开方法（含 [close]）只允许该线程调用，其他线程抛出
/// [IllegalStateException]，不切换线程、不排队。
///
/// ## 关闭
///
/// [close] 只释放会话内的事务、交互与选择记录，不关闭注入的 store / playback。关闭后除
/// [snapshot] / [close] 外的方法返回拒绝、空值或空操作；[beginEdit] 仍返回事务值，但不会登记，
/// 后续 [draft] / [commit] 会拒绝。
///
/// ## 整份换档
///
/// 会话订阅 [AnimationDocumentStore] 的发布通知。普通编辑、撤销与重做只改变文档内容，
/// 会话状态保留；[DocumentListener#onSessionSwitched] 触发的整份换档则清空草稿、当前事务、
/// 选择 / 视图状态、交互请求与已记录选择（主设计 §3.3、§4.5）。旧事务随后按“未知事务”
/// 拒绝，旧交互请求不再出现在新文档中。通知在宿主线程外发生时先挂起，下一次宿主线程调用
/// 前统一应用，避免跨线程修改会话容器。
///
/// ## 第一版边界
///
/// - [updateDraftText] 保留字段原文，并用 [CoreDraftTextParser] 解析当前支持的结构化字段；
///   不支持的字段保留原文并产生 `INVALID_EDIT` 诊断；
/// - 交互选项 [choose] 只记录选择并移除请求，不执行任何回调；
/// - [preview] 通过 [DocumentPreviewer] 生成只读候选，不发布、不写历史。
@NullMarked
public final class CoreEditorSession implements EditorSession {

    /// 没有播放服务或会话已关闭时的空时钟。
    private static final PlaybackClock IDLE_CLOCK = new PlaybackClock(0f, 1f, false, 0f);

    /// 没有当前事务时使用的事务序号占位；真实事务序号非负。
    private static final long NO_TRANSACTION = -1L;

    private final AnimationDocumentStore store;
    private final @Nullable PlaybackService playback;
    private final Thread ownerThread;

    private final Map<Long, Draft> drafts = new HashMap<>();
    private final Map<InteractionId, InteractionRequest> interactions = new LinkedHashMap<>();
    private final List<InteractionChoice> interactionChoices = new ArrayList<>();

    /// 换档发生在宿主线程之外时挂起，下一次宿主线程调用前应用。
    private volatile boolean resetPending;

    private long nextTransactionId;
    private long currentTransactionId = NO_TRANSACTION;
    private SessionViewState viewState = SessionViewState.initial();
    private boolean closed;

    private final DocumentListener documentListener = new DocumentListener() {
        @Override
        public void onDocumentChanged(AnimationSnapshot snapshot) {
            // 普通编辑不清理会话：草稿、选择与交互仍属于同一份文档。
        }

        @Override
        public void onSessionSwitched(AnimationSnapshot snapshot, SessionSwitch reason) {
            if (closed) {
                return;
            }

            if (Thread.currentThread() == ownerThread) {
                clearSessionState();
            } else {
                resetPending = true;
            }
        }
    };

    /// 建立会话、登记换档监听并记录当前线程为宿主线程。
    ///
    /// @param store    文档存储；所有写入委托给它
    /// @param playback 播放服务；为空时播放控制为空操作，状态固定为 `IDLE`
    public CoreEditorSession(AnimationDocumentStore store, @Nullable PlaybackService playback) {
        this.store = Objects.requireNonNull(store, "store");
        this.playback = playback;
        this.ownerThread = Thread.currentThread();
        store.addListener(documentListener);
    }

    @Override
    public AnimationSnapshot snapshot() {
        requireHostThread();
        return store.snapshot();
    }

    @Override
    public EditResult submit(EditSubmission submission) {
        requireHostThread();

        if (closed) {
            return closedRejection();
        }

        Objects.requireNonNull(submission, "submission");
        return store.submit(submission);
    }

    @Override
    public EditTransaction beginEdit(EditTarget target, LocalizedMessage label) {
        requireHostThread();
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(label, "label");

        EditTransaction transaction = new EditTransaction(
                nextTransactionId++,
                store.snapshot().revision(),
                target,
                label);

        if (!closed) {
            drafts.put(transaction.transactionId(), new Draft(transaction));
            currentTransactionId = transaction.transactionId();
        }

        return transaction;
    }

    @Override
    public EditDraftState draft(EditTransaction transaction) {
        requireHostThread();
        Objects.requireNonNull(transaction, "transaction");

        if (closed) {
            return new EditDraftState(transaction, Map.of(), null, List.of(closedDiagnostic()));
        }

        Draft draft = findDraft(transaction);

        if (draft == null) {
            return new EditDraftState(transaction, Map.of(), null, List.of(diagnostic(
                    DiagnosticCode.TARGET_NOT_FOUND,
                    Severity.ERROR,
                    "Unknown or cancelled edit transaction: " + transaction.transactionId(),
                    store.snapshot().revision())));
        }

        return new EditDraftState(
                transaction,
                draft.fieldTexts,
                draft.candidate,
                draftDiagnostics(draft, store.snapshot().revision()));
    }

    @Override
    public void updateDraftOperation(EditTransaction transaction, @Nullable EditOp operation) {
        requireHostThread();

        if (closed) {
            return;
        }

        Objects.requireNonNull(transaction, "transaction");
        Draft draft = findDraft(transaction);

        if (draft == null) {
            return;
        }

        draft.candidate = operation;
        currentTransactionId = transaction.transactionId();
    }

    @Override
    public void updateDraftText(EditTransaction transaction, String fieldPath, String text) {
        requireHostThread();

        if (closed) {
            return;
        }

        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(fieldPath, "fieldPath");
        Objects.requireNonNull(text, "text");

        if (fieldPath.isBlank()) {
            throw new IllegalArgumentException("fieldPath must not be blank");
        }

        Draft draft = findDraft(transaction);

        if (draft == null) {
            return;
        }

        draft.fieldTexts.put(fieldPath, text);
        CoreDraftTextParser.Result parsed = CoreDraftTextParser.parse(
                draft.transaction.target(), draft.fieldTexts, store.snapshot());
        draft.candidate = parsed.candidate();
        draft.textDiagnostics.clear();

        for (Diagnostic diagnostic : parsed.diagnostics()) {
            String path = diagnostic.location() == null
                    ? fieldPath
                    : diagnostic.location().fieldPath();
            draft.textDiagnostics.put(path, diagnostic);
        }

        currentTransactionId = transaction.transactionId();
    }

    @Override
    public PreviewState preview() {
        requireHostThread();

        if (closed) {
            return PreviewState.EMPTY;
        }

        Draft draft = currentDraft();

        if (draft == null) {
            return PreviewState.EMPTY;
        }

        AnimationSnapshot snapshot = store.snapshot();
        boolean stale = !draft.transaction.baseRevision().equals(snapshot.revision());
        List<Diagnostic> diagnostics = draftDiagnostics(draft, snapshot.revision());
        CameraAnimation candidate = null;

        // 基版本过期、草稿有错误或没有候选时不应用预览；不把未经 store 校验的文档交出去。
        if (!stale && draft.candidate != null && !hasError(diagnostics)) {
            candidate = DocumentPreviewer.preview(snapshot.document(), draft.candidate).orElse(null);

            if (candidate == null) {
                List<Diagnostic> withPreviewFailure = new ArrayList<>(diagnostics);
                withPreviewFailure.add(diagnostic(
                        DiagnosticCode.INVALID_EDIT,
                        Severity.ERROR,
                        "Draft candidate could not be applied to the current document",
                        snapshot.revision()));
                diagnostics = List.copyOf(withPreviewFailure);
            }
        }

        return new PreviewState(candidate, diagnostics, stale);
    }

    @Override
    public EditResult commit(EditTransaction transaction) {
        requireHostThread();

        if (closed) {
            return closedRejection();
        }

        Objects.requireNonNull(transaction, "transaction");
        Draft draft = findDraft(transaction);

        if (draft == null) {
            return rejected(DiagnosticCode.TARGET_NOT_FOUND,
                    "Unknown or cancelled edit transaction: " + transaction.transactionId());
        }

        Revision currentRevision = store.snapshot().revision();

        if (!draft.transaction.baseRevision().equals(currentRevision)) {
            return rejected(DiagnosticCode.STALE_REVISION,
                    "Transaction base revision " + draft.transaction.baseRevision()
                            + " no longer matches current revision " + currentRevision);
        }

        List<Diagnostic> diagnostics = draftDiagnostics(draft, currentRevision);

        if (draft.candidate == null) {
            if (hasError(diagnostics)) {
                return new EditResult.Rejected(diagnostics);
            }

            return rejected(DiagnosticCode.INVALID_EDIT,
                    "Edit transaction has no complete candidate");
        }

        if (hasError(diagnostics)) {
            return new EditResult.Rejected(diagnostics);
        }

        EditResult result = store.submit(new EditSubmission(
                draft.candidate,
                draft.transaction.baseRevision(),
                draft.transaction.label()));

        if (result instanceof EditResult.Applied) {
            drafts.remove(transaction.transactionId());

            if (currentTransactionId == transaction.transactionId()) {
                currentTransactionId = NO_TRANSACTION;
            }
        }

        return result;
    }

    @Override
    public void cancel(EditTransaction transaction) {
        requireHostThread();

        if (closed) {
            return;
        }

        Objects.requireNonNull(transaction, "transaction");

        if (findDraft(transaction) == null) {
            return;
        }

        drafts.remove(transaction.transactionId());

        if (currentTransactionId == transaction.transactionId()) {
            currentTransactionId = NO_TRANSACTION;
        }
    }

    @Override
    public EditResult undo() {
        requireHostThread();

        if (closed) {
            return closedRejection();
        }

        return store.undo(store.snapshot().revision());
    }

    @Override
    public EditResult redo() {
        requireHostThread();

        if (closed) {
            return closedRejection();
        }

        return store.redo(store.snapshot().revision());
    }

    @Override
    public PlaybackState playbackState() {
        requireHostThread();

        if (closed || playback == null) {
            return PlaybackState.IDLE;
        }

        return playback.state();
    }

    @Override
    public PlaybackClock playbackClock() {
        requireHostThread();

        if (closed || playback == null) {
            return IDLE_CLOCK;
        }

        return playback.clock();
    }

    @Override
    public void seek(float time) {
        requireHostThread();

        if (closed || playback == null) {
            return;
        }

        playback.seek(time);
    }

    @Override
    public void play() {
        requireHostThread();

        if (closed || playback == null) {
            return;
        }

        AnimationSnapshot snapshot = store.snapshot();
        PlaybackRequest request = new PlaybackRequest(
                snapshot.document(),
                Map.of(),
                1f,
                false,
                playback.clock().time());

        try {
            // 启动失败由 playback 记录 failure / 保持原状态；会话不抛出，也不先换档。
            playback.start(request, snapshot.revision());
        } catch (IllegalArgumentException ignored) {
            // 明显非法的播放输入不抛出；playback 自身状态保持不变。
        }
    }

    @Override
    public void pause() {
        requireHostThread();

        if (closed || playback == null) {
            return;
        }

        playback.pause();
    }

    @Override
    public SessionViewState viewState() {
        requireHostThread();

        if (closed) {
            return SessionViewState.initial();
        }

        return viewState;
    }

    @Override
    public void updateViewState(SessionViewState state) {
        requireHostThread();

        if (closed) {
            return;
        }

        viewState = Objects.requireNonNull(state, "state");
    }

    @Override
    public List<InteractionRequest> interactions() {
        requireHostThread();

        if (closed) {
            return List.of();
        }

        return List.copyOf(interactions.values());
    }

    @Override
    public void choose(InteractionId requestId, String optionId) {
        requireHostThread();

        if (closed) {
            return;
        }

        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(optionId, "optionId");

        InteractionRequest request = interactions.get(requestId);

        if (request == null) {
            return;
        }

        boolean validOption = request.options().stream()
                .anyMatch(option -> option.id().equals(optionId));

        if (!validOption) {
            // 选项无效时保留请求，允许面板重试；不执行任何旧回调。
            return;
        }

        interactions.remove(requestId);
        interactionChoices.add(new InteractionChoice(requestId, optionId));
    }

    @Override
    public void cancelInteraction(InteractionId requestId) {
        requireHostThread();

        if (closed) {
            return;
        }

        Objects.requireNonNull(requestId, "requestId");
        interactions.remove(requestId);
    }

    @Override
    public void close() {
        requireHostThread();

        if (closed) {
            return;
        }

        closed = true;
        store.removeListener(documentListener);
        clearSessionState();
    }

    // region 包内扩展点

    /// 登记一个待处理交互请求。
    ///
    /// 第一版接口没有产生交互请求的入口；本方法供后续宿主接线与同包测试使用，不属于
    /// [EditorSession] 契约。会话关闭后调用为空操作。
    ///
    /// @param request 交互请求；同一会话内 ID 必须唯一
    void addInteraction(InteractionRequest request) {
        requireHostThread();

        if (closed) {
            return;
        }

        Objects.requireNonNull(request, "request");

        if (interactions.putIfAbsent(request.id(), request) != null) {
            throw new IllegalArgumentException(
                    "Duplicate interaction request id: " + request.id().value());
        }
    }

    /// 第一版已记录的选择结果；只用于观察，不执行回调。
    ///
    /// @return 按选择顺序排列的不可变副本
    List<InteractionChoice> interactionChoices() {
        requireHostThread();
        return List.copyOf(interactionChoices);
    }

    // endregion

    // region 事务与诊断

    private @Nullable Draft findDraft(EditTransaction transaction) {
        Draft draft = drafts.get(transaction.transactionId());

        if (draft == null || !draft.transaction.equals(transaction)) {
            return null;
        }

        return draft;
    }

    private @Nullable Draft currentDraft() {
        if (currentTransactionId == NO_TRANSACTION) {
            return null;
        }

        return drafts.get(currentTransactionId);
    }

    private List<Diagnostic> draftDiagnostics(Draft draft, Revision currentRevision) {
        List<Diagnostic> diagnostics = new ArrayList<>(draft.textDiagnostics.values());

        if (!draft.transaction.baseRevision().equals(currentRevision)) {
            diagnostics.add(diagnostic(
                    DiagnosticCode.STALE_REVISION,
                    Severity.ERROR,
                    "Transaction base revision " + draft.transaction.baseRevision()
                            + " no longer matches current revision " + currentRevision,
                    currentRevision));
        }

        return List.copyOf(diagnostics);
    }

    private static boolean hasError(List<Diagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }

    private EditResult closedRejection() {
        return new EditResult.Rejected(List.of(closedDiagnostic()));
    }

    private EditResult rejected(DiagnosticCode code, String message) {
        return new EditResult.Rejected(List.of(diagnostic(
                code, Severity.ERROR, message, store.snapshot().revision())));
    }

    private Diagnostic closedDiagnostic() {
        return diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                "Editor session is closed", store.snapshot().revision());
    }

    private static Diagnostic diagnostic(DiagnosticCode code,
                                         Severity severity,
                                         String message,
                                         Revision revision) {
        return diagnostic(code, severity, message, null, revision);
    }

    private static Diagnostic diagnostic(DiagnosticCode code,
                                         Severity severity,
                                         String message,
                                         Diagnostic.@Nullable Location location,
                                         Revision revision) {
        return new Diagnostic(
                code,
                severity,
                LocalizedMessage.literal(message),
                new DiagnosticScope.Document(revision),
                location,
                null);
    }

    // endregion

    // region 线程

    private void requireHostThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("CoreEditorSession must be called on its owner thread");
        }

        applyPendingReset();
    }

    /// 整份换档后清理会话内状态：草稿、当前事务、选择 / 视图、交互请求与已记录选择。
    ///
    /// 不清空 [nextTransactionId]，只清空映射与当前指针；旧事务对象之后按未知事务拒绝，
    /// 不会误命中新文档。
    private void clearSessionState() {
        drafts.clear();
        currentTransactionId = NO_TRANSACTION;
        interactions.clear();
        interactionChoices.clear();
        viewState = SessionViewState.initial();
    }

    /// 应用宿主线程之外挂起的换档清理。
    private void applyPendingReset() {
        if (!resetPending) {
            return;
        }

        resetPending = false;

        if (!closed) {
            clearSessionState();
        }
    }

    // endregion

    /// 一个事务的会话内草稿：输入原文、文本诊断与结构化候选。
    private static final class Draft {

        private final EditTransaction transaction;
        private final Map<String, String> fieldTexts = new LinkedHashMap<>();
        private final Map<String, Diagnostic> textDiagnostics = new LinkedHashMap<>();
        private @Nullable EditOp candidate;

        private Draft(EditTransaction transaction) {
            this.transaction = transaction;
        }
    }

    /// 第一版记录的选择：只保留请求与选项标识，不执行回调。
    ///
    /// @param requestId 已处理的请求身份
    /// @param optionId  用户选择的选项标识
    record InteractionChoice(InteractionId requestId, String optionId) {

        InteractionChoice {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(optionId, "optionId");
        }
    }
}
