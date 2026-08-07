package cn.anecansaitin.free_camera_api_tripod.mixin.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.OrderedSubmitNodeCollectorExtension;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(OrderedSubmitNodeCollector.class)
public interface OrderedSubmitNodeCollectorMixin extends OrderedSubmitNodeCollectorExtension {
}
