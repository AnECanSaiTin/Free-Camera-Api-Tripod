package cn.anecansaitin.free_camera_api_tripod.network;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.network.chunk_loader.CameraPos;
import cn.anecansaitin.free_camera_api_tripod.network.chunk_loader.CameraState;
import cn.anecansaitin.free_camera_api_tripod.network.chunk_loader.CameraView;
import cn.anecansaitin.free_camera_api_tripod.network.cmd_camera.PlayerRelativeSetting;
import cn.anecansaitin.free_camera_api_tripod.network.cmd_camera.PresetsDelete;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID)
public class ModPayload {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.0");
        registrar
                .playToServer(
                        CameraState.TYPE,
                        CameraState.CODEC,
                        CameraState::handle
                )
                .playToServer(
                        CameraPos.TYPE,
                        CameraPos.CODEC,
                        CameraPos::handle
                )
                .playToServer(
                        CameraView.TYPE,
                        CameraView.CODEC,
                        CameraView::handle
                )
                .playToServer(
                        PlayerRelativeSetting.TYPE,
                        PlayerRelativeSetting.CODEC,
                        PlayerRelativeSetting::handle
                )
                .playToServer(
                        PresetsDelete.TYPE,
                        PresetsDelete.CODEC,
                        PresetsDelete::handle
                );
    }
}
