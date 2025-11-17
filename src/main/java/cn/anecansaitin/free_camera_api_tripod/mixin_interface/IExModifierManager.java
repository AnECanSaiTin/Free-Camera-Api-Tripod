package cn.anecansaitin.free_camera_api_tripod.mixin_interface;

import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.freecameraapi.api.ICameraModifier;

public interface IExModifierManager {
    default ControlScheme controlScheme() {
        return ControlScheme.VANILLA;
    }
}
