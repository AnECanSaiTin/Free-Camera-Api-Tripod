package cn.anecansaitin.free_camera_api_tripod.api;

import cn.anecansaitin.freecameraapi.api.CameraState;
import cn.anecansaitin.freecameraapi.api.CameraStates;

@CameraState
public class ExCameraStates {
    public static final int CHUNK_LOADER = CameraStates.nextState("chunk_loader");
}