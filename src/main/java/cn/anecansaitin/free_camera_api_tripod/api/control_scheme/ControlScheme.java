package cn.anecansaitin.free_camera_api_tripod.api.control_scheme;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public sealed interface ControlScheme {
    VANILLA VANILLA = new VANILLA();
    CAMERA_RELATIVE CAMERA_RELATIVE = new CAMERA_RELATIVE();
    CAMERA_RELATIVE_STRAFE CAMERA_RELATIVE_STRAFE = new CAMERA_RELATIVE_STRAFE();
    PLAYER_RELATIVE_STRAFE PLAYER_RELATIVE_STRAFE = new PLAYER_RELATIVE_STRAFE();

    MutableComponent getTranslation();

    static PLAYER_RELATIVE PLAYER_RELATIVE(int angle) {
        return new PLAYER_RELATIVE(angle);
    }

    record VANILLA() implements ControlScheme {
        @Override
        public MutableComponent getTranslation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.vanilla");
        }
    }

    record CAMERA_RELATIVE() implements ControlScheme {
        @Override
        public MutableComponent getTranslation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.camera_relative");
        }
    }

    record CAMERA_RELATIVE_STRAFE() implements ControlScheme {
        @Override
        public MutableComponent getTranslation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.camera_relative_strafe");
        }
    }

    record PLAYER_RELATIVE(int angle) implements ControlScheme {
        @Override
        public MutableComponent getTranslation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.player_relative", angle);
        }
    }

    record PLAYER_RELATIVE_STRAFE() implements ControlScheme {
        @Override
        public MutableComponent getTranslation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.player_relative_strafe");
        }
    }
}