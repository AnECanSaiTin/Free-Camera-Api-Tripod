package cn.anecansaitin.free_camera_api_tripod_v2.core.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditSubmission;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTransaction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.PreviewState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorToolState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.InteractionId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.InteractionOption;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.InteractionRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.editor.SessionViewState;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document.CoreAnimationDocumentStore;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback.CorePlaybackService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [CoreEditorSession] 的事务、草稿、预览、播放、视图、交互、关闭与线程边界。
class CoreEditorSessionTest {

    private static final LocalizedMessage LABEL = LocalizedMessage.literal("edit");
    private static final PlaybackClock EMPTY_CLOCK = new PlaybackClock(0f, 1f, false, 0f);

    // region 提交与事务

    @Test
    void submitDelegatesToStoreAndAdvancesRevision() {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);

        EditResult result = session.submit(rename("renamed", Revision.INITIAL));

        assertTrue(result instanceof EditResult.Applied);
        assertEquals(new Revision(1), session.snapshot().revision());
        assertEquals("renamed", session.snapshot().document().name());
        assertEquals(store.snapshot(), session.snapshot());
        assertEquals(2, store.history().entries().size());

        session.close();
    }

    @Test
    void beginUpdateOperationAndCommitApplyCandidate() {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);

        EditTransaction first = session.beginEdit(new EditTarget.Document(), LABEL);
        EditTransaction second = session.beginEdit(
                new EditTarget.Rig(new RigId("rig")), LABEL);

        assertTrue(first.transactionId() >= 0);
        assertTrue(second.transactionId() > first.transactionId());
        assertEquals(Revision.INITIAL, first.baseRevision());
        assertEquals(Revision.INITIAL, second.baseRevision());

        EditOp candidate = new EditOp.RenameDocument("draft");
        session.updateDraftOperation(first, candidate);

        var draft = session.draft(first);
        assertSame(candidate, draft.candidate());
        assertTrue(draft.fieldTexts().isEmpty());
        assertTrue(draft.hasCandidate());

        PreviewState preview = session.preview();
        assertEquals(new Revision(0), session.snapshot().revision());
        assertFalse(preview.stale());
        assertTrue(preview.diagnostics().isEmpty());
        assertEquals("draft", preview.candidate().name());

        EditResult result = session.commit(first);

        assertTrue(result instanceof EditResult.Applied);
        assertEquals(new Revision(1), session.snapshot().revision());
        assertEquals("draft", session.snapshot().document().name());
        assertEquals(2, store.history().entries().size());

        // 成功事务被移除，预览恢复为空。
        assertTrue(session.draft(first).fieldTexts().isEmpty());
        assertNull(session.draft(first).candidate());
        assertEquals(DiagnosticCode.TARGET_NOT_FOUND,
                session.draft(first).diagnostics().getFirst().code());
        assertEquals(PreviewState.EMPTY, session.preview());

        session.close();
    }

    @Test
    void commitWithoutCandidateOrWithErrorIsRejectedAndKeepsTransaction() {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);
        EditTransaction transaction = session.beginEdit(new EditTarget.Document(), LABEL);

        EditResult noCandidate = session.commit(transaction);

        assertTrue(noCandidate instanceof EditResult.Rejected);
        assertEquals(DiagnosticCode.INVALID_EDIT,
                ((EditResult.Rejected) noCandidate).diagnostics().getFirst().code());
        assertEquals(Revision.INITIAL, store.snapshot().revision());

        EditOp candidate = new EditOp.RenameDocument("renamed");
        session.updateDraftOperation(transaction, candidate);
        session.updateDraftText(transaction, "name", "not a valid name");

        var draft = session.draft(transaction);
        assertSame(candidate, draft.candidate());
        assertEquals("not a valid name", draft.fieldTexts().get("name"));
        assertFalse(draft.hasCandidate());
        assertEquals(Severity.ERROR, draft.diagnostics().getFirst().severity());

        EditResult withError = session.commit(transaction);

        assertTrue(withError instanceof EditResult.Rejected);
        assertTrue(((EditResult.Rejected) withError).diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR));
        assertEquals(Revision.INITIAL, store.snapshot().revision());
        assertEquals("doc", store.snapshot().document().name());
        // 失败保留事务与草稿，用户可继续修正。
        assertEquals("not a valid name", session.draft(transaction).fieldTexts().get("name"));

        session.updateDraftOperation(transaction, null);
        assertNull(session.draft(transaction).candidate());
        assertTrue(session.commit(transaction) instanceof EditResult.Rejected);

        session.close();
    }

    @Test
    void cancelRemovesTransactionAndPreview() {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);
        EditTransaction transaction = session.beginEdit(new EditTarget.Document(), LABEL);
        session.updateDraftOperation(transaction, new EditOp.RenameDocument("cancelled"));

        session.cancel(transaction);
        session.cancel(transaction);

        assertTrue(session.draft(transaction).fieldTexts().isEmpty());
        assertNull(session.draft(transaction).candidate());
        assertEquals(DiagnosticCode.TARGET_NOT_FOUND,
                session.draft(transaction).diagnostics().getFirst().code());
        assertEquals(PreviewState.EMPTY, session.preview());

        EditResult commit = session.commit(transaction);
        assertTrue(commit instanceof EditResult.Rejected);
        assertEquals(DiagnosticCode.TARGET_NOT_FOUND,
                ((EditResult.Rejected) commit).diagnostics().getFirst().code());
        assertEquals(Revision.INITIAL, store.snapshot().revision());

        session.close();
    }

    @Test
    void unknownTransactionIsReportedWithoutThrowing() {
        CoreEditorSession session = new CoreEditorSession(store("doc"), null);
        EditTransaction unknown = new EditTransaction(
                999L, Revision.INITIAL, new EditTarget.Document(), LABEL);

        var draft = session.draft(unknown);
        assertTrue(draft.fieldTexts().isEmpty());
        assertNull(draft.candidate());
        assertEquals(DiagnosticCode.TARGET_NOT_FOUND, draft.diagnostics().getFirst().code());
        assertEquals(Severity.ERROR, draft.diagnostics().getFirst().severity());

        assertDoesNotThrow(() -> session.cancel(unknown));
        assertTrue(session.commit(unknown) instanceof EditResult.Rejected);

        session.close();
    }

    @Test
    void staleBaseRevisionCommitIsRejectedAndPreviewMarksStale() {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);
        EditTransaction transaction = session.beginEdit(new EditTarget.Document(), LABEL);
        session.updateDraftOperation(transaction, new EditOp.RenameDocument("stale"));

        // 另一个提交推进 Revision，使事务基版本过期。
        assertTrue(session.submit(rename("external", Revision.INITIAL)) instanceof EditResult.Applied);

        var draft = session.draft(transaction);
        assertTrue(draft.candidate() instanceof EditOp.RenameDocument);
        assertEquals("stale", ((EditOp.RenameDocument) draft.candidate()).name());
        assertTrue(draft.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals(DiagnosticCode.STALE_REVISION)
                        && diagnostic.severity() == Severity.ERROR));
        assertFalse(draft.hasCandidate());

        PreviewState preview = session.preview();
        assertTrue(preview.stale());
        assertNull(preview.candidate());

        EditResult commit = session.commit(transaction);

        assertTrue(commit instanceof EditResult.Rejected);
        assertEquals(DiagnosticCode.STALE_REVISION,
                ((EditResult.Rejected) commit).diagnostics().getFirst().code());
        assertEquals("external", store.snapshot().document().name());
        assertEquals(new Revision(1), store.snapshot().revision());
        // 过期事务仍保留，便于面板显示；但不能覆盖新文档。
        assertTrue(session.draft(transaction).candidate() instanceof EditOp.RenameDocument);

        session.close();
    }

    @Test
    void undoAndRedoDelegateToStoreWithFreshRevisions() {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);
        assertTrue(session.submit(rename("renamed", Revision.INITIAL)) instanceof EditResult.Applied);

        EditResult undone = session.undo();

        assertTrue(undone instanceof EditResult.Applied);
        assertEquals(new Revision(2), session.snapshot().revision());
        assertEquals("doc", session.snapshot().document().name());

        EditResult redone = session.redo();

        assertTrue(redone instanceof EditResult.Applied);
        assertEquals(new Revision(3), session.snapshot().revision());
        assertEquals("renamed", session.snapshot().document().name());

        session.close();
    }

    // endregion

    // region 文本草稿与预览

    @Test
    void textDraftKeepsVerbatimInputAndInvalidEditDiagnostic() {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);
        EditTransaction transaction = session.beginEdit(new EditTarget.Document(), LABEL);

        session.updateDraftText(transaction, "view.rect.width", "not-a-number");

        var draft = session.draft(transaction);
        assertEquals("not-a-number", draft.fieldTexts().get("view.rect.width"));
        assertNull(draft.candidate());
        assertFalse(draft.hasCandidate());
        assertEquals(1, draft.diagnostics().size());

        Diagnostic diagnostic = draft.diagnostics().getFirst();
        assertEquals(DiagnosticCode.INVALID_EDIT, diagnostic.code());
        assertEquals(Severity.ERROR, diagnostic.severity());
        assertTrue(diagnostic.location() != null);
        assertEquals("view.rect.width", diagnostic.location().fieldPath());
        assertTrue(diagnostic.message() instanceof LocalizedMessage.Literal);
        assertTrue(((LocalizedMessage.Literal) diagnostic.message()).text().contains("not implemented"));

        assertThrows(UnsupportedOperationException.class,
                () -> draft.fieldTexts().put("other", "text"));

        // 同一字段的新文本替换旧诊断，不累积。
        session.updateDraftText(transaction, "view.rect.width", "2.5");
        assertEquals("2.5", session.draft(transaction).fieldTexts().get("view.rect.width"));
        assertEquals(1, session.draft(transaction).diagnostics().size());
        assertEquals(Revision.INITIAL, store.snapshot().revision());

        session.close();
    }

    @Test
    void previewWithUnappliableCandidateReportsDiagnosticWithoutPublishing() {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);
        EditTransaction transaction = session.beginEdit(new EditTarget.Document(), LABEL);
        session.updateDraftOperation(transaction, new EditOp.RemoveChannel(
                new RigId("rig"), new TrackId("missing")));

        PreviewState preview = session.preview();

        assertNull(preview.candidate());
        assertFalse(preview.stale());
        assertTrue(preview.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals(DiagnosticCode.INVALID_EDIT)
                        && diagnostic.severity() == Severity.ERROR));
        assertEquals(Revision.INITIAL, store.snapshot().revision());
        assertEquals(1, store.history().entries().size());

        EditResult commit = session.commit(transaction);
        assertTrue(commit instanceof EditResult.Rejected);
        assertEquals(DiagnosticCode.TARGET_NOT_FOUND,
                ((EditResult.Rejected) commit).diagnostics().getFirst().code());
        assertEquals(Revision.INITIAL, store.snapshot().revision());

        session.close();
    }

    // endregion

    // region 视图状态与交互

    @Test
    void viewStateIsSessionLocalAndDoesNotWriteDocument() {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);

        assertEquals(SessionViewState.initial(), session.viewState());

        SessionViewState initial = session.viewState();
        SessionViewState updated = new SessionViewState(
                2.5f,
                initial.selection(),
                initial.timeline(),
                initial.snapping(),
                null,
                initial.trackVisibility(),
                EditorToolState.MOVE,
                new RigId("rig"),
                Map.of());

        session.updateViewState(updated);

        assertEquals(updated, session.viewState());
        assertEquals(Revision.INITIAL, store.snapshot().revision());
        assertEquals("doc", store.snapshot().document().name());
        assertEquals(1, store.history().entries().size());

        session.close();
    }

    @Test
    void chooseRequiresExistingRequestAndValidOption() {
        CoreEditorSession session = new CoreEditorSession(store("doc"), null);
        InteractionRequest request = interaction(1L, "ok", "cancel");
        session.addInteraction(request);

        assertEquals(List.of(request), session.interactions());

        // 无效选项：拒绝且保留请求。
        session.choose(request.id(), "missing");
        assertEquals(List.of(request), session.interactions());
        assertTrue(session.interactionChoices().isEmpty());

        // 过期 / 未知请求：拒绝且不抛。
        session.choose(new InteractionId(99L), "ok");
        assertEquals(List.of(request), session.interactions());
        assertTrue(session.interactionChoices().isEmpty());

        // 有效选择：移除请求并记录选择。
        session.choose(request.id(), "ok");
        assertTrue(session.interactions().isEmpty());
        assertEquals(1, session.interactionChoices().size());
        var choice = session.interactionChoices().getFirst();
        assertEquals(request.id(), choice.requestId());
        assertEquals("ok", choice.optionId());

        // 已处理过的请求再次选择：过期拒绝，不产生新记录。
        session.choose(request.id(), "cancel");
        assertEquals(1, session.interactionChoices().size());

        session.close();
    }

    @Test
    void cancelInteractionRemovesOnlyMatchingRequest() {
        CoreEditorSession session = new CoreEditorSession(store("doc"), null);
        InteractionRequest first = interaction(1L, "ok", "cancel");
        InteractionRequest second = interaction(2L, "yes", "no");
        session.addInteraction(first);
        session.addInteraction(second);

        session.cancelInteraction(first.id());
        session.cancelInteraction(new InteractionId(77L));

        assertEquals(List.of(second), session.interactions());
        assertTrue(session.interactionChoices().isEmpty());

        session.close();
    }

    // endregion

    // region 播放

    @Test
    void playbackControlsDelegateAndUseCurrentSnapshot() {
        CameraAnimation document = document("doc", 10f);
        FakePlayback playback = new FakePlayback();
        CoreEditorSession session = new CoreEditorSession(store(document), playback);

        assertEquals(PlaybackState.IDLE, session.playbackState());
        assertEquals(EMPTY_CLOCK, session.playbackClock());

        session.play();

        assertEquals(1, playback.startCalls);
        assertSame(document, playback.lastRequest.animation());
        assertEquals(Revision.INITIAL, playback.lastRevision);
        assertEquals(1f, playback.lastRequest.speed(), 1e-5f);
        assertFalse(playback.lastRequest.loop());
        assertEquals(0f, playback.lastRequest.startTime(), 1e-5f);
        assertTrue(playback.lastRequest.rigOverrides().isEmpty());
        assertEquals(PlaybackState.PLAYING, session.playbackState());
        assertEquals(10f, session.playbackClock().duration(), 1e-5f);

        session.pause();
        assertEquals(1, playback.pauseCalls);
        assertEquals(PlaybackState.PAUSED, session.playbackState());

        session.seek(2f);
        assertEquals(1, playback.seekCalls);
        assertEquals(2f, playback.lastSeek, 1e-5f);
        assertEquals(2f, session.playbackClock().time(), 1e-5f);

        session.play();
        assertEquals(2, playback.startCalls);
        assertEquals(2f, playback.lastRequest.startTime(), 1e-5f);

        session.close();
    }

    @Test
    void playFailureDoesNotThrowAndKeepsPlaybackState() {
        FakePlayback playback = new FakePlayback();
        PlaybackFailure failure = new PlaybackFailure(
                PlaybackFailure.Reason.EVALUATION_ERROR,
                LocalizedMessage.literal("boom"),
                0f,
                List.of());
        playback.startResult = new PlaybackService.StartResult(false, failure);
        CoreEditorSession session = new CoreEditorSession(store(document("doc", 10f)), playback);

        assertDoesNotThrow(session::play);

        assertEquals(PlaybackState.IDLE, session.playbackState());
        assertEquals(EMPTY_CLOCK, session.playbackClock());
        assertEquals(failure, playback.failure());

        session.close();
    }

    @Test
    void nullPlaybackIsSafeAndKeepsIdleState() {
        CoreEditorSession session = new CoreEditorSession(store(document("doc", 10f)), null);

        assertEquals(PlaybackState.IDLE, session.playbackState());
        assertEquals(EMPTY_CLOCK, session.playbackClock());

        assertDoesNotThrow(() -> session.seek(3f));
        assertDoesNotThrow(session::play);
        assertDoesNotThrow(session::pause);

        assertEquals(PlaybackState.IDLE, session.playbackState());
        assertEquals(EMPTY_CLOCK, session.playbackClock());

        session.close();
    }

    @Test
    void realPlaybackServiceDrivesClockThroughSession() {
        CameraAnimation document = document("doc", 10f);
        CoreAnimationDocumentStore store = store(document);
        CorePlaybackService playback = new CorePlaybackService(store,
                (snapshot, request, clock, fromTime, index, budget) -> {
                    throw new AssertionError("prepareFrame is not expected in this test");
                });
        CoreEditorSession session = new CoreEditorSession(store, playback);

        session.play();

        assertEquals(PlaybackState.PLAYING, session.playbackState());
        assertEquals(0f, session.playbackClock().time(), 1e-5f);
        assertEquals(10f, session.playbackClock().duration(), 1e-5f);

        session.seek(2f);
        assertEquals(2f, session.playbackClock().time(), 1e-5f);

        session.pause();
        assertEquals(PlaybackState.PAUSED, session.playbackState());
        assertEquals(2f, session.playbackClock().time(), 1e-5f);

        session.play();
        assertEquals(PlaybackState.PLAYING, session.playbackState());
        assertEquals(2f, session.playbackClock().time(), 1e-5f);

        session.close();
    }

    // endregion

    // region 关闭与线程

    @Test
    void closeIsIdempotentAndRejectsFurtherOperations() {
        CoreAnimationDocumentStore store = store("doc");
        FakePlayback playback = new FakePlayback();
        playback.state = PlaybackState.PLAYING;
        playback.clock = new PlaybackClock(4f, 1f, false, 10f);
        CoreEditorSession session = new CoreEditorSession(store, playback);
        EditTransaction transaction = session.beginEdit(new EditTarget.Document(), LABEL);
        session.updateDraftOperation(transaction, new EditOp.RenameDocument("closed"));
        session.addInteraction(interaction(1L, "ok", "cancel"));
        session.updateViewState(new SessionViewState(
                1f, session.viewState().selection(), session.viewState().timeline(),
                session.viewState().snapping(), null, session.viewState().trackVisibility(),
                EditorToolState.MOVE, new RigId("rig"), Map.of()));

        session.close();
        session.close();

        assertEquals(Revision.INITIAL, session.snapshot().revision());

        EditResult submitted = session.submit(rename("after", Revision.INITIAL));
        assertTrue(submitted instanceof EditResult.Rejected);
        assertEquals(DiagnosticCode.INVALID_EDIT,
                ((EditResult.Rejected) submitted).diagnostics().getFirst().code());
        assertTrue(session.undo() instanceof EditResult.Rejected);
        assertTrue(session.redo() instanceof EditResult.Rejected);

        var closedDraft = session.draft(transaction);
        assertTrue(closedDraft.fieldTexts().isEmpty());
        assertNull(closedDraft.candidate());
        assertEquals(DiagnosticCode.INVALID_EDIT,
                closedDraft.diagnostics().getFirst().code());
        assertTrue(session.commit(transaction) instanceof EditResult.Rejected);
        assertDoesNotThrow(() -> session.cancel(transaction));

        assertEquals(PreviewState.EMPTY, session.preview());
        assertEquals(PlaybackState.IDLE, session.playbackState());
        assertEquals(EMPTY_CLOCK, session.playbackClock());
        assertEquals(SessionViewState.initial(), session.viewState());
        assertTrue(session.interactions().isEmpty());

        assertDoesNotThrow(() -> session.choose(new InteractionId(1L), "ok"));
        assertDoesNotThrow(() -> session.cancelInteraction(new InteractionId(1L)));
        assertDoesNotThrow(() -> session.seek(5f));
        assertDoesNotThrow(session::play);
        assertDoesNotThrow(session::pause);
        assertDoesNotThrow(() -> session.updateViewState(SessionViewState.initial()));
        assertEquals(SessionViewState.initial(), session.viewState());
        assertEquals(0, playback.startCalls);
        assertEquals(0, playback.seekCalls);
        assertEquals(0, playback.pauseCalls);

        // 关闭后 beginEdit 仍返回值但不登记，后续操作拒绝。
        EditTransaction unregistered = session.beginEdit(new EditTarget.Document(), LABEL);
        assertTrue(unregistered.transactionId() >= 0);
        assertEquals(DiagnosticCode.INVALID_EDIT,
                session.draft(unregistered).diagnostics().getFirst().code());
        assertTrue(session.commit(unregistered) instanceof EditResult.Rejected);

        // 会话关闭不关闭 store。
        assertTrue(store.submit(rename("after close", Revision.INITIAL)) instanceof EditResult.Applied);
        assertEquals(new Revision(1), session.snapshot().revision());
    }

    @Test
    void allCallsCheckOwnerThreadAndDoNotMutate() throws InterruptedException {
        CoreAnimationDocumentStore store = store("doc");
        CoreEditorSession session = new CoreEditorSession(store, null);
        AtomicReference<Throwable> snapshotFailure = new AtomicReference<>();
        AtomicReference<Throwable> submitFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            try {
                session.snapshot();
            } catch (Throwable error) {
                snapshotFailure.set(error);
            }

            try {
                session.submit(rename("wrong thread", Revision.INITIAL));
            } catch (Throwable error) {
                submitFailure.set(error);
            }

            try {
                session.close();
            } catch (Throwable error) {
                closeFailure.set(error);
            }
        });
        thread.start();
        thread.join();

        assertTrue(snapshotFailure.get() instanceof IllegalStateException);
        assertTrue(submitFailure.get() instanceof IllegalStateException);
        assertTrue(closeFailure.get() instanceof IllegalStateException);
        assertEquals(Revision.INITIAL, store.snapshot().revision());
        assertEquals("doc", store.snapshot().document().name());

        session.close();
    }

    // endregion

    // region 测试夹具

    private static CameraAnimation document(String name) {
        return document(name, 0f);
    }

    private static CameraAnimation document(String name, float duration) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create(name, rig, new TrackId("source"));

        if (duration <= 0f) {
            return document;
        }

        return document.withMarkers(List.of(Marker.at(new MarkerId("end"), duration)));
    }

    private static CoreAnimationDocumentStore store(CameraAnimation document) {
        return CoreAnimationDocumentStore.withFixedValidator(document, candidate -> List.of());
    }

    private static CoreAnimationDocumentStore store(String name) {
        return store(document(name));
    }

    private static EditSubmission rename(String name, Revision revision) {
        return new EditSubmission(new EditOp.RenameDocument(name), revision, LABEL);
    }

    private static InteractionRequest interaction(long id, String... optionIds) {
        List<InteractionOption> options = java.util.Arrays.stream(optionIds)
                .map(optionId -> new InteractionOption(
                        optionId, LocalizedMessage.literal(optionId), false))
                .toList();
        return new InteractionRequest(
                new InteractionId(id), LocalizedMessage.literal("choose"), options);
    }

    /// 只记录调用并模拟状态的播放服务；`clock()` 与真实服务一样返回最后读数。
    private static final class FakePlayback implements PlaybackService {

        private PlaybackState state = PlaybackState.IDLE;
        private @Nullable PlaybackFailure failure;
        private PlaybackClock clock = EMPTY_CLOCK;
        private StartResult startResult = new StartResult(true, null);
        private @Nullable PlaybackRequest lastRequest;
        private @Nullable Revision lastRevision;
        private float lastSeek = Float.NaN;
        private int startCalls;
        private int seekCalls;
        private int pauseCalls;

        @Override
        public PlaybackState state() {
            return state;
        }

        @Override
        public PlaybackClock clock() {
            return clock;
        }

        @Override
        public @Nullable PlaybackFailure failure() {
            return failure;
        }

        @Override
        public StartResult start(PlaybackRequest request, Revision expectedRevision) {
            startCalls++;
            lastRequest = request;
            lastRevision = expectedRevision;

            if (startResult.accepted()) {
                failure = null;
                state = PlaybackState.PLAYING;
                clock = new PlaybackClock(request.startTime(), request.speed(), request.loop(),
                        request.animation().duration());
            } else {
                failure = startResult.failure();
                state = PlaybackState.IDLE;
            }

            return startResult;
        }

        @Override
        public void pause() {
            pauseCalls++;

            if (state == PlaybackState.PLAYING) {
                state = PlaybackState.PAUSED;
            }
        }

        @Override
        public void resume() {
            if (state == PlaybackState.PAUSED) {
                state = PlaybackState.PLAYING;
            }
        }

        @Override
        public void seek(float time) {
            seekCalls++;
            lastSeek = time;
            clock = new PlaybackClock(time, clock.speed(), clock.loop(), clock.duration());
        }

        @Override
        public void stopAndReset() {
            state = PlaybackState.IDLE;
            failure = null;
            clock = EMPTY_CLOCK;
        }

        @Override
        public void halt(PlaybackFailure failure) {
            this.failure = failure;
            state = PlaybackState.HALTED;
        }

        @Override
        public @Nullable PreparedFrame prepareFrame(long frameIndex,
                                                    float deltaSeconds,
                                                    EventBudget eventBudget) {
            return null;
        }
    }

    // endregion
}
