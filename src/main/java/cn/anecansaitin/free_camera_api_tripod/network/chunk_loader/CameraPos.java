package cn.anecansaitin.free_camera_api_tripod.network.chunk_loader;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.attachment.chunk_loader.CameraData;
import cn.anecansaitin.free_camera_api_tripod.attachment.ModAttachment;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CameraPos(float x, float y, float z) implements CustomPacketPayload {
    public static final Type<CameraPos> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "camera_pos"));
    public static final StreamCodec<ByteBuf, CameraPos> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, (pack) -> pack.x,
            ByteBufCodecs.FLOAT, (pack) -> pack.y,
            ByteBufCodecs.FLOAT, (pack) -> pack.z,
            CameraPos::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CameraPos pack, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            CameraData data = player.getData(ModAttachment.CAMERA_DATA);
            data.updatePos(pack.x, pack.y, pack.z);
        });
    }
}
