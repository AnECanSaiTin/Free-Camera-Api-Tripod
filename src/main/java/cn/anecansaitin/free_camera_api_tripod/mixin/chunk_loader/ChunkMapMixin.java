package cn.anecansaitin.free_camera_api_tripod.mixin.chunk_loader;

import cn.anecansaitin.free_camera_api_tripod.core.chunk_loader.attachment.CameraData;
import cn.anecansaitin.free_camera_api_tripod.registry.ModAttachment;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    protected abstract void markChunkPendingToSend(ServerPlayer player, ChunkPos pos);

    @Inject(method = "updateChunkTracking", at = @At(value = "HEAD"))
    private void freeCameraAPI$updateChunkTracking(ServerPlayer player, CallbackInfo ci) {
        // 发送相机附近的区块信息到客户端
        CameraData data = player.getData(ModAttachment.CAMERA_DATA);

        if (!data.enable) {
            if (data.update) {
                // 玩家退出相机视角并恢复到正常视角后，再次发送玩家周围方块信息，避免区块渲染缺失
                player.getChunkTrackingView().forEach(chunkPos -> markChunkPendingToSend(player, chunkPos));
//                CameraChunkLoader.INSTANCE.cameraStorage();
                data.update = false;
            }

            return;
        }

        if (!data.update) {
            return;
        }

        ChunkTrackingView.difference(player.getChunkTrackingView(), data.view, chunkPos -> markChunkPendingToSend(player, chunkPos), chunkPos -> {});
        data.update = false;
    }

    @ModifyExpressionValue(method = "onChunkReadyToSend", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkTrackingView;contains(Lnet/minecraft/world/level/ChunkPos;)Z"))
    public boolean freeCameraAPI$onChunkReadyToSend(boolean original, @Local ChunkPos pos, @Local ServerPlayer player) {
        // 让相机区块能通过可视范围检测
        if (original) {
            return true;
        }

        CameraData data = player.getData(ModAttachment.CAMERA_DATA);

        if (!data.enable) {
            return false;
        }

        return data.view.contains(pos);
    }

    @Inject(method = "isChunkTracked", at = @At("HEAD"), cancellable = true)
    private void freeCameraAPI$isChunkTracked(ServerPlayer player, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        // 让相机范围内区块保持更新
        CameraData data = player.getData(ModAttachment.CAMERA_DATA);

        if (!data.enable || !data.view.contains(x, z)) {
            return;
        }

        if (!player.connection.chunkSender.isPending(ChunkPos.asLong(x, z))) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "dropChunk", at = @At("HEAD"), cancellable = true)
    private static void freeCameraAPI$onDropChunk(ServerPlayer player, ChunkPos pos, CallbackInfo ci) {
        // 确保玩家在相机范围内区块不被丢弃
        CameraData data = player.getData(ModAttachment.CAMERA_DATA);

        if (!data.enable || !data.view.contains(pos)) {
            return;
        }

        ci.cancel();
    }
}
