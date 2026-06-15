package cn.anecansaitin.free_camera_api_tripod.core.animation.test;

import cn.anecansaitin.free_camera_api_tripod.api.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Clip;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Curve;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;

public class ClipTestGui extends Screen {
    private final Clip clip = new Clip();

    public ClipTestGui() {
        super(Component.empty());
        clip.addCurve("test", new Curve());
        clip.addKey("test", Keyframe.hermite(0, 0, 0, 0));
        clip.addKey("test", Keyframe.hermite(1, 1, 0, 0));
        clip.addKey("test", Keyframe.hermite(2, 2, 0, 0));
        clip.addKey("test", Keyframe.hermite(3, -1, 0, 0));
        clip.addKey("test", Keyframe.hermite(4, -2, 0, 0));
        clip.addKey("test", Keyframe.hermite(5, 0, 0, 0));
        clip.addKey("test", Keyframe.hermite(6, 5, 0, 0));
        clip.addKey("test", Keyframe.hermite(7, 3, 0, 0));
        clip.addKey("test", Keyframe.hermite(8, -4, 0, 0));
        clip.addKey("test", Keyframe.hermite(9, 0, 0, 0));
        Curve test = clip.curve("test");
        test.smoothTangents(1f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        float time = 0f;

        for (int i = 0; i < 900; i++) {
            float test = clip.evaluate("test", time);
            float x0 = time * 10 + 100;
            float y0 = test * 10 + 100;
            fillFloat(graphics, x0, y0, x0 + 1, y0 + 1, 0xFFFFFFFF);
            time = 0.01f * i;
        }
    }

    private void fillFloat(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int col) {
        graphics.submitGuiElementRenderState(new FloatFill(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()), x0, y0, x1, y1, col, col, graphics.peekScissorStack()));
    }
}
