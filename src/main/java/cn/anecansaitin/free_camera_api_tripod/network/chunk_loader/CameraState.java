package cn.anecansaitin.free_camera_api_tripod.network.chunk_loader;

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

public record CameraState(boolean enable, boolean update) implements CustomPacketPayload {
    public static final Type<CameraState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "camera_state"));
    public static final StreamCodec<ByteBuf, CameraState> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, (pack) -> pack.enable,
            ByteBufCodecs.BOOL, (pack) -> pack.update,
            CameraState::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CameraState pack, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            CameraData data = player.getData(ModAttachment.CAMERA_DATA);
            data.updateState(pack.enable, pack.update);

            if (!pack.enable && pack.update) {
                CameraTicketController.removeAllChunk((ServerPlayer) player);
                ((ServerLevel) player.level()).getChunkSource().chunkMap.move((ServerPlayer) player);
            }
        });
    }
}
