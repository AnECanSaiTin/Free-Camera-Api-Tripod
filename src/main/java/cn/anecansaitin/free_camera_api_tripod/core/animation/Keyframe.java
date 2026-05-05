package cn.anecansaitin.free_camera_api_tripod.core.animation;

import java.util.Comparator;

public class Keyframe {
    public final float time;
    public float value;
    public float inTangent;
    public float inWeight;
    public float outTangent;
    public float outWeight;
    public WeightedMode weightedMode = WeightedMode.NONE;

    public Keyframe(float time, float value) {
        this.time = time;
        this.value = value;
    }

    public Keyframe(float time, float value, float inTangent, float outTangent) {
        this.time = time;
        this.value = value;
        this.inTangent = inTangent;
        this.outTangent = outTangent;
    }

    public Keyframe(float time, float value, float inTangent, float inWeight, float outTangent, float outWeight, WeightedMode weightedMode) {
        this.time = time;
        this.value = value;
        this.inTangent = inTangent;
        this.inWeight = inWeight;
        this.outTangent = outTangent;
        this.outWeight = outWeight;
        this.weightedMode = weightedMode;
    }

    public Keyframe copy() {
        return new Keyframe(time, value, inTangent, inWeight, outTangent, outWeight, weightedMode);
    }

    public Keyframe copy(float time) {
        return new Keyframe(time, value, inTangent, inWeight, outTangent, outWeight, weightedMode);
    }

    public static final Comparator<Keyframe> TIME_COMPARATOR = (Keyframe k1, Keyframe k2) -> Float.compare(k1.time, k2.time);
}
