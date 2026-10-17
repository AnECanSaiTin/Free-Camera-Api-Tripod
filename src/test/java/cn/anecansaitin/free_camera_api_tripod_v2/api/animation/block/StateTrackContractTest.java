package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 有类型状态轨道与世界采样读数的契约测试（主设计 §3.1.1、§4.7、§7.7）。
///
/// 只验证**纯派生**行为：STEP 求值口径、键时间唯一、时长口径、统一轨道目录与读数三分支。
/// 这些规则同时被求值层、时间轴预览与 Codec 校验引用，因此先以测试固定住语义。
class StateTrackContractTest {

    private static final Identifier TEST_ANCHOR_TYPE =
            Identifier.fromNamespaceAndPath("free_camera_api_tripod", "test_anchor");

    private static final Anchor ANCHOR_A = () -> TEST_ANCHOR_TYPE;

    private static TrackId track(String value) {
        return new TrackId(value);
    }

    private static KeyId key(String value) {
        return new KeyId(value);
    }

    private static CurveTrack channelEndingAt(float endSeconds) {
        Keyframe keyframe = new Keyframe(key("channel-key"), endSeconds, new Constant(0f),
                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR);

        return CurveTrack.ofChannel(track("rotation-x"), "rotation.x", CameraChannel.ROTATION_X,
                Curve.of(keyframe));
    }

    @Test
    void aimTrackStepsAtKeyBoundaries() {
        AimTarget targetA = AimTarget.lookAt(ANCHOR_A);

        AimTrack aim = new AimTrack(track("aim"), "aim", AimState.off(), List.of(
                ValueKeyframe.at(key("k0"), 0f, AimState.target(targetA)),
                ValueKeyframe.at(key("k1"), 10f, AimState.off())));

        // 首键前用默认值不可见（0 秒已有键）；键时间起立即生效，不含插值。
        assertEquals(AimState.target(targetA), aim.at(0f));
        assertEquals(AimState.target(targetA), aim.at(4.999f));
        assertEquals(AimState.off(), aim.at(10f));
        assertEquals(AimState.off(), aim.at(11f));
        assertEquals(10f, aim.duration());
        assertEquals(2, aim.keyCount());
    }

    @Test
    void emptyTrackUsesExplicitDefault() {
        ActiveTrack enabled = ActiveTrack.enabled(track("active-on"));
        assertTrue(enabled.at(0f));
        assertTrue(enabled.at(500f));
        assertEquals(0f, enabled.duration());
        assertEquals(0, enabled.keyCount());

        ActiveTrack disabled = ActiveTrack.disabled(track("active-off"));
        assertFalse(disabled.at(12f));
    }

    @Test
    void keysAreSortedAndDuplicateTimesAreRejected() {
        ActiveTrack sorted = ActiveTrack.of(track("active"), "active", false, List.of(
                ValueKeyframe.at(key("late"), 5f, true),
                ValueKeyframe.at(key("early"), 1f, true)));

        assertEquals(List.of(1f, 5f), sorted.keys().stream().map(ValueKeyframe::time).toList());

        assertThrows(IllegalArgumentException.class, () -> ActiveTrack.of(track("active"), "active", false, List.of(
                ValueKeyframe.at(key("first"), 5f, true),
                ValueKeyframe.at(key("second"), 5f, false))));
    }

    @Test
    void valueKeyframeRejectsInvalidTimesAndNormalizesNegativeZero() {
        float[] invalid = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f};

