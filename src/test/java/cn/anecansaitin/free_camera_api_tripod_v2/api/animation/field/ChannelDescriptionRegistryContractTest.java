package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 相机通道描述注册表（主设计 §8.1）。
class ChannelDescriptionRegistryContractTest {

    @Test
    void builtInDescribesEveryChannel() {
        ChannelDescriptionRegistry registry = ChannelDescriptionRegistry.builtIn();

        for (CameraChannel channel : CameraChannel.values()) {
            assertEquals(channel, registry.description(channel).channel());
        }

        assertEquals("free_camera_api_tripod:channel.position_x",
                registry.description(CameraChannel.POSITION_X).labelKey());
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelDescription(CameraChannel.FOV, " ", 0, "degrees"));
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelDescription(CameraChannel.FOV, "key", 0x1000000, "degrees"));
    }

    @Test
    void registryRequiresCompleteAndConsistentEntries() {
        EnumMap<CameraChannel, ChannelDescription> incomplete = new EnumMap<>(CameraChannel.class);
        incomplete.put(CameraChannel.FOV,
                new ChannelDescription(CameraChannel.FOV, "key", 0, "degrees"));
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelDescriptionRegistry(incomplete));

        EnumMap<CameraChannel, ChannelDescription> mismatched = new EnumMap<>(ChannelDescriptionRegistry.builtIn().descriptions());
        mismatched.put(CameraChannel.FOV,
                new ChannelDescription(CameraChannel.PATH_DISTANCE, "key", 0, "degrees"));
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelDescriptionRegistry(mismatched));
    }
}
