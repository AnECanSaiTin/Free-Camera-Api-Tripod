package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 结构解码的字段级路径（主设计 §4.2、§8.2）。
class StructureFailureContractTest {

    @Test
    void structureFailureCarriesOptionalFieldPath() {
        DocumentDecodeResult.StructureFailed withPath =
                new DocumentDecodeResult.StructureFailed("bad field", "rigs[0].channels[1].keys[0].time");
        DocumentDecodeResult.StructureFailed legacy = new DocumentDecodeResult.StructureFailed("bad input");

        assertEquals("rigs[0].channels[1].keys[0].time", withPath.fieldPath());
        assertNull(legacy.fieldPath());
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentDecodeResult.StructureFailed("bad", " "));
        assertThrows(NullPointerException.class,
                () -> new DocumentDecodeResult.StructureFailed(null));
    }
}