        for (float time : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> ValueKeyframe.at(key("bad"), time, Boolean.TRUE), "constructor: " + time);
            assertThrows(IllegalArgumentException.class,
                    () -> ValueKeyframe.at(key("ok"), 0f, Boolean.TRUE).withTime(time), "withTime: " + time);
        }

        ValueKeyframe<Boolean> constructed = ValueKeyframe.at(key("zero"), -0.0f, Boolean.TRUE);
        ValueKeyframe<Boolean> shifted = ValueKeyframe.at(key("zero"), 1f, Boolean.TRUE).withTime(-0.0f);

        assertEquals(0, Float.floatToIntBits(constructed.time()));
        assertEquals(0, Float.floatToIntBits(shifted.time()));
    }

    @Test
    void negativeQueryTimeIsAllowedButNegativeKeyTimeIsNot() {
        ActiveTrack active = ActiveTrack.of(track("active"), "active", true, List.of(
                ValueKeyframe.at(key("k"), 1f, Boolean.FALSE)));

        // 查询时间可为负（时间域检查属求值层）；键时间必须非负。
        assertTrue(active.at(0.5f));
        assertTrue(active.at(-10f));
        assertNull(active.keyAt(-1f));
        assertThrows(IllegalArgumentException.class,
                () -> active.withKeys(List.of(ValueKeyframe.at(key("neg"), -1f, Boolean.FALSE))));
    }

    @Test
    void duplicateNormalizedZeroTimesAreRejectedInTracks() {
        assertThrows(IllegalArgumentException.class, () -> ActiveTrack.of(track("active"), "active", true, List.of(
                ValueKeyframe.at(key("plus-zero"), 0f, Boolean.TRUE),
                ValueKeyframe.at(key("minus-zero"), -0.0f, Boolean.FALSE))));
    }

    @Test
    void trackSortsKeysWithoutMutatingInput() {
        List<ValueKeyframe<Boolean>> input = new ArrayList<>(List.of(
                ValueKeyframe.at(key("late"), 5f, Boolean.TRUE),
                ValueKeyframe.at(key("early"), 1f, Boolean.FALSE)));

        ActiveTrack track = ActiveTrack.of(track("active"), "active", true, input);

        assertEquals(List.of(1f, 5f), track.keys().stream().map(ValueKeyframe::time).toList());
        assertEquals(List.of(5f, 1f), input.stream().map(ValueKeyframe::time).toList());

        int sizeBefore = track.keyCount();
        input.clear();
        assertEquals(sizeBefore, track.keyCount());
    }

    @Test
    void nonFiniteQueryTimeAndNonFiniteSampleValueAreRejected() {
        ActiveTrack active = ActiveTrack.enabled(track("active"));

        assertThrows(IllegalArgumentException.class, () -> active.at(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> active.keyAt(Float.POSITIVE_INFINITY));

        assertThrows(IllegalArgumentException.class,
                () -> new SampleReading.Value(Float.NaN, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new SampleReading.Unknown(SampleUnknownReason.CHUNK_NOT_LOADED, -1L));
    }

    @Test
    void rigContentDurationCoversChannelsAimAndActive() {
        AimTrack aim = new AimTrack(track("aim"), "aim", AimState.off(), List.of(
                ValueKeyframe.at(key("aim-k"), 10f, AimState.off())));
        ActiveTrack active = ActiveTrack.of(track("active"), "active", true, List.of(
                ValueKeyframe.at(key("active-k"), 7f, true)));

        CameraRig rig = CameraRig.create(new RigId("rig"), "rig",
                        List.of(channelEndingAt(2f)), aim.id(), active.id())
                .withAim(aim)
                .withView(RigView.full(active));

        assertEquals(2f, rig.channelsDuration());
        assertEquals(10f, rig.contentDuration());
    }

    @Test
    void documentDurationIncludesCameraSourceAndSkipsDisabledRigs() {
        ActiveTrack active = ActiveTrack.enabled(track("active"));
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig",
                        List.of(channelEndingAt(4f)), track("aim"), active.id())
                .withView(RigView.full(active));

        CameraSourceTrack source = new CameraSourceTrack(track("source"), "camera_source",
                CameraSource.player(), List.of(
                ValueKeyframe.at(key("source-k"), 30f, CameraSource.rig(rig.id()))));

        CameraAnimation document = CameraAnimation.create("doc", rig, source.id())
                .withCameraSource(source);

        assertEquals(30f, document.duration());

        CameraAnimation disabled = CameraAnimation.create("doc",
                rig.withView(rig.view().withEnabled(false)), source.id());

        assertEquals(0f, disabled.duration());
    }

    @Test
    void trackDirectoryFindsStateTracksButNotCurves() {
        ActiveTrack active = ActiveTrack.enabled(track("active"));
        AimTrack aim = AimTrack.off(track("aim"));
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(), aim.id(), active.id())
                .withAim(aim)
                .withView(RigView.full(active));

        CameraSourceTrack source = CameraSourceTrack.player(track("source"));
        CameraAnimation document = CameraAnimation.create("doc", rig, source.id())
                .withCameraSource(source);

        assertEquals(aim, document.track(aim.id()).orElseThrow());
        assertEquals(active, document.track(active.id()).orElseThrow());
        assertEquals(source, document.track(source.id()).orElseThrow());

        assertTrue(document.track(aim.id()).orElseThrow() instanceof AimTrack);

        // TrackRef 只接受数值曲线：状态轨道不得被当作曲线轨道解析。
        assertTrue(document.curveTrack(aim.id()).isEmpty());
        assertTrue(document.curveTrack(active.id()).isEmpty());
        assertTrue(document.curveTrack(source.id()).isEmpty());
        assertTrue(document.curveTracks().isEmpty());
    }

    @Test
    void cameraSourceFollowsStepKeys() {
        ActiveTrack active = ActiveTrack.enabled(track("active"));
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(), track("aim"), active.id());

        CameraSourceTrack source = new CameraSourceTrack(track("source"), "camera_source",
                CameraSource.player(), List.of(
                ValueKeyframe.at(key("source-k"), 5f, CameraSource.rig(rig.id()))));

        CameraAnimation document = CameraAnimation.create("doc", rig, source.id())
                .withCameraSource(source);

        assertEquals(CameraSource.player(), document.cameraSourceAt(4.999f));
        assertEquals(CameraSource.rig(rig.id()), document.cameraSourceAt(5f));
        assertEquals(CameraSource.rig(rig.id()), document.cameraSourceAt(60f));
    }

    @Test
    void sameChannelOnTwoRigsNeedsDistinctTrackIds() {
        // 通道语义键只在机位内唯一；身份是 TrackId，必须由调用方显式给出且文档内唯一。
        CurveTrack first = CurveTrack.ofChannel(track("rig-a-rotation-x"), "rotation.x",
                CameraChannel.ROTATION_X, Curve.EMPTY);
        CurveTrack second = CurveTrack.ofChannel(track("rig-b-rotation-x"), "rotation.x",
                CameraChannel.ROTATION_X, Curve.EMPTY);

        CameraRig rigA = CameraRig.create(new RigId("rig-a"), "A", List.of(first),
                track("aim-a"), track("active-a"));
        CameraRig rigB = CameraRig.create(new RigId("rig-b"), "B", List.of(second),
                track("aim-b"), track("active-b"));

        CameraAnimation document = CameraAnimation.create("doc", rigA, track("source"))
                .withRigs(List.of(rigA, rigB));

        assertEquals(first, document.track(first.id()).orElseThrow());
        assertEquals(second, document.track(second.id()).orElseThrow());
        assertEquals(2, document.curveTracks().size());
    }

    @Test
    void sampleReadingSeparatesFallbackableUnknownFromCollectionError() {
        SampleReading value = new SampleReading.Value(3f, 42L);
        assertTrue(value.usable());
        assertFalse(value.fallbackable());

        SampleReading unknown = new SampleReading.Unknown(SampleUnknownReason.DATA_NOT_SYNCED, 42L);
        assertFalse(unknown.usable());
        assertTrue(unknown.fallbackable());

        SampleReading error = new SampleReading.CollectionError(
                SampleCollectionErrorReason.COLLECTOR_THREW, 42L);
        assertFalse(error.usable());
        assertFalse(error.fallbackable());
        assertEquals(42L, error.sampledAtTick());
    }

    @Test
    void copyingKeysAndTracksGeneratesNewIdentityOnlyWhenAsked() {
        AimTrack aim = AimTrack.off(track("aim"));

        AimTrack renamed = aim.withName("looking");
        assertEquals(aim.id(), renamed.id());
        assertEquals("looking", renamed.name());

        AimTrack reIdentified = aim.withId(track("aim-copy"));
        assertEquals(track("aim-copy"), reIdentified.id());
        assertEquals(aim.name(), reIdentified.name());

        ValueKeyframe<Boolean> original = ValueKeyframe.at(key("k"), 1f, true);
        ValueKeyframe<Boolean> copy = original.withId(key("k-copy"));
        assertEquals(key("k-copy"), copy.keyId());
        assertEquals(1f, copy.time());
        assertEquals(Boolean.TRUE, copy.value());
    }
}
