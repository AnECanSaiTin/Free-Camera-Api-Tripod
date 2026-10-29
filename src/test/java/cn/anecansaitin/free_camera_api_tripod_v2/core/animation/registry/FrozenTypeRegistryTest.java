package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackType;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 类型注册表的冻结、重复 ID 与查询语义。
class FrozenTypeRegistryTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "track");

    @Test
    void rejectsDuplicateId() {
        FrozenTrackTypeRegistry registry = new FrozenTrackTypeRegistry();
        registry.register(new TestTrackType());

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new TestTrackType()));
    }

    @Test
    void freezesAndRejectsLateRegistration() {
        FrozenTrackTypeRegistry registry = new FrozenTrackTypeRegistry();
        registry.register(new TestTrackType());
        registry.freeze();

        assertTrue(registry.frozen());
        assertTrue(registry.find(TYPE).isPresent());
        assertEquals(1, registry.ids().size());
        assertFalse(registry.find(Identifier.fromNamespaceAndPath("test", "missing")).isPresent());
        assertThrows(IllegalStateException.class, () -> registry.register(new TestTrackType()));

        registry.freeze();
        assertTrue(registry.frozen());
    }

    private record TestTrack(TrackId id, String name) implements AnimationTrack.ExtensionTrack {
        public TestTrack withName(String newName) {
            return new TestTrack(id, newName);
        }

        @Override
        public Identifier type() {
            return TYPE;
        }

        @Override
        public float duration() {
            return 0f;
        }

        @Override
        public int keyCount() {
            return 0;
        }
    }

    private static final class TestTrackType implements TrackType<TestTrack> {
        private final TestTrack track = new TestTrack(new TrackId("test"), "test");

        @Override
        public Identifier id() {
            return TYPE;
        }

        @Override
        public MapCodec<TestTrack> codec() {
            return MapCodec.unit(track);
        }

        @Override
        public Optional<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeValidator<TestTrack>>
        validator() {
            return Optional.empty();
        }
    }
}
