package cn.anecansaitin.free_camera_api_tripod.core.animation.test;

import cn.anecansaitin.free_camera_api_tripod.core.animation.Path;
import cn.anecansaitin.free_camera_api_tripod.core.animation.PathMode;
import cn.anecansaitin.free_camera_api_tripod.core.animation.PathNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;
import org.joml.Matrix3x2f;
import org.joml.Vector3f;

public class PathTestGui extends Screen {
    private final Path path = new Path();

    public PathTestGui() {
        super(Component.empty());
        path.node(new PathNode(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), PathMode.BEZIER));
        path.node(new PathNode(new Vector3f(1, 1, 0), new Vector3f(-1, 0, 0), new Vector3f(1, 0, 0), PathMode.BEZIER));
        path.node(new PathNode(new Vector3f(2, -2, 0), new Vector3f(-1, 0, 0), new Vector3f(0.2f, 0, 0), PathMode.BEZIER));
        path.node(new PathNode(new Vector3f(3, 3, 0), new Vector3f(-0.2f, 0, 0), new Vector3f(0, 0, 0), PathMode.BEZIER));
        path.node(new PathNode(new Vector3f(4, -4, 0), new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), PathMode.BEZIER));
    }

    @Override
    protected void init() {
        addRenderableWidget(new ExtendedButton(0, 0, 10, 10, Component.literal("+"), (_) -> up()));
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

        if (path.size() < 2) {
            return;
        }

        float delta = 0.01f;
        Vector3f dest = new Vector3f();

        for (int i = 0; i < 100; i++) {
            path.evaluate(delta * i, dest);
            float x0 = dest.x * 10 + 100;
            float y0 = dest.y * 10 + 100;
            fillFloat(graphics, x0, y0, x0 + 1, y0 + 1, 0xFFFFFFFF);
        }
    }

    private void fillFloat(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int col) {
        graphics.submitGuiElementRenderState(new FloatFill(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()), x0, y0, x1, y1, col, col, graphics.peekScissorStack()));
    }

    public void up() {
        path.node(0, new PathNode(new Vector3f(0, -1, 0), new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), PathMode.LINEAR));
    }
}
