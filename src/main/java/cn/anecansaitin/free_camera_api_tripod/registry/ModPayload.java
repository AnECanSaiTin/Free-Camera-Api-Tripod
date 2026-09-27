package cn.anecansaitin.free_camera_api_tripod.registry;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlaybackClient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID)
public class ModPayload {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.0");

        // 处理逻辑包在 lambda 里：真正的客户端类要等这条包到达时才加载，
        // 若在这里直接写方法引用，专用服务端在注册阶段就会去碰仅客户端存在的类
        registrar.playToClient(CameraPlaybackPayload.TYPE, CameraPlaybackPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CameraPlaybackClient.handle(payload)));
    }
}
