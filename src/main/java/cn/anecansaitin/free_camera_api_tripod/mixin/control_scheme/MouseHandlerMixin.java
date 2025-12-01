package cn.anecansaitin.free_camera_api_tripod.mixin.control_scheme;

import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.free_camera_api_tripod.core.control_scheme.ControlSchemeManager;
import cn.anecansaitin.free_camera_api_tripod.mixin_interface.IExModifierManager;
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

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), cancellable = true)
    public void freeCameraAPI$turnPlayer(double movementTime, CallbackInfo ci) {
        ModifierManager manager = ModifierManager.INSTANCE;
        IExModifierManager exManager = IExModifierManager.of(manager);

        if (!manager.isStateEnabledOr(CameraStates.ENABLE) || ClientUtil.player().isPassenger()) {
            return;
        }

        switch (exManager.controlScheme()) {
            case ControlScheme.CAMERA_RELATIVE cameraRelative -> ci.cancel();
            case ControlScheme.CAMERA_RELATIVE_STRAFE cameraRelativeStrafe -> ci.cancel();
            case ControlScheme.PLAYER_RELATIVE playerRelative -> ci.cancel();
            case ControlScheme.PLAYER_RELATIVE_STRAFE playerRelativeStrafe -> ci.cancel();
            default -> {}
        }
    }

    @Inject(method = "onMove", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;accumulatedDX:D", opcode = Opcodes.GETFIELD))
    public void freeCameraAPI$onMove(long windowPointer, double xpos, double ypos, CallbackInfo ci) {
        ControlSchemeManager.mouseMove();
    }

    @WrapOperation(method = "onPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;grabMouse()V"))
    public void freeCameraAPI$onPress(MouseHandler instance, Operation<Void> original) {
        ModifierManager manager = ModifierManager.INSTANCE;
        IExModifierManager exManager = IExModifierManager.of(manager);

        if (!manager.isStateEnabledOr(CameraStates.ENABLE)) {
            original.call(instance);
            return;
        }

        switch (exManager.controlScheme()) {
            case ControlScheme.CAMERA_RELATIVE_STRAFE cameraRelativeStrafe -> {}
            case ControlScheme.PLAYER_RELATIVE playerRelative -> {}
            case ControlScheme.PLAYER_RELATIVE_STRAFE playerRelativeStrafe -> {}
            default -> original.call(instance);
        }
    }
}
