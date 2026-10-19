package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.ValidationLayer;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 类型级校验钩子（主设计 §2.2、§4.2）。
class TypeValidationContractTest {

    @Test
    void contextCarriesStableLocationAndRevision() {
        TypeValidationContext context = new TypeValidationContext(
                new Revision(1), new TrackId("track"), null, "keys[0].time");

        assertEquals(new Revision(1), context.revision());
        assertEquals("keys[0].time", context.fieldPath());
        assertThrows(IllegalArgumentException.class, () -> new TypeValidationContext(
                new Revision(1), new TrackId("track"), null, " "));
    }

    @Test
    void validatorDefaultsToStructureAndReturnsList() {
        TypeValidator<String> validator = (value, context) -> List.of();
        assertEquals(ValidationLayer.STRUCTURE, validator.layer());
        assertEquals(List.of(), validator.validate("x", new TypeValidationContext(
                new Revision(1), new TrackId("track"), null, "value")));
    }
}
