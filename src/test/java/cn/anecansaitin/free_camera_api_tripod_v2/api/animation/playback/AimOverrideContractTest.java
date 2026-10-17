package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 注视覆盖三态的契约测试（主设计 §6.3、§7.1，Q5 裁决）。
///
/// 固定三件事：`Inherit` / `Disabled` / `Replace` 互斥且可判别；
/// 固定目标不能为空；覆盖类型不进入文档（本测试只验证契约本身）。
class AimOverrideContractTest {

    private static final Identifier ANCHOR_TYPE =
            Identifier.fromNamespaceAndPath("free_camera_api_tripod", "test_anchor");

    private static final Anchor ANCHOR = () -> ANCHOR_TYPE;

    @Test
    void inheritIsTheAbsenceOfOverride() {
        AimOverride override = AimOverride.inherit();

        assertTrue(override.inherits());
        assertFalse(override.disables());
        assertTrue(override.fixedTarget().isEmpty());
        assertEquals(new AimOverride.Inherit(), override);
    }

    @Test
    void disabledIsExplicitOff() {
        AimOverride override = AimOverride.disabled();

        assertFalse(override.inherits());
        assertTrue(override.disables());
        assertTrue(override.fixedTarget().isEmpty());
        assertEquals(new AimOverride.Disabled(), override);
    }

    @Test
    void replaceCarriesAFixedTargetForTheWholeRun() {
        AimTarget target = AimTarget.lookAt(ANCHOR);
        AimOverride override = AimOverride.replace(target);

        assertFalse(override.inherits());
        assertFalse(override.disables());
        assertEquals(target, override.fixedTarget().orElseThrow());
        assertEquals(new AimOverride.Replace(target), override);
    }

    @Test
    void replaceWithoutTargetIsRejected() {
        assertThrows(NullPointerException.class, () -> new AimOverride.Replace(null));
    }
}
