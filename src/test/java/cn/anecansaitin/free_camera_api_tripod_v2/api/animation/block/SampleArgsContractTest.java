package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `WorldSample` 参数名义接口的契约测试（主设计 §7.7，Q7 裁决）。
///
/// 固定两件事：参数必须是 `SampleArgs` 值；`kind` 与 `args.type()` 不一致的组合
/// 不能构造成功，避免注册表按一种类型分派却解出另一种参数。
class SampleArgsContractTest {

    private static final Identifier LIGHT = Identifier.fromNamespaceAndPath("free_camera_api_tripod", "light");
    private static final Identifier BLOCK_MATCH =
            Identifier.fromNamespaceAndPath("free_camera_api_tripod", "block_match");

    /// 测试用采样参数：只持有类型标识，模拟“坐标 / 方块 id”一类静态参数。
    private record TestArgs(Identifier type) implements SampleArgs {
    }

    private static WorldSample sampleOf(Identifier kind, SampleArgs args) {
        return new WorldSample(new SampleId("sample-1"), "ground_is_stone", kind, args, WorldSample.EVERY_TICK);
    }

    @Test
    void matchingKindAndArgsTypeIsAccepted() {
        WorldSample sample = sampleOf(LIGHT, new TestArgs(LIGHT));

        assertEquals(LIGHT, sample.kind());
        assertEquals(LIGHT, sample.args().type());
        assertEquals(1, sample.resampleEveryTicks());
    }

    @Test
    void mismatchedKindAndArgsTypeIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sampleOf(LIGHT, new TestArgs(BLOCK_MATCH)));

        assertTrue(error.getMessage().contains("args.type()"));
    }

    @Test
    void nullArgsAreRejected() {
        assertThrows(NullPointerException.class, () -> new WorldSample(
                new SampleId("sample-2"), "light", LIGHT, null, 1));
    }

    @Test
    void derivedSamplesKeepTypeConsistency() {
        WorldSample sample = sampleOf(LIGHT, new TestArgs(LIGHT));

        WorldSample slower = sample.withResampleEveryTicks(20);
        assertEquals(20, slower.resampleEveryTicks());
        assertEquals(LIGHT, slower.args().type());

        // 换参数时沿用原 kind，构造器会重新检查一致性。
        assertThrows(IllegalArgumentException.class,
                () -> sample.withArgs(new TestArgs(BLOCK_MATCH)));
    }
}
