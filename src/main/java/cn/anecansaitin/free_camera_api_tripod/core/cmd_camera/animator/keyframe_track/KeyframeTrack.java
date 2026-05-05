package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe_track;

public interface KeyframeTrack<T> {
    T getValue(int timeTick, float deltaTick);
}
