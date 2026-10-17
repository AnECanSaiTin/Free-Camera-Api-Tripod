package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.OverlayId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// `OverlayElement` 的创作数据访问器与时长贡献（审核修正 P2）。
///
/// 固定一件事：`enabled` 只影响合成与**时长贡献**，不能改写 `startTime` / `endTime`
/// 这两个创作数据访问器；用访问器重建对象必须与原对象相等。
class OverlayElementContractTest {

    private static OverlayElement overlay(boolean enabled, float startTime, float endTime) {
        return new OverlayElement(new OverlayId("overlay-1"), OverlayKind.LETTERBOX, enabled, 0,
                startTime, endTime, new Constant(1f), 0f, 0f, "");
    }

    private static CameraAnimation documentWith(OverlayElement overlay) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));

        return CameraAnimation.create("doc", rig, new TrackId("source")).withOverlays(List.of(overlay));
    }

    @Test
    void accessorsKeepAuthoredRangeWhenDisabled() {
        OverlayElement element = overlay(false, 5f, 10f);

        assertEquals(5f, element.startTime());
        assertEquals(10f, element.endTime());

        // 用公开访问器重建同值对象：不能因“未启用”读出 [5, 0] 这种非法区间。
        OverlayElement rebuilt = new OverlayElement(element.id(), element.kind(), element.enabled(),
                element.zOrder(), element.startTime(), element.endTime(), element.opacity(),
                element.fadeInDuration(), element.fadeOutDuration(), element.content());

        assertEquals(element, rebuilt);
    }

    @Test
    void durationContributionOnlyCountsEnabledOverlays() {
        assertEquals(10f, overlay(true, 5f, 10f).durationContribution());
        assertEquals(0f, overlay(false, 5f, 10f).durationContribution());
    }

    @Test
    void disabledOverlayDoesNotExtendDocumentDuration() {
        assertEquals(10f, documentWith(overlay(true, 5f, 10f)).duration());
        assertEquals(0f, documentWith(overlay(false, 5f, 10f)).duration());
    }
}
