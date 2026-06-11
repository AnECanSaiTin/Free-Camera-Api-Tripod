package cn.anecansaitin.free_camera_api_tripod.api;

public enum TripodStates {
    CHUNK_LOADER(1);

    public final int code;

    TripodStates(int code) {
        this.code = code;
    }
}