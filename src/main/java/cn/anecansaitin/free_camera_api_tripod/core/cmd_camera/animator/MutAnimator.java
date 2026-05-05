package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator;


import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.interpolator.FloatLerp;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.interpolator.Vec3fLerp;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe.SimpleKeyframe;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe_track.MapKeyframeTrack;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.value_getter.SimpleValueGetter;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.value_getter.ValueGetter;
import org.joml.Vector3f;

public class MutAnimator {
    private final MapKeyframeTrack<Vector3f> pos = new MapKeyframeTrack<>();
    private final MapKeyframeTrack<Vector3f> rot = new MapKeyframeTrack<>();
    private final MapKeyframeTrack<Float> fov = new MapKeyframeTrack<>();
    private AnimeMode mode = new AnimeMode.Global();

    public void putPos(int selfTick, Vector3f pos) {
        this.pos.putKeyframe(selfTick, vec3fKeyframe(pos));
    }

    public void putRot(int selfTick, Vector3f rot) {
        this.rot.putKeyframe(selfTick, vec3fKeyframe(rot));
    }

    public void putFov(int selfTick, float fov) {
        this.fov.putKeyframe(selfTick, floatKeyframe(fov));
    }

    private Keyframe<ValueGetter<Vector3f>, Vector3f> vec3fKeyframe(Vector3f vec3f) {
        return new SimpleKeyframe<>(new SimpleValueGetter<>(vec3f), new Vec3fLerp());
    }

    private Keyframe<ValueGetter<Float>, Float> floatKeyframe(float f) {
        return new SimpleKeyframe<>(new SimpleValueGetter<>(f), new FloatLerp());
    }

    public AnimeMode mode() {
        return mode;
    }

    public void mode(AnimeMode mode) {
        this.mode = mode;
    }
}
