package cn.anecansaitin.free_camera_api_tripod.mixin_interface;

import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.freecameraapi.core.ModifierManager;

public interface IExModifierManager {
    default ControlScheme controlScheme() {
        return ControlScheme.VANILLA;
    }

    static IExModifierManager of(ModifierManager manager) {
        return (IExModifierManager) manager;
    }
}
