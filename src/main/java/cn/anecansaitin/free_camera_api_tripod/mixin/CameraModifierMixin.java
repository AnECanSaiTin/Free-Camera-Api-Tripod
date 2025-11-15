package cn.anecansaitin.free_camera_api_tripod.mixin;

import cn.anecansaitin.free_camera_api_tripod.api.ExModifierStates;
import cn.anecansaitin.free_camera_api_tripod.api.IExCameraModifier;
import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.freecameraapi.api.ICameraModifier;
import cn.anecansaitin.freecameraapi.core.Modifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(Modifier.class)
public abstract class CameraModifierMixin implements IExCameraModifier {
    @Unique
    private int exState = 0;
    private ControlScheme scheme;

    @Override
    public IExCameraModifier enableChunkLoader() {
        exState |= ExModifierStates.CHUNK_LOADER;
        return this;
    }

    @Override
    public IExCameraModifier disableChunkLoader() {
        exState &= ~ExModifierStates.CHUNK_LOADER;
        return this;
    }

    @Override
    public IExCameraModifier setControlScheme(ControlScheme scheme) {
        this.scheme = scheme;
        return this;
    }

    @Override
    public ControlScheme getControlScheme() {
        return scheme;
    }

    @Inject(method = "disableAll", at = @At("RETURN"))
    public void free_camera_api_tripod$disableAll() {
        exState = 0;
    }
}
