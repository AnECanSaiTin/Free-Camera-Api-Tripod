package cn.anecansaitin.free_camera_api_tripod;

import cn.anecansaitin.freecameraapi.api.*;

@CameraPlugin(value = "dev", priority = ModifierPriority.LOWEST)
public class DevPlugin implements ICameraPlugin {
    private ICameraModifier modifier;

    @Override
    public void initialize(ICameraModifier modifier) {
        this.modifier = modifier;
    }

    @Override
    public void update() {
//        modifier.enable()
//                .enablePos()
//                .enableGlobalMode()
//                .setPos(509,60,-9999);
//        IExCameraModifier.of(modifier)
//                .enableChunkLoader();

//        modifier.enable()
//                .enablePos()
//                .enableRotation()
//                .setPos(0, 10, 0)
//                .setRotationYXZ(90, 0, 0);
//        IExCameraModifier.of(modifier)
//                .setControlScheme(ControlScheme.VANILLA);

//        modifier.disable();
//        IExCameraModifier.of(modifier)
//                .disableChunkLoader();
    }
}