package cn.anecansaitin.free_camera_api_tripod.mixin.control_scheme;

import cn.anecansaitin.freecameraapi.core.ModifierManager;
import cn.anecansaitin.freecameraapi.core.ModifierStates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static cn.anecansaitin.freecameraapi.api.extension.ControlScheme.CAMERA_RELATIVE_STRAFE;
import static cn.anecansaitin.freecameraapi.api.extension.ControlScheme.PLAYER_RELATIVE_STRAFE;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;grabMouse()V"))
    public void freeCameraAPI$setScreen(MouseHandler instance, Operation<Void> original) {
        ModifierManager manager = ModifierManager.INSTANCE;

        if(!manager.isStateEnabledAnd(ModifierStates.ENABLE)) {
            original.call(instance);
            return;
        }

        switch (manager.controlScheme()) {
            case CAMERA_RELATIVE_STRAFE cameraRelativeStrafe-> {}
            case PLAYER_RELATIVE_STRAFE playerRelativeStrafe-> {}
            default -> original.call(instance);
        }
    }
}
