package cn.anecansaitin.free_camera_api_tripod.network.chunk_loader;

import cn.anecansaitin.free_camera_api_tripod.CameraAdditionConfig;
import cn.anecansaitin.free_camera_api_tripod.core.chunk_loader.CameraTicketController;
import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.attachment.chunk_loader.CameraData;
import cn.anecansaitin.free_camera_api_tripod.attachment.ModAttachment;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CameraView(int x, int z) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CameraView> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "camera_radius"));
    public static final StreamCodec<ByteBuf, CameraView> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, pack -> pack.x,
            ByteBufCodecs.VAR_INT, pack -> pack.z,
            CameraView::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CameraView pack, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            CameraData data = player.getData(ModAttachment.CAMERA_DATA);
            int radius = CameraAdditionConfig.cameraChunkLoadRadius((ServerPlayer) player);
            boolean updateView = data.updateView(pack.x, pack.z, radius);

            if (updateView) {
                int currentX = data.view.x();
                int currentZ = data.view.z();

                int minX = currentX - radius;
                int maxX = currentX + radius;
                int minZ = currentZ - radius;
                int maxZ = currentZ + radius;

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        CameraTicketController.addChunk((ServerLevel) player.level(), player, x, z);
                    }
                }
            }
        });
    }
}
