package cn.anecansaitin.free_camera_api_tripod.core.animation.test;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
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

        graphics.horizontalLine(100, 300, 60, 0xff000000);
        graphics.horizontalLine(100, 300, 70, 0xff000000);
        graphics.horizontalLine(100, 300, 80, 0xff000000);
        graphics.horizontalLine(100, 300, 90, 0xff000000);
        graphics.horizontalLine(100, 300, 100, 0xffff0000);
        graphics.horizontalLine(100, 300, 110, 0xff000000);
        graphics.horizontalLine(100, 300, 120, 0xff000000);
        graphics.horizontalLine(100, 300, 130, 0xff000000);
        graphics.horizontalLine(100, 300, 140, 0xff000000);
        graphics.horizontalLine(100, 300, 150, 0xff000000);
        graphics.verticalLine(100, 50, 200, 0xff00ff00);
        graphics.verticalLine(110, 50, 200, 0xff000000);
        graphics.verticalLine(120, 50, 200, 0xff000000);
        graphics.verticalLine(130, 50, 200, 0xff000000);
        graphics.verticalLine(140, 50, 200, 0xff000000);
        graphics.verticalLine(150, 50, 200, 0xff000000);

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
