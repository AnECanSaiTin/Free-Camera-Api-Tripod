package cn.anecansaitin.free_camera_api_tripod.mixin;

import cn.anecansaitin.free_camera_api_tripod.api.ExCameraStates;
import cn.anecansaitin.free_camera_api_tripod.api.IExCameraModifier;
import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.freecameraapi.core.Modifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Modifier.class)
public abstract class CameraModifierMixin implements IExCameraModifier {
    @Unique
    private ControlScheme scheme;
    @Shadow
    private int state;

    @Override
    public IExCameraModifier enableChunkLoader() {
        state |= ExCameraStates.CHUNK_LOADER;
        return this;
    }

    @Override
    public IExCameraModifier disableChunkLoader() {
        state &= ~ExCameraStates.CHUNK_LOADER;
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
}
