package cn.anecansaitin.free_camera_api_tripod.mixin;

import cn.anecansaitin.free_camera_api_tripod.api.IExCameraModifier;
import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.free_camera_api_tripod.mixin_interface.IExModifierManager;
import cn.anecansaitin.freecameraapi.api.ICameraModifier;
import cn.anecansaitin.freecameraapi.core.ModifierManager;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModifierManager.class)
public abstract class ModifierManagerMixin implements IExModifierManager {
    @Unique
    private ControlScheme controlScheme = ControlScheme.VANILLA;// 控制模式

    @Inject(method = "applyToCamera", at = @At(value = "INVOKE", target = "Lcn/anecansaitin/freecameraapi/core/ModifierManager;setCamera()V"))
    private void applyExtension(CallbackInfo ci, @Local ICameraModifier modifier) {
        applyControlScheme(modifier);
    }

    @Inject(method = "setToVanilla", at = @At("TAIL"))
    private void resetExtension(CallbackInfo ci) {
        controlScheme = ControlScheme.VANILLA;
    }

    @Unique
    private void applyControlScheme(ICameraModifier modifier) {
        controlScheme = IExCameraModifier.of(modifier).getControlScheme();
    }

    @Override
    public ControlScheme controlScheme() {
        return controlScheme;
    }
}
