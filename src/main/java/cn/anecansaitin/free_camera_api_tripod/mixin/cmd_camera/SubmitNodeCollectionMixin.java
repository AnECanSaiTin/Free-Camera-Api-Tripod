package cn.anecansaitin.free_camera_api_tripod.mixin.cmd_camera;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin implements OrderedSubmitNodeCollector {
    @Shadow
    private boolean wasUsed = false;
    @Final
    @Shadow
    private NameTagFeatureRenderer.Storage nameTagSubmits;

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
        this.wasUsed = true;
        this.nameTagSubmits.add(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera, textColor, backgroundColor);
    }
}
