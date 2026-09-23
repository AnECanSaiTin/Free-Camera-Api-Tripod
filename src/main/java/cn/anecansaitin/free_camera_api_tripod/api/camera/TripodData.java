package cn.anecansaitin.free_camera_api_tripod.api.camera;

import cn.anecansaitin.freecameraapi.api.CameraData;

/// 三脚架相机的数据接口：链式设置区块加载与操作方案。
public interface TripodData extends CameraData {
    TripodData enableChunkLoader();

    TripodData disableChunkLoader();

    TripodData controlScheme(ControlScheme scheme);

    ControlScheme controlScheme();

    int state();

    TripodData state(int state);
}
