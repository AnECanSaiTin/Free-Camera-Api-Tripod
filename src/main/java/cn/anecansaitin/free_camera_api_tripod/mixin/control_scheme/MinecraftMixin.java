package cn.anecansaitin.free_camera_api_tripod.mixin.control_scheme;

import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.free_camera_api_tripod.mixin_interface.IExModifierManager;
import cn.anecansaitin.freecameraapi.api.CameraStates;
import cn.anecansaitin.freecameraapi.core.ModifierManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;grabMouse()V"))
    public void freeCameraAPI$setScreen(MouseHandler instance, Operation<Void> original) {
        ModifierManager manager = ModifierManager.INSTANCE;
        IExModifierManager exManager = IExModifierManager.of(manager);

        if(!manager.isStateEnabledAnd(CameraStates.ENABLE)) {
            original.call(instance);
            return;
        }

        switch (exManager.controlScheme()) {
            case ControlScheme.CAMERA_RELATIVE_STRAFE cameraRelativeStrafe-> {}
            case ControlScheme.PLAYER_RELATIVE_STRAFE playerRelativeStrafe-> {}
            default -> original.call(instance);
        }
    }
}
