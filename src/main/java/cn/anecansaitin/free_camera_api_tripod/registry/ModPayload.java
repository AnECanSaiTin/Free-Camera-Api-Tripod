package cn.anecansaitin.free_camera_api_tripod.registry;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.core.chunk_loader.network.CameraPos;
import cn.anecansaitin.free_camera_api_tripod.core.chunk_loader.network.CameraState;
import cn.anecansaitin.free_camera_api_tripod.core.chunk_loader.network.CameraView;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.network.PlayerRelativeSetting;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.network.PresetsDelete;
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
                );
    }
}
