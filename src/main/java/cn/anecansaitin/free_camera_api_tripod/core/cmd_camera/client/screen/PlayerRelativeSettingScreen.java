package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.client.screen;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.network.cmd_camera.PlayerRelativeSetting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

public class PlayerRelativeSettingScreen extends Screen {
    private final String presets;

    public PlayerRelativeSettingScreen(String presets) {
        super(Component.literal(""));
        this.presets = presets;
    }

    @Override
    protected void init() {
        MutableComponent title = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.options.player_relative");
        EditBox rotEdit = new EditBox(font, width / 2 - 50, height / 2 - 10, 100, 20, title) {
            @Override
            public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
                if (keyCode != GLFW.GLFW_KEY_ENTER) {
                    return false;
                }

                int rot = Integer.parseInt(getValue().isBlank() ? "0" : getValue());
                ClientPacketDistributor.sendToServer(new PlayerRelativeSetting(presets, rot));
                onClose();
                return true;
            }
        };
        rotEdit.setFilter(value -> value.isEmpty() || value.matches("[0-9]+"));
        addRenderableWidget(rotEdit);
        StringWidget stringWidget = new StringWidget(title, font);
        stringWidget.setX(width / 2 - font.width(title) / 2);
        stringWidget.setY(height / 2 - 20);
        addRenderableOnly(stringWidget);
    }
}
