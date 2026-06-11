package cn.anecansaitin.free_camera_api_tripod.core.animation.test;

import cn.anecansaitin.free_camera_api_tripod.core.animation.Clip;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Curve;
import cn.anecansaitin.free_camera_api_tripod.core.animation.ImmutableKeyframe;
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
        clip.addKey("test", new ImmutableKeyframe(0, 0));
        clip.addKey("test", new ImmutableKeyframe(1, 1));
        clip.addKey("test", new ImmutableKeyframe(2, 2));
        clip.addKey("test", new ImmutableKeyframe(3, -1));
        clip.addKey("test", new ImmutableKeyframe(4, -2));
        clip.addKey("test", new ImmutableKeyframe(5, 0));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        float time = 1f;

        for (int i = 0; i < 100; i++) {
            float test = clip.evaluate("test", time);
            float x0 = time * 100;
            float y0 = test * 100;
            fillFloat(graphics, x0, y0, x0 + 1, y0 + 1, 0xFFFFFFFF);
            time += 0.01f;
        }
    }

    private void fillFloat(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int col) {
        graphics.submitGuiElementRenderState(new FloatFill(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()), x0, y0, x1, y1, col, col, graphics.peekScissorStack()));
    }
}
