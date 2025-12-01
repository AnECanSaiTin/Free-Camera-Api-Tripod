package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.client.screen;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.network.cmd_camera.PresetsDelete;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class PresetsDeleteScreen extends Screen {
    private final String presets;

    public PresetsDeleteScreen(String presets) {
        super(Component.literal(""));
        this.presets = presets;
    }

    @Override
    protected void init() {
        MutableComponent title = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.delete.title", Component.literal(presets).withStyle(ChatFormatting.GOLD));
        StringWidget stringWidget = new StringWidget(title, font);
        stringWidget.setX(width / 2 - font.width(title) / 2);
        stringWidget.setY(height / 2 - 20);
        addRenderableOnly(stringWidget);
        addRenderableWidget(new ExtendedButton(width / 2 - 65, height / 2, 60, 20, Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.confirm"), (button) -> {
            ClientPacketDistributor.sendToServer(new PresetsDelete(presets));
            onClose();
        }));
        addRenderableWidget(new ExtendedButton(width / 2 + 5, height / 2, 60, 20, Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.cancel"), (button) -> onClose()));
    }
}
