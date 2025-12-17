package cn.anecansaitin.free_camera_api_tripod.api.control_scheme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;

public sealed interface ControlScheme {
    VANILLA VANILLA = new VANILLA();
    CAMERA_RELATIVE CAMERA_RELATIVE = new CAMERA_RELATIVE();
    CAMERA_RELATIVE_STRAFE CAMERA_RELATIVE_STRAFE = new CAMERA_RELATIVE_STRAFE();
    PLAYER_RELATIVE_STRAFE PLAYER_RELATIVE_STRAFE = new PLAYER_RELATIVE_STRAFE();

    HashMap<String, MapCodec<? extends ControlScheme>> CODEC_TYPE = new HashMap<>(Map.of(VANILLA.type(), VANILLA.mapCodec(), CAMERA_RELATIVE.type(), CAMERA_RELATIVE.mapCodec(), CAMERA_RELATIVE_STRAFE.type(), CAMERA_RELATIVE_STRAFE.mapCodec(), PLAYER_RELATIVE_STRAFE.type(), PLAYER_RELATIVE_STRAFE.mapCodec(), PLAYER_RELATIVE.TYPE, PLAYER_RELATIVE.MAP_CODEC));
    Codec<ControlScheme> CODEC = Codec.STRING.dispatch(
            ControlScheme::type,
            CODEC_TYPE::get
    );

    MutableComponent translation();
    String type();

    static PLAYER_RELATIVE PLAYER_RELATIVE(int angle) {
        return new PLAYER_RELATIVE(angle);
    }

    record VANILLA() implements ControlScheme {
        public static final String TYPE = "vanilla";
        public static final MapCodec<VANILLA> MAP_CODEC = MapCodec.unit(() -> ControlScheme.VANILLA);

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

    record CAMERA_RELATIVE() implements ControlScheme {
        public static final String TYPE = "camera_relative";
        public static final MapCodec<CAMERA_RELATIVE> MAP_CODEC = MapCodec.unit(() -> ControlScheme.CAMERA_RELATIVE);

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

    record CAMERA_RELATIVE_STRAFE() implements ControlScheme {
        public static final String TYPE = "camera_relative_strafe";
        public static final MapCodec<CAMERA_RELATIVE_STRAFE> MAP_CODEC = MapCodec.unit(ControlScheme.CAMERA_RELATIVE_STRAFE);

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

    record PLAYER_RELATIVE(int angle) implements ControlScheme {
        public static final String TYPE = "player_relative";
        public static final MapCodec<PLAYER_RELATIVE> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("angle").forGetter(PLAYER_RELATIVE::angle)
        ).apply(instance, PLAYER_RELATIVE::new));

        @Override
        public MutableComponent translation() {
            return Component.translatable("free_camera_api_tripod.control_scheme.player_relative", angle);
        }

        @Override
        public String type() {
            return TYPE;
        }
    }

    record PLAYER_RELATIVE_STRAFE() implements ControlScheme {
        public static final String TYPE = "player_relative_strafe";
        public static final MapCodec<PLAYER_RELATIVE_STRAFE> MAP_CODEC = MapCodec.unit(() -> ControlScheme.PLAYER_RELATIVE_STRAFE);

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