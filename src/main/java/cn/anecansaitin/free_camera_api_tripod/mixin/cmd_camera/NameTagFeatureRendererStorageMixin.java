package cn.anecansaitin.free_camera_api_tripod.mixin.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.NameTagFeatureRendererStorageExtension;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(targets = "net.minecraft.client.renderer.feature.NameTagFeatureRenderer$Storage")
public abstract class NameTagFeatureRendererStorageMixin implements NameTagFeatureRendererStorageExtension {
    @Final
    @Shadow
    private List<SubmitNodeStorage.NameTagSubmit> nameTagSubmitsSeethrough;

    @Final
    @Shadow
    private List<SubmitNodeStorage.NameTagSubmit> nameTagSubmitsNormal;

    @Override
    public void add(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords, double distanceToCameraSq, CameraRenderState camera, int color, int backgroundColor) {
        if (nameTagAttachment != null) {
            Minecraft minecraft = Minecraft.getInstance();
            poseStack.pushPose();
            poseStack.translate(nameTagAttachment.x, nameTagAttachment.y + 0.5, nameTagAttachment.z);
            poseStack.mulPose(camera.orientation);
            poseStack.scale(0.025F, -0.025F, 0.025F);
            Matrix4f pose = new Matrix4f(poseStack.last().pose());
            float x = -minecraft.font.width(name) / 2.0F;
            if (seeThrough) {
                this.nameTagSubmitsNormal
                        .add(
                                new SubmitNodeStorage.NameTagSubmit(
                                        pose, x, offset, name, LightCoordsUtil.lightCoordsWithEmission(lightCoords, 2), color, backgroundColor, distanceToCameraSq
                                )
                        );
                this.nameTagSubmitsSeethrough
                        .add(new SubmitNodeStorage.NameTagSubmit(pose, x, offset, name, lightCoords, color, backgroundColor, distanceToCameraSq));
            } else {
                this.nameTagSubmitsNormal
                        .add(new SubmitNodeStorage.NameTagSubmit(pose, x, offset, name, lightCoords, color, backgroundColor, distanceToCameraSq));
            }

            poseStack.popPose();
        }
    }
}
