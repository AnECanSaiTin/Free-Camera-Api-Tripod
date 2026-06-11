package cn.anecansaitin.free_camera_api_tripod.mixin.control_scheme;

import cn.anecansaitin.free_camera_api_tripod.api.TripodData;
import cn.anecansaitin.free_camera_api_tripod.core.Data;
import cn.anecansaitin.free_camera_api_tripod.core.control_scheme.ControlSchemeManager;
import cn.anecansaitin.freecameraapi.ClientUtil;
import cn.anecansaitin.freecameraapi.api.CameraStates;
import cn.anecansaitin.freecameraapi.core.ModifierManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static cn.anecansaitin.free_camera_api_tripod.api.ControlScheme.*;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), cancellable = true)
    public void freeCameraAPI$turnPlayer(double mousea, CallbackInfo ci) {
        ModifierManager manager = ModifierManager.INSTANCE;
        TripodData data = manager.getData(Data.TYPE);

        if (!manager.isStateEnabledOr(CameraStates.ENABLE.code) || ClientUtil.player().isPassenger()) {
            return;
        }

        switch (data.controlScheme()) {
            case CameraRelative _, CameraRelativeStrafe _, PlayerRelative _, PlayerRelativeStrafe _ -> ci.cancel();
            default -> {}
        }
    }

    @Inject(method = "onMove", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;accumulatedDX:D", opcode = Opcodes.GETFIELD))
    public void freeCameraAPI$onMove(long handle, double xpos, double ypos, CallbackInfo ci) {
        ControlSchemeManager.mouseMove();
    }

    @WrapOperation(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;grabMouse()V"))
    public void freeCameraAPI$onButton(MouseHandler instance, Operation<Void> original) {
        ModifierManager manager = ModifierManager.INSTANCE;
        TripodData data = manager.getData(Data.TYPE);

        if (!manager.isStateEnabledOr(CameraStates.ENABLE.code)) {
            original.call(instance);
            return;
        }

        switch (data.controlScheme()) {
            case CameraRelativeStrafe _, PlayerRelative _, PlayerRelativeStrafe _ -> {}
            default -> original.call(instance);
        }
    }
}
