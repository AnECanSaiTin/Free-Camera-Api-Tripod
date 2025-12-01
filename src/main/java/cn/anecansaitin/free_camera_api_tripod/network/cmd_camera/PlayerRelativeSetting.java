package cn.anecansaitin.free_camera_api_tripod.network.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CmdCamera;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayerRelativeSetting(String presets, int angle) implements CustomPacketPayload {
    public static final Type<PlayerRelativeSetting> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "player_relative_setting"));
    public static final StreamCodec<ByteBuf, PlayerRelativeSetting> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, pack -> pack.presets,
            ByteBufCodecs.VAR_INT, pack -> pack.angle,
            PlayerRelativeSetting::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerRelativeSetting pack, IPayloadContext context) {
        context.enqueueWork(() -> {
            ControlScheme.PLAYER_RELATIVE scheme = new ControlScheme.PLAYER_RELATIVE(pack.angle);
            boolean updated = CmdCamera.instance().update(pack.presets, state -> state.setScheme(scheme));
            Player player = context.player();

            if (updated) {
                player.displayClientMessage(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.set", Component.literal(pack.presets).withStyle(ChatFormatting.GOLD), scheme.translation().withStyle(ChatFormatting.GREEN)), false);
                MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.set.click").withStyle(ChatFormatting.BLUE);
                click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera presets view " + pack.presets)));
                player.displayClientMessage(click, false);
                player.displayClientMessage(Component.literal("───────────"), false);
            } else {
                player.displayClientMessage(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", Component.literal(pack.presets).withStyle(ChatFormatting.GOLD)), false);
            }
        });
    }
}
