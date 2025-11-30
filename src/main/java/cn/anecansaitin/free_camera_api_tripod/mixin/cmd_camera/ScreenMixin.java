package cn.anecansaitin.free_camera_api_tripod.mixin.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.client.screen.PlayerRelativeSettingScreen;
import cn.anecansaitin.freecameraapi.ClientUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private static final ResourceLocation CLICK_EVENT_ID = ResourceLocation.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "player_relative_setting");

    @Inject(method = "defaultHandleGameClickEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"), cancellable = true)
    private static void defaultHandleGameClickEvent(ClickEvent clickEvent, Minecraft minecraft, Screen screen, CallbackInfo ci) {
        ClickEvent.Custom event = (ClickEvent.Custom) clickEvent;

        if (!event.id().equals(CLICK_EVENT_ID)) {
            return;
        }

        event.payload().ifPresent(payload -> ClientUtil.pushGuiLayer(new PlayerRelativeSettingScreen(payload.asString().get())));
        ci.cancel();
    }
}
