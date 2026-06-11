package cn.anecansaitin.free_camera_api_tripod.mixin.control_scheme;

import cn.anecansaitin.free_camera_api_tripod.api.TripodData;
import cn.anecansaitin.free_camera_api_tripod.core.Data;
import cn.anecansaitin.freecameraapi.api.CameraStates;
import cn.anecansaitin.freecameraapi.core.ModifierManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static cn.anecansaitin.free_camera_api_tripod.api.ControlScheme.*;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;grabMouse()V"))
    public void freeCameraAPI$setScreen(MouseHandler instance, Operation<Void> original) {
        ModifierManager manager = ModifierManager.INSTANCE;

        if(!manager.isStateEnabledAnd(CameraStates.ENABLE.code)) {
            original.call(instance);
            return;
        }

        TripodData data = manager.getData(Data.TYPE);

        switch (data.controlScheme()) {
            case CameraRelativeStrafe _, PlayerRelativeStrafe _ -> {}
            default -> original.call(instance);
        }
    }
}
