package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 全局属性绑定、来源与默认关闭模型。
class PropertyModelTest {

    @Test
    void allAnimatablePropertiesAndOutputDefaultDisabled() {
        RigProperties properties = RigProperties.disabled();

        assertTrue(properties.position().x().isDisabled());
        assertTrue(properties.rotation().source().isDisabled());
        assertTrue(properties.fov().source().isDisabled());
        assertTrue(properties.active().source().isDisabled());
        assertTrue(properties.rect() instanceof RectProperty.Normalized normalized
                && normalized.width().isDisabled());
        assertTrue(properties.obstacle().enabled().isDisabled());
        assertFalse(RigOutput.HIDDEN.enabled());
        assertFalse(new PlaybackRange.Auto().equals(new PlaybackRange.Manual(0f, 1f)));
    }

    @Test
    void numericBindingRoundTrips() {
        PropertyBinding<NumericSource> binding =
                PropertyBinding.enabled(new NumericSource.Track(new TrackId("number")));

        var json = PropertyBinding.codec(NumericSource.CODEC)
                .encodeStart(JsonOps.INSTANCE, binding).result().orElseThrow();
        PropertyBinding<NumericSource> decoded = PropertyBinding.codec(NumericSource.CODEC)
                .parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(binding, decoded);
    }

    @Test
    void quaternionEulerSourceRoundTrips() {
        QuaternionSource source = new QuaternionSource.EulerComposite(
                new NumericSource.Constant(10),
                new NumericSource.Formula("t * 2"),
                new NumericSource.Constant(0),
                QuaternionSource.RotationOrder.YXZ,
                QuaternionSource.AngleUnit.DEGREES);

        var json = QuaternionSource.CODEC.encodeStart(JsonOps.INSTANCE, source).result().orElseThrow();
        QuaternionSource decoded = QuaternionSource.CODEC.parse(JsonOps.INSTANCE, json)
                .result().orElseThrow();

        assertEquals(source, decoded);
    }

    @Test
    void quaternionConstantIsFinite() {
        QuaternionSource source = new QuaternionSource.Constant(Quaternion.IDENTITY);

        assertTrue(source instanceof QuaternionSource.Constant constant
                && constant.value().equals(Quaternion.IDENTITY));
    }

    @Test
    void manualPlaybackRangeValidatesAndRoundTrips() {
        PlaybackRange range = new PlaybackRange.Manual(1f, 4f);

        var json = PlaybackRange.CODEC.encodeStart(JsonOps.INSTANCE, range).result().orElseThrow();
        PlaybackRange decoded = PlaybackRange.CODEC.parse(JsonOps.INSTANCE, json)
                .result().orElseThrow();

        assertEquals(range, decoded);
    }
}
