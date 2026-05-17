package cn.anecansaitin.free_camera_api_tripod.core;

import cn.anecansaitin.free_camera_api_tripod.api.ControlScheme;
import cn.anecansaitin.free_camera_api_tripod.api.TripodData;
import cn.anecansaitin.free_camera_api_tripod.api.TripodStates;
import cn.anecansaitin.freecameraapi.api.CameraDataType;

public class Data implements TripodData {
    public static final CameraDataType<TripodData> TYPE = new CameraDataType<>(TripodData.class, Data::new);
    private int state;
    private ControlScheme controlScheme;

    @Override
    public Data enableChunkLoader() {
        state |= TripodStates.CHUNK_LOADER;
        return this;
    }

    @Override
    public Data disableChunkLoader() {
        state &= ~TripodStates.CHUNK_LOADER;
        return this;
    }

    @Override
    public Data setControlScheme(ControlScheme scheme) {
        this.controlScheme = scheme;
        return this;
    }

    @Override
    public ControlScheme getControlScheme() {
        return controlScheme;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public Data setState(int state) {
        this.state = state;
        return this;
    }
}
