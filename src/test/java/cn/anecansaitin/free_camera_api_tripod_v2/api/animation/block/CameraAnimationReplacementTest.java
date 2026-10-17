package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/// `CameraAnimation.withRig` 的无变化派生与替换语义（主设计 §4.5，复核 A4）。
///
/// 固定三件事：未找到或内容相等时返回原实例；真实替换保序；原文档不被修改。
class CameraAnimationReplacementTest {

    private static CameraRig rig(String id, String name) {
        return CameraRig.create(new RigId(id), name, List.of(),
                new TrackId("aim-" + id), new TrackId("active-" + id));
    }

    private static CameraAnimation document() {
        return CameraAnimation.create("doc", rig("a", "A"), new TrackId("source"))
                .withRigs(List.of(rig("a", "A"), rig("b", "B")));
    }

    @Test
    void missingRigReturnsSameDocument() {
        CameraAnimation document = document();

        assertSame(document, document.withRig(rig("missing", "X")));
    }

    @Test
    void equalReplacementReturnsSameDocument() {
        CameraAnimation document = document();

        assertSame(document, document.withRig(document.rigs().get(0)));
    }

    @Test
    void realReplacementPreservesOrderAndLeavesOriginalUntouched() {
        CameraAnimation document = document();
        CameraRig originalFirst = document.rigs().get(0);

        CameraAnimation replaced = document.withRig(originalFirst.withName("A2"));

        assertNotSame(document, replaced);
        assertEquals(new RigId("a"), replaced.rigs().get(0).id());
        assertEquals("A2", replaced.rigs().get(0).name());
        assertEquals(new RigId("b"), replaced.rigs().get(1).id());
        assertSame(document.rigs().get(1), replaced.rigs().get(1));

        // 原文档保持原值，派生替换不污染输入。
        assertEquals("A", document.rigs().get(0).name());
        assertEquals(2, document.rigs().size());
    }
}
