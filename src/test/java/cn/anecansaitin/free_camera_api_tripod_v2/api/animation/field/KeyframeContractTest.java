package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `Keyframe` 取值字段的类型边界（主设计 §6.1，审核修正 P1）。
///
/// 固定一件事：关键帧字段的静态类型是 `NumberSource`，而 `TrackRef` 只实现
/// `ValueSource`，因此轨道读数**编译期**就进不了关键帧。这个测试把该边界钉在
/// record 组件上，避免以后有人把它放宽回 `ValueSource`。
class KeyframeContractTest {

    private static final KeyId KEY = new KeyId("key-1");

    private static Keyframe keyframe(NumberSource source) {
        return new Keyframe(KEY, 1f, source, 0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR);
    }

    @Test
    void valueComponentIsNumberSource() {
        RecordComponent value = Arrays.stream(Keyframe.class.getRecordComponents())
                .filter(component -> component.getName().equals("value"))
                .findFirst()
                .orElseThrow();

        assertEquals(NumberSource.class, value.getType());

        // TrackRef 只实现 ValueSource，不实现 NumberSource：类型上不能进入关键帧。
        assertFalse(NumberSource.class.isAssignableFrom(TrackRef.class));
        assertTrue(ValueSource.class.isAssignableFrom(TrackRef.class));
    }

    @Test
    void constantKeyframeExposesItsValue() {
        Keyframe keyframe = keyframe(new Constant(2.5f));

        assertFalse(keyframe.isFormula());
        assertEquals(2.5f, keyframe.constantValue());
        assertEquals(7f, keyframe.withValue(new Constant(7f)).constantValue());
    }

    @Test
    void formulaKeyframeIsDetectedAndHasNoConstantValue() {
        Keyframe keyframe = keyframe(new Formula("t + 1"));

        assertTrue(keyframe.isFormula());
        // constantValue() 只对固定值有效；公式键读取应抛异常而不是返回兜底数值。
        assertThrows(ClassCastException.class, keyframe::constantValue);
    }

    @Test
    void nullValueIsRejected() {
        assertThrows(NullPointerException.class, () -> new Keyframe(
                KEY, 0f, null, 0f, 0f, 0f, 0f, Curve.Interpolation.STEP));
    }

    @Test
    void timeMustBeFiniteAndNonNegative() {
        float[] invalid = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, -0.5f};
        Keyframe reference = new Keyframe(KEY, 1f, new Constant(0f),
                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR);

        for (float time : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Keyframe(KEY, time, new Constant(0f), 0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR),
                    "constructor: " + time);
            assertThrows(IllegalArgumentException.class, () -> reference.withTime(time), "withTime: " + time);
        }
    }

    @Test
    void negativeZeroIsNormalizedToPositiveZero() {
        Keyframe constructed = new Keyframe(KEY, -0.0f, new Constant(0f),
                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR);
        Keyframe shifted = new Keyframe(KEY, 1f, new Constant(0f),
                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR).withTime(-0.0f);

        assertEquals(0, Float.floatToIntBits(constructed.time()));
        assertEquals(0, Float.floatToIntBits(shifted.time()));
    }
}
