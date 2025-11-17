package cn.anecansaitin.free_camera_api_tripod.mixin;

import cn.anecansaitin.free_camera_api_tripod.api.IExCameraModifier;
import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.freecameraapi.core.Modifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Modifier.class)
public abstract class CameraModifierMixin implements IExCameraModifier {
    @Unique
    private ControlScheme scheme;

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
