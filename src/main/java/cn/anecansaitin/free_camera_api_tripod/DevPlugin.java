package cn.anecansaitin.free_camera_api_tripod;

import cn.anecansaitin.free_camera_api_tripod.api.IExCameraModifier;
import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.freecameraapi.api.*;

@CameraPlugin(value = "dev", priority = ModifierPriority.LOWEST)
public class DevPlugin implements ICameraPlugin {
    private ICameraModifier modifier;

    @Override
    public void initialize(ICameraModifier modifier) {
        this.modifier = modifier;
        modifier.disable()
                .enablePos()
                .enableRotation();
        IExCameraModifier.of(modifier)
                .setControlScheme(ControlScheme.VANILLA)
                .enableChunkLoader();
    }

    @Override
    public void update() {
//        modifier
//                .disable()
//                .enable()
//                .setPos(4,5,-4)
//                .enableGlobalMode()
//                .setRotationYXZ(90,0,0)
//                .setPos(100, 90, -10)
//                .setRotationYXZ(45, 45, 0);
 ;
    }
}