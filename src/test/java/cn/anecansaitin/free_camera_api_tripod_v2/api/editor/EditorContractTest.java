package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Selection;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTransaction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/// 编辑器会话 / 视图 / 交互 / 后端契约的结构约束（主设计 §5、§9）。
class EditorContractTest {

    @Test
    void interactionRequestRequiresUniqueNonEmptyOptions() {
        InteractionRequest request = new InteractionRequest(
                new InteractionId(1),
                LocalizedMessage.literal("choose"),
                List.of(new InteractionOption("ok", LocalizedMessage.literal("OK"), false),
                        new InteractionOption("cancel", LocalizedMessage.literal("Cancel"), true)));

        assertEquals(2, request.options().size());
        assertThrows(IllegalArgumentException.class,
                () -> new InteractionId(-1));
        assertThrows(IllegalArgumentException.class, () -> new InteractionRequest(
                new InteractionId(1), LocalizedMessage.literal("x"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new InteractionRequest(
                new InteractionId(1), LocalizedMessage.literal("x"),
                List.of(new InteractionOption("same", LocalizedMessage.literal("a"), false),
                        new InteractionOption("same", LocalizedMessage.literal("b"), false))));
    }

    @Test
    void timelineAndPreviewRangeValidateBounds() {
        assertEquals(100f, TimelineView.defaults().pixelsPerSecond());
        assertThrows(IllegalArgumentException.class, () -> new TimelineView(0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> new TimelineView(1f, -1f));

        assertEquals(1f, new PreviewRange(1f, 2f).in());
        assertThrows(IllegalArgumentException.class, () -> new PreviewRange(1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new PreviewRange(-1f, 1f));

        SnappingSettings snapping = new SnappingSettings(true, 2f,
                List.of(SnapSource.KEYFRAMES, SnapSource.PLAYHEAD));
        assertEquals(2, snapping.sources().size());
        assertThrows(IllegalArgumentException.class,
                () -> new SnappingSettings(true, -1f, List.of()));
    }

    @Test
    void trackVisibilityFiltersOnlyDisplay() {
        TrackId a = new TrackId("a");
        TrackId b = new TrackId("b");
        TrackVisibility hidden = new TrackVisibility(Set.of(a), Set.of());
        TrackVisibility solo = new TrackVisibility(Set.of(a), Set.of(b));

        assertFalse(hidden.isVisible(a));
        assertTrue(hidden.isVisible(b));
        assertTrue(solo.isVisible(b));
        assertFalse(solo.isVisible(a));
    }

    @Test
    void sessionViewStateCopiesContainersAndKeepsPlayhead() {
        Map<RigId, RigPlaybackOverrides> overrides = new HashMap<>();
        overrides.put(new RigId("rig"), RigPlaybackOverrides.INHERIT);
        Set<TrackId> hidden = new HashSet<>(Set.of(new TrackId("track")));

        SessionViewState state = new SessionViewState(
                1.5f,
                Selection.EMPTY,
                TimelineView.defaults(),
                SnappingSettings.disabled(),
                new PreviewRange(0f, 2f),
                new TrackVisibility(hidden, Set.of()),
                EditorToolState.MOVE,
                new RigId("rig"),
                overrides);

        overrides.clear();
        hidden.clear();

        assertEquals(1, state.rigOverrides().size());
        assertEquals(1, state.trackVisibility().hidden().size());
        assertEquals(EditorToolState.MOVE, state.tool());
        assertEquals(RigPlaybackOverrides.INHERIT,
                state.previewOverrides(new RigId("rig")).orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> new SessionViewState(-1f, Selection.EMPTY, TimelineView.defaults(),
                        SnappingSettings.disabled(), null, TrackVisibility.allVisible(),
                        EditorToolState.SELECT, null, Map.of()));
    }

    @Test
    void editDraftStateKeepsTextAndCandidacy() {
        EditTransaction transaction = new EditTransaction(
                1, Revision.INITIAL, new EditTarget.Document(), LocalizedMessage.literal("edit"));
        Map<String, String> fieldTexts = new HashMap<>();
        fieldTexts.put("name", "draft");

        EditDraftState draft = new EditDraftState(
                transaction, fieldTexts, new EditOp.RenameDocument("draft"), List.of());
        fieldTexts.clear();

        assertEquals("draft", draft.fieldTexts().get("name"));
        assertTrue(draft.hasCandidate());

        Diagnostic error = new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal("bad"), new DiagnosticScope.File(new ResourceId("test")),
                null, null);
        EditDraftState invalid = new EditDraftState(
                transaction, Map.of("name", "?"), new EditOp.RenameDocument("?"), List.of(error));
        assertFalse(invalid.hasCandidate());
    }

    @Test
    void backendSelectorDistinguishesAutoAndExplicit() {
        assertTrue(EditorBackendSelector.auto().isAuto());
        assertNull(EditorBackendSelector.auto().backendId());

        Identifier id = Identifier.fromNamespaceAndPath("test", "backend");
        EditorBackendSelector explicit = EditorBackendSelector.of(id);
        assertFalse(explicit.isAuto());
        assertEquals(id, explicit.backendId());
    }
}
