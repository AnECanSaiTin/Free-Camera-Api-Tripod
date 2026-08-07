package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface NameTagFeatureRendererStorageExtension {
    default void add(
            PoseStack poseStack,
            @Nullable Vec3 nameTagAttachment,
            int offset,
            Component name,
            boolean seeThrough,
            int lightCoords,
            double distanceToCameraSq,
            CameraRenderState camera,
            int color,
            int backgroundColor) {
    }
}
