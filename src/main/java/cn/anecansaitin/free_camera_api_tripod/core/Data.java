package cn.anecansaitin.free_camera_api_tripod.core;

import cn.anecansaitin.free_camera_api_tripod.api.ControlScheme;
import cn.anecansaitin.free_camera_api_tripod.api.TripodData;
import cn.anecansaitin.free_camera_api_tripod.api.TripodStates;
import cn.anecansaitin.freecameraapi.api.CameraDataType;

public class Data implements TripodData {
    public static final CameraDataType<TripodData> TYPE = new CameraDataType<>(TripodData.class, Data::new);
    private int state;
    private ControlScheme controlScheme = ControlScheme.VANILLA;

    @Override
    public Data enableChunkLoader() {
        state |= TripodStates.CHUNK_LOADER.code;
        return this;
    }

    @Override
    public Data disableChunkLoader() {
        state &= ~TripodStates.CHUNK_LOADER.code;
        return this;
    }

    @Override
    public Data controlScheme(ControlScheme scheme) {
        this.controlScheme = scheme;
        return this;
    }

    @Override
    public ControlScheme controlScheme() {
        return controlScheme;
    }

    @Override
    public int state() {
        return state;
    }

    @Override
    public Data state(int state) {
        this.state = state;
        return this;
    }

    @Override
    public void update() {

    }
}
