package cn.anecansaitin.free_camera_api_tripod.core.chunk_loader;

import cn.anecansaitin.freecameraapi.core.ModifierManager;
import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.network.chunk_loader.CameraPos;
import cn.anecansaitin.free_camera_api_tripod.network.chunk_loader.CameraState;
import cn.anecansaitin.free_camera_api_tripod.network.chunk_loader.CameraView;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Vector3f;

import static cn.anecansaitin.freecameraapi.api.ModifierStates.*;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID, value = Dist.CLIENT)
public class CameraChunkLoader {
    public static final CameraChunkLoader INSTANCE = new CameraChunkLoader();
    private ClientChunkCache.Storage cameraStorage;
    private boolean chunkLoaderPrepared;

    public ClientChunkCache.Storage cameraStorage() {
        return cameraStorage;
    }

    public void cameraStorage(ClientChunkCache.Storage storage) {
        cameraStorage = storage;
    }

    public boolean loadingChunk() {
        return chunkLoaderPrepared;
    }

    private void updateChunkLoader() {
        ModifierManager manager = ModifierManager.INSTANCE;

        if (!manager.isStateEnabledAnd(CHUNK_LOADER | ENABLE)) {
            if (chunkLoaderPrepared) {
                chunkLoaderPrepared = false;
                ClientPacketDistributor.sendToServer(new CameraState(false, true));
                cameraStorage.viewCenterX = Integer.MAX_VALUE;
                cameraStorage.viewCenterZ = Integer.MAX_VALUE;
            }

            return;
        }

        Vector3f pos = manager.pos();

        if (!chunkLoaderPrepared) {
            ClientPacketDistributor.sendToServer(
                    new CameraState(true, true),
                    new CameraPos(pos.x, pos.y, pos.z),
                    new CameraView(SectionPos.blockToSectionCoord(pos.x), SectionPos.blockToSectionCoord(pos.z))
            );
            chunkLoaderPrepared = true;
            return;
        }

        int vx = cameraStorage.viewCenterX;
        int vz = cameraStorage.viewCenterZ;
        int nvx = SectionPos.blockToSectionCoord(pos.x);
        int nvz = SectionPos.blockToSectionCoord(pos.z);

        if (vx == nvx && vz == nvz) {
            ClientPacketDistributor.sendToServer(new CameraPos(pos.x, pos.y, pos.z));
            return;
        }

        cameraStorage.viewCenterX = nvx;
        cameraStorage.viewCenterZ = nvz;
        ClientPacketDistributor.sendToServer(new CameraView(nvx, nvz));
    }

    @SubscribeEvent
    public static void levelTick(ClientTickEvent.Post event) {
        CameraChunkLoader.INSTANCE.updateChunkLoader();
    }
}
