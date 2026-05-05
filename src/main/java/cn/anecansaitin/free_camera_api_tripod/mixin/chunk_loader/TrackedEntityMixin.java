package cn.anecansaitin.free_camera_api_tripod.mixin.chunk_loader;

import cn.anecansaitin.free_camera_api_tripod.core.chunk_loader.CameraChunkLoader;
import cn.anecansaitin.free_camera_api_tripod.core.chunk_loader.attachment.CameraData;
import cn.anecansaitin.free_camera_api_tripod.registry.ModAttachment;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {
    @Shadow
    @Final
    public Entity entity;

    @ModifyVariable(method = "updatePlayer", name = "flag", at = @At(value = "JUMP", opcode = Opcodes.IFEQ, shift = At.Shift.BEFORE, ordinal = 2))
    public boolean freeCameraAPI$modifyFlag(boolean original, ServerPlayer player, @Local(ordinal = 0) double radius) {
        // 让相机范围内的实体能更新
        if (!CameraChunkLoader.INSTANCE.loadingChunk()) {
            return original;
        }

        CameraData data = player.getData(ModAttachment.CAMERA_DATA);

        if (!data.enable) {
            return original;
        }

        Vec3 distance = entity.position().subtract(data.x, data.y, data.z);

        if (distance.x >= -radius && distance.x <= radius && distance.z >= -radius && data.z <= radius) {
            return true;
        }

        return original;
    }
}