package cn.anecansaitin.free_camera_api_tripod.api;

import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;

public interface IExCameraModifier {
    default IExCameraModifier enableChunkLoader() {
        return this;
    }

    default IExCameraModifier disableChunkLoader() {
        return this;
    }

    default IExCameraModifier setControlScheme(ControlScheme scheme) {
        return this;
    }

    default ControlScheme getControlScheme() {
        return ControlScheme.VANILLA;
    }
}