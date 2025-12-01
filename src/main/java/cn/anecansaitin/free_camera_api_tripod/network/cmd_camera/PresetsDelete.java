package cn.anecansaitin.free_camera_api_tripod.network.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CmdCamera;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PresetsDelete(String presets) implements CustomPacketPayload {
    public static final Type<PresetsDelete> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "presets_delete"));
    public static final StreamCodec<ByteBuf, PresetsDelete> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, pack -> pack.presets,
            PresetsDelete::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PresetsDelete pack, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (CmdCamera.instance().remove(pack.presets)) {
                Player player = context.player();
                player.displayClientMessage(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.delete", Component.literal(pack.presets).withStyle(ChatFormatting.GOLD)), false);
                player.displayClientMessage(Component.literal("───────────"), false);
            } else {
                Player player = context.player();
                player.displayClientMessage(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", Component.literal(pack.presets).withStyle(ChatFormatting.GOLD)), false);
                player.displayClientMessage(Component.literal("───────────"), false);
            }
        });
    }
}
