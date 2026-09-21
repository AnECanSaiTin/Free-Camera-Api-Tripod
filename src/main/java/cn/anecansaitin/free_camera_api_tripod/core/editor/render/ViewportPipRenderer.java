package cn.anecansaitin.free_camera_api_tripod.core.editor.render;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;

/// 视窗离屏画面：把整帧游戏画面抓进纹理，再由 GUI 通道贴回视窗矩形。
///
/// 本版本的世界渲染被 FrameGraph 绑定在主渲染目标上，不能单独渲进别的纹理；
/// 而 GUI 通道一旦开始，主目标就在被写入、无法再采样。PiP 的准备阶段恰好位于
/// 「世界已画完、GUI 通道尚未创建」之间，因此借它做一次主目标到离屏纹理的拷贝。
///
/// 关键在于主渲染目标的 **alpha 通道不可信**：天空圆盘没有覆盖到的地平线一带是 0
/// （MC 存截图时也得手动补成 0xFF）。若把它一并搬进离屏纹理，贴回视窗时那块会被判成
/// 透明而漏出面板底色。所以拷贝时把离屏纹理先刷成不透明黑，再只覆盖颜色、保留目标
/// 的 alpha，使离屏纹理恒为不透明。
public final class ViewportPipRenderer extends PictureInPictureRenderer<ViewportPipRenderer.State> {
    /// 拷贝用管线：颜色直接覆盖（ONE/ZERO），alpha 保留目标值（ZERO/ONE）
    private static final RenderPipeline COPY_OPAQUE = RenderPipeline.builder()
            .withLocation("pipeline/viewport_copy_opaque")
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withSampler("InSampler")
            .withColorTargetState(new ColorTargetState(
                    Optional.of(new BlendFunction(SourceFactor.ONE, DestFactor.ZERO, SourceFactor.ZERO, DestFactor.ONE)), 15))
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build();

    /// 上一帧 PiP 分配出来的离屏纹理，贴回视窗时作为采样源
    private @Nullable GpuTextureView textureView;

    public ViewportPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    @Override
    protected String getTextureLabel() {
        return "camera editor viewport";
    }

    @Override
    protected void renderToTexture(State renderState, PoseStack poseStack) {
        GpuTextureView destination = RenderSystem.outputColorTextureOverride;
        this.textureView = destination;

        if (destination == null) {
            return;
        }

        RenderTarget source = Minecraft.getInstance().getMainRenderTarget();

        if (source.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        // 底色刷成不透明黑，随后的拷贝只写颜色，离屏纹理的 alpha 便恒为 1
        encoder.clearColorTexture(destination.texture(), 0xFF000000);

        try (RenderPass pass = encoder.createRenderPass(() -> "Camera editor viewport", destination, OptionalInt.empty())) {
            pass.setPipeline(COPY_OPAQUE);
            pass.bindTexture("InSampler", source.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }

    @Override
    protected void blitTexture(State renderState, GuiRenderState guiRenderState) {
        if (this.textureView == null) {
            return;
        }

        // 离屏纹理恒不透明，预乘混合即等于整块覆盖
        guiRenderState.addBlitToCurrentLayer(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(this.textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                renderState.pose(),
                renderState.x0(),
                renderState.y0(),
                renderState.x1(),
                renderState.y1(),
                0.0F,
                1.0F,
                1.0F,
                0.0F,
                -1,
                renderState.scissorArea()
        ));
    }

    /// 视窗矩形（GUI 逻辑坐标）、缩放与裁剪区
    public record State(int x0, int y0, int x1, int y1, float scale,
                        @Nullable ScreenRectangle scissorArea) implements PictureInPictureRenderState {
        @Override
        public ScreenRectangle bounds() {
            return new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
        }
    }

    @EventBusSubscriber(modid = FreeCameraApiTripod.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onRegister(RegisterPictureInPictureRenderersEvent event) {
            event.register(State.class, ViewportPipRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
            event.registerPipeline(COPY_OPAQUE);
        }
    }
}
