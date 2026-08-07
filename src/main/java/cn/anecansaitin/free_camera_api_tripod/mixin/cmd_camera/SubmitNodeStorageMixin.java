package cn.anecansaitin.free_camera_api_tripod.mixin.cmd_camera;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SubmitNodeStorage.class)
public abstract class SubmitNodeStorageMixin implements SubmitNodeCollector {
    @Override
    public void submitNameTag(
            PoseStack poseStack,
            @Nullable Vec3 nameTagAttachment,
            final int offset,
            Component name,
            boolean seeThrough,
            int lightCoords,
            double distanceToCameraSq,
            final CameraRenderState camera,
            int textColor,
            int backgroundColor
    ) {
        this.order(0).submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera, textColor, backgroundColor);
    }
}
