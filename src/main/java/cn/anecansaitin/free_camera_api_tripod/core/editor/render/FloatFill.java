package cn.anecansaitin.free_camera_api_tripod.core.editor.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/// 浮点坐标的矩形填充。
///
/// 原版 {@code GuiGraphicsExtractor.fill} 只接受整数坐标，曲线会被量化成阶梯；
/// 曲线图与切线手柄使用本渲染状态以获得平滑连续的线条。
public record FloatFill(RenderPipeline pipeline,
                        TextureSetup textureSetup,
                        Matrix3x2fc pose,
                        float x0,
                        float y0,
                        float x1,
                        float y1,
                        int col1,
                        int col2,
                        @Nullable ScreenRectangle scissorArea,
                        @Nullable ScreenRectangle bounds) implements GuiElementRenderState {

    public FloatFill(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float x0, float y0, float x1, float y1, int col1, int col2, @Nullable ScreenRectangle scissorArea) {
        this(pipeline, textureSetup, pose, x0, y0, x1, y1, col1, col2, scissorArea, getBounds(x0, y0, x1, y1, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y0()).setColor(this.col1());
        vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y1()).setColor(this.col2());
        vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y1()).setColor(this.col2());
        vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y0()).setColor(this.col1());
    }

    private static @Nullable ScreenRectangle getBounds(float x0, float y0, float x1, float y1, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle bounds = (new ScreenRectangle((int) x0, (int) y0, (int) (x1 - x0) + 1, (int) (y1 - y0) + 1)).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }
}
