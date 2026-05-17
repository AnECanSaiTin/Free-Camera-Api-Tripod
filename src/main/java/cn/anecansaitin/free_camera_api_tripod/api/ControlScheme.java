package cn.anecansaitin.free_camera_api_tripod.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;

public sealed interface ControlScheme {
    Vanilla VANILLA = new Vanilla();
    CameraRelative CAMERA_RELATIVE = new CameraRelative();
    CameraRelativeStrafe CAMERA_RELATIVE_STRAFE = new CameraRelativeStrafe();
    PlayerRelativeStrafe PLAYER_RELATIVE_STRAFE = new PlayerRelativeStrafe();

    Codec<ControlScheme> CODEC = Codec.STRING.dispatch(
            ControlScheme::type,
            ControlScheme::getCodec
    );

    MutableComponent translation();

    String type();

    private static MapCodec<? extends ControlScheme> getCodec(String type) {
        return switch (type) {
            case Vanilla.TYPE -> Vanilla.MAP_CODEC;
            case CameraRelative.TYPE -> CameraRelative.MAP_CODEC;
            case CameraRelativeStrafe.TYPE -> CameraRelativeStrafe.MAP_CODEC;
            case PlayerRelative.TYPE -> PlayerRelative.MAP_CODEC;
            case PlayerRelativeStrafe.TYPE -> PlayerRelativeStrafe.MAP_CODEC;
            default -> null;
        };
    }

    static PlayerRelative playerRelative(int angle) {
        return new PlayerRelative(angle);
    }

    record Vanilla() implements ControlScheme {
        public static final String TYPE = "vanilla";
        public static final MapCodec<Vanilla> MAP_CODEC = MapCodec.unit(() -> ControlScheme.VANILLA);

        @Override
        public MutableComponent translation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.vanilla");
        }

        @Override
        public String type() {
            return TYPE;
        }

        public MapCodec<? extends ControlScheme> mapCodec() {
            return MAP_CODEC;
        }
    }

    record CameraRelative() implements ControlScheme {
        public static final String TYPE = "camera_relative";
        public static final MapCodec<CameraRelative> MAP_CODEC = MapCodec.unit(() -> ControlScheme.CAMERA_RELATIVE);

        @Override
        public MutableComponent translation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.camera_relative");
        }

        @Override
        public String type() {
            return TYPE;
        }

        public MapCodec<? extends ControlScheme> mapCodec() {
            return MAP_CODEC;
        }
    }

    record CameraRelativeStrafe() implements ControlScheme {
        public static final String TYPE = "camera_relative_strafe";
        public static final MapCodec<CameraRelativeStrafe> MAP_CODEC = MapCodec.unit(ControlScheme.CAMERA_RELATIVE_STRAFE);

        @Override
        public MutableComponent translation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.camera_relative_strafe");
        }

        @Override
        public String type() {
            return TYPE;
        }

        public MapCodec<? extends ControlScheme> mapCodec() {
            return MAP_CODEC;
        }
    }

    record PlayerRelative(int angle) implements ControlScheme {
        public static final String TYPE = "player_relative";
        public static final MapCodec<PlayerRelative> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("angle").forGetter(PlayerRelative::angle)
        ).apply(instance, PlayerRelative::new));

        @Override
        public MutableComponent translation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.player_relative", angle);
        }

        @Override
        public String type() {
            return TYPE;
        }
    }

    record PlayerRelativeStrafe() implements ControlScheme {
        public static final String TYPE = "player_relative_strafe";
        public static final MapCodec<PlayerRelativeStrafe> MAP_CODEC = MapCodec.unit(() -> ControlScheme.PLAYER_RELATIVE_STRAFE);

        @Override
        public MutableComponent translation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.player_relative_strafe");
        }

        @Override
        public String type() {
            return TYPE;
        }

        public MapCodec<? extends ControlScheme> mapCodec() {
            return MAP_CODEC;
        }
    }
}