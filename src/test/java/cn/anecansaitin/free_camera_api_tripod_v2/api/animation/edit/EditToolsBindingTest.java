package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackLookup;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 编辑工具的输入快照与注视捕获绑定（主设计 §4.6、§4.7）。
class EditToolsBindingTest {

    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private static AnimationSnapshot snapshot(String name) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        return new AnimationSnapshot(new Revision(1),
                CameraAnimation.create(name, rig, new TrackId("source")));
    }

    private static EvaluationContext context(AnimationSnapshot snapshot) {
        FrameFacts facts = new FrameFacts(
                snapshot, DIMENSION, 7L, 1L, 0f,
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime(12000, 24000),
                1L, Map.of(), Map.of(), true);
        TrackLookup lookup = trackId -> Optional.empty();
        TrackIndex index = new TrackIndex() {
            @Override
            public AnimationSnapshot snapshot() {
                return snapshot;
            }

            @Override
            public Optional<Entry> find(TrackId trackId) {
                return Optional.empty();
            }

            @Override
            public List<Diagnostic> diagnostics() {
                return List.of();
            }
        };

        return EvaluationContext.bind(1, snapshot, facts, lookup, index);
    }

    @Test
    void easingAndBatchRequestsBindTheInputSnapshot() {
        AnimationSnapshot first = snapshot("first");
        AnimationSnapshot second = snapshot("second");
        EasingPreset preset = new EasingPreset(
                "smooth", Curve.Interpolation.HERMITE, 0f, 0f, 0.2f, 0.2f);

        EasingRequest easing = new EasingRequest(
                new TrackId("t"), List.of(new KeyId("k")), preset, first);
        BatchTransformRequest batch = new BatchTransformRequest(
                new TrackId("t"), List.of(new KeyId("k")),
                new BatchTransform(0f, 1f, 1f, false), second);

        assertEquals(first, easing.snapshot());
        assertEquals(second, batch.snapshot());
        assertNotEquals(easing.snapshot(), batch.snapshot());
        assertThrows(NullPointerException.class,
                () -> new EasingRequest(new TrackId("t"), List.of(new KeyId("k")), preset, null));
        assertThrows(NullPointerException.class,
                () -> new BatchTransformRequest(new TrackId("t"), List.of(new KeyId("k")),
                        new BatchTransform(0f, 1f, 0f, false), null));
    }

    @Test
    void aimCaptureCarriesModeOverrideAndMatchingRevision() {
        AnimationSnapshot snapshot = snapshot("aim");
        EvaluationContext context = context(snapshot);
        AimCaptureRequest request = new AimCaptureRequest(
                new RigId("rig"), 1f, AimCaptureMode.CURRENT,
                AimOverride.inherit(), new Revision(1), context);

        assertEquals(AimCaptureMode.CURRENT, request.mode());
        assertEquals(AimOverride.inherit(), request.override());
        assertEquals(new Revision(1), request.baseRevision());

        assertThrows(IllegalArgumentException.class, () -> new AimCaptureRequest(
                new RigId("rig"), 1f, AimCaptureMode.LEFT_TARGET,
                AimOverride.inherit(), new Revision(2), context));
    }
}
