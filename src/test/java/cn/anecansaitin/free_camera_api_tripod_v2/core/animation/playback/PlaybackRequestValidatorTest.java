package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 播放请求的启动前静态检查：局部空间锚点覆盖不能只看显式列出的机位。
class PlaybackRequestValidatorTest {

    private static final RigId RIG = new RigId("rig");
    private static final Anchor ANCHOR =
            () -> Identifier.fromNamespaceAndPath("test", "anchor");

    @Test
    void localRigWithoutDefaultAnchorIsRejectedEvenWhenOverrideMapIsEmpty() {
        CameraAnimation document = document(new Space.Local(null, RotationMask.NONE));

        List<Diagnostic> diagnostics = PlaybackRequestValidator.validate(
                request(document, Map.of()), Revision.INITIAL);

        assertTrue(hasMessage(diagnostics, "requires an anchor"), () -> "unexpected: " + diagnostics);
    }

    @Test
    void localRigWithoutDefaultAnchorAcceptsRequestAnchor() {
        CameraAnimation document = document(new Space.Local(null, RotationMask.NONE));
        Map<RigId, RigPlaybackOverrides> overrides = Map.of(RIG,
                new RigPlaybackOverrides(ANCHOR, null, AimOverride.inherit(), null));

        List<Diagnostic> diagnostics = PlaybackRequestValidator.validate(
                request(document, overrides), Revision.INITIAL);

        assertTrue(diagnostics.isEmpty(), () -> "unexpected: " + diagnostics);
    }

    @Test
    void localRigWithDocumentDefaultAnchorIsAccepted() {
        CameraAnimation document = document(new Space.Local(ANCHOR, RotationMask.NONE));

        List<Diagnostic> diagnostics = PlaybackRequestValidator.validate(
                request(document, Map.of()), Revision.INITIAL);

        assertTrue(diagnostics.isEmpty(), () -> "unexpected: " + diagnostics);
    }

    @Test
    void unknownOverrideRigIsStillRejected() {
        CameraAnimation document = document(new Space.World());
        Map<RigId, RigPlaybackOverrides> overrides = Map.of(new RigId("missing"),
                RigPlaybackOverrides.INHERIT);

        List<Diagnostic> diagnostics = PlaybackRequestValidator.validate(
                request(document, overrides), Revision.INITIAL);

        assertFalse(diagnostics.isEmpty());
        assertTrue(hasMessage(diagnostics, "missing rig"), () -> "unexpected: " + diagnostics);
    }

    private static CameraAnimation document(Space space) {
        CameraRig rig = CameraRig.create(RIG, "rig", List.of(),
                new TrackId("aim"), new TrackId("active")).withSpace(space);
        return CameraAnimation.create("doc", rig, new TrackId("source"));
    }

    private static PlaybackRequest request(CameraAnimation document,
                                           Map<RigId, RigPlaybackOverrides> overrides) {
        return new PlaybackRequest(document, overrides, 1f, false, 0f);
    }

    private static boolean hasMessage(List<Diagnostic> diagnostics, String fragment) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.message() instanceof
                LocalizedMessage.Literal literal && literal.text().contains(fragment));
    }
}