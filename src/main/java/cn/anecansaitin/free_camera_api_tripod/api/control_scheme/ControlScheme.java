package cn.anecansaitin.free_camera_api_tripod.api.control_scheme;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public sealed interface ControlScheme {
    VANILLA VANILLA = new VANILLA();
    CAMERA_RELATIVE CAMERA_RELATIVE = new CAMERA_RELATIVE();
    CAMERA_RELATIVE_STRAFE CAMERA_RELATIVE_STRAFE = new CAMERA_RELATIVE_STRAFE();
    PLAYER_RELATIVE_STRAFE PLAYER_RELATIVE_STRAFE = new PLAYER_RELATIVE_STRAFE();

    Component getTranslation();

    static PLAYER_RELATIVE PLAYER_RELATIVE(int angle) {
        return new PLAYER_RELATIVE(angle);
    }

    record VANILLA() implements ControlScheme {
        private static final Component TRANSLATION = Component.translatable("free_camera_api_tripod.control_scheme.vanilla").withStyle(ChatFormatting.GREEN);

        @Override
        public Component getTranslation() {
            return TRANSLATION;
        }
    }

    record CAMERA_RELATIVE() implements ControlScheme {
        private static final Component TRANSLATION = Component.translatable("free_camera_api_tripod.control_scheme.camera_relative").withStyle(ChatFormatting.GREEN);

        @Override
        public Component getTranslation() {
            return TRANSLATION;
        }
    }

    record CAMERA_RELATIVE_STRAFE() implements ControlScheme {
        private static final Component TRANSLATION = Component.translatable("free_camera_api_tripod.control_scheme.camera_relative_strafe").withStyle(ChatFormatting.GREEN);

        @Override
        public Component getTranslation() {
            return TRANSLATION;
        }
    }

    record PLAYER_RELATIVE(int angle) implements ControlScheme {
        @Override
        public Component getTranslation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.player_relative", angle).withStyle(ChatFormatting.GREEN);
        }
    }

    record PLAYER_RELATIVE_STRAFE() implements ControlScheme {
        private static final Component TRANSLATION = Component.translatable("free_camera_api_tripod.control_scheme.player_relative_strafe").withStyle(ChatFormatting.GREEN);

        @Override
        public Component getTranslation() {
            return TRANSLATION;
        }
    }
}