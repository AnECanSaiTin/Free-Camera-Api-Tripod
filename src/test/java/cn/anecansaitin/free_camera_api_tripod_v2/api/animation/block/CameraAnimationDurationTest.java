package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.AudioRefId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.OverlayId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// `CameraAnimation.duration()` 的取最大口径（主设计 §3.1、§3.4，复核 A1/A7）。
///
/// 锁定：启用机位的通道 / 注视 / 活动、相机来源、文档轨道、标记、音轨与启用叠加
/// 全部参与取最大；禁用机位不参与；标记仍按当前设计贡献时长（A7）。
class CameraAnimationDurationTest {

    private static TrackId track(String value) {
        return new TrackId(value);
    }

    private static KeyId key(String value) {
        return new KeyId(value);
    }

    private static CurveTrack channelEndingAt(String id, float endSeconds) {
        Keyframe keyframe = new Keyframe(key("channel-key"), endSeconds, new Constant(0f),
                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR);

        return CurveTrack.ofChannel(track(id), "rotation.x", CameraChannel.ROTATION_X,
                Curve.of(keyframe));
    }

    private static OverlayElement overlay(boolean enabled, float endTime) {
        return new OverlayElement(new OverlayId("overlay-1"), OverlayKind.LETTERBOX, enabled, 0,
                0f, endTime, new Constant(1f), 0f, 0f, "");
    }

    /// 测试用文档级扩展轨道：只提供时长与键数，不参与求值。
    private record TestExtensionTrack(TrackId id, String name, float duration)
            implements AnimationTrack.ExtensionTrack {

        public TestExtensionTrack withName(String newName) {
            return new TestExtensionTrack(id, newName, duration);
        }

        @Override
        public Identifier type() {
            return Identifier.fromNamespaceAndPath("free_camera_api_tripod", "test_extension");
        }

        @Override
        public int keyCount() {
            return 1;
        }
    }

    @Test
    void durationTakesMaximumOfAllContributionsAndSkipsDisabledRigs() {
        ActiveTrack activeA = ActiveTrack.of(track("active-a"), "active", true, List.of(
                ValueKeyframe.at(key("a-active"), 30f, Boolean.TRUE)));
        AimTrack aimA = new AimTrack(track("aim-a"), "aim", AimState.off(), List.of(
                ValueKeyframe.at(key("a-aim"), 20f, AimState.off())));
        CameraRig enabledRig = CameraRig.create(new RigId("a"), "A",
                        List.of(channelEndingAt("ch-a", 10f)), aimA.id(), activeA.id())
                .withAim(aimA)
                .withView(RigView.full(activeA));

        // 禁用机位带有更远的键（2000 秒），不得参与时长。
        ActiveTrack activeB = ActiveTrack.of(track("active-b"), "active", true, List.of(
                ValueKeyframe.at(key("b-active"), 2000f, Boolean.TRUE)));
        CameraRig disabledRig = CameraRig.create(new RigId("b"), "B", List.of(),
                        track("aim-b"), activeB.id())
                .withView(RigView.full(activeB).withEnabled(false));

        CameraSourceTrack source = new CameraSourceTrack(track("source"), "camera_source",
                CameraSource.player(), List.of(
                ValueKeyframe.at(key("source-key"), 50f, CameraSource.player())));

        CameraAnimation document = CameraAnimation.create("doc", enabledRig, source.id())
                .withCameraSource(source)
                .withRigs(List.of(enabledRig, disabledRig))
                .withTracks(List.<AnimationTrack.ExtensionTrack>of(new TestExtensionTrack(track("ext"), "test-extension", 40f)))
                .withMarkers(List.of(Marker.at(new MarkerId("marker"), 300f)))
                .withAudioRefs(List.of(new AudioRef(new AudioRefId("audio"),
                        "assets/free_camera_api_tripod/test.ogg", null, 2f, 0f, 4f, 1f)))
                .withOverlays(List.of(overlay(true, 7f)));

        // 最大贡献来自标记（300 秒）；其余为来源 50、扩展 40、机位 30、叠加 7、音轨 6。
        assertEquals(300f, document.duration());

        // 移除标记后取其余最大结束时间；标记按当前设计参与时长（A7），不是隐藏规则。
        assertEquals(50f, document.withMarkers(List.of()).duration());
    }

    @Test
    void markerBeyondAllContentExtendsCurrentDuration() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig",
                List.of(channelEndingAt("ch-rig", 10f)), track("aim"), track("active"));

        CameraAnimation document = CameraAnimation.create("doc", rig, track("source"))
                .withMarkers(List.of(Marker.at(new MarkerId("marker"), 300f)));

        assertEquals(300f, document.duration());
    }
}
