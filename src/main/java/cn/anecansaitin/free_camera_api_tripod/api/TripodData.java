package cn.anecansaitin.free_camera_api_tripod.api;

import cn.anecansaitin.free_camera_api_tripod.core.Data;

public interface TripodData {
    Data enableChunkLoader();

    Data disableChunkLoader();

    Data setControlScheme(ControlScheme scheme);

    ControlScheme getControlScheme();

    int getState();

    Data setState(int state);
}
