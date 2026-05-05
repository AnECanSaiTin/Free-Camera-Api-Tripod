package cn.anecansaitin.free_camera_api_tripod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

public class CameraAdditionConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue CAMERA_CHUNK_LOAD_RADIUS;
    public static final ModConfigSpec SPEC;

    static {
        CAMERA_CHUNK_LOAD_RADIUS =
                BUILDER
                        .comment("  The max radius of the chunk loading area.", "  Directly take the minimum of the rendering distance and the maximum radius.", "  \"1\" indicates the same as the rendering distance.")
                        .defineInRange("camera_chunk_loading_radius", 1, 1, 32);

        SPEC = BUILDER.build();
    }

    public static int cameraChunkLoadRadius(ServerPlayer player) {
        int radius = CAMERA_CHUNK_LOAD_RADIUS.getAsInt();
        int renderDistance = Mth.clamp(player.requestedViewDistance(), 2, player.level().getServer().getPlayerList().getViewDistance());

        if (radius == 1) {
            return renderDistance;
        } else {
            return Math.min(renderDistance, radius);
        }
    }

    public static int cameraChunkLoadRadius(int renderDistance) {
        int radius = CAMERA_CHUNK_LOAD_RADIUS.getAsInt();

        if (radius == 1) {
            return renderDistance;
        } else {
            return Math.min(renderDistance, radius);
        }
    }
}
