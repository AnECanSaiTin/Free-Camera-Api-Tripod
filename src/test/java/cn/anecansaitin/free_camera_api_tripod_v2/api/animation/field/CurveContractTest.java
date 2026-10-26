package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// `Curve` 的时间顺序与容器边界（主设计 §4.4，复核 A0）。
///
/// 固定三件事：乱序键构造后严格递增、原输入列表不被修改、时间重复（含 `+0` / `-0`
/// 归一后重复）被拒绝；容器做防御性复制。
class CurveContractTest {

    private static Keyframe key(String id, float time) {
        return new Keyframe(new KeyId(id), time, new Constant(0f),
                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR);
    }

    private static List<Float> times(List<Keyframe> keys) {
        return keys.stream().map(Keyframe::time).toList();
    }

    @Test
    void unorderedKeysAreSortedAndInputIsNotMutated() {
        List<Keyframe> input = new ArrayList<>(List.of(
                key("late", 2f),
                key("first", 0f),
                key("middle", 1f)));

        Curve curve = new Curve(input, Curve.Wrap.CLAMP, Curve.Wrap.CLAMP);

        assertEquals(List.of(0f, 1f, 2f), times(curve.keys()));
        assertEquals(List.of(2f, 0f, 1f), times(input));

        int sizeBefore = curve.size();
        input.clear();
        assertEquals(sizeBefore, curve.size());
    }

    @Test
    void duplicateKeyIdsAtDifferentTimesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Curve(
                List.of(key("same", 0f), key("same", 1f)), Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));
    }

    @Test
    void duplicateTimesAreRejectedEvenWhenZeroSignDiffers() {
        assertThrows(IllegalArgumentException.class, () -> new Curve(
                List.of(key("a", 1f), key("b", 1f)), Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));

        // -0.0 构造时归一为 +0.0，与 0.0 同轨即构成重复时间。
        assertThrows(IllegalArgumentException.class, () -> new Curve(
                List.of(key("a", 0f), key("b", -0.0f)), Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));
    }
}
