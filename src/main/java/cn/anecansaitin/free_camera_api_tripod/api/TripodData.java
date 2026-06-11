package cn.anecansaitin.free_camera_api_tripod.api;

import cn.anecansaitin.free_camera_api_tripod.core.Data;
import cn.anecansaitin.freecameraapi.api.CameraData;

public interface TripodData extends CameraData {
    Data enableChunkLoader();

    Data disableChunkLoader();

    Data controlScheme(ControlScheme scheme);

    ControlScheme controlScheme();

    int state();

    Data state(int state);
}
