package cn.anecansaitin.free_camera_api_tripod.api;

import cn.anecansaitin.free_camera_api_tripod.core.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.core.animation.MultiKeyframe;
import cn.anecansaitin.free_camera_api_tripod.core.animation.WeightedMode;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;

@NullMarked
@SuppressWarnings("unused")
public interface Keyframe {
    // get
    float time();
    float value();
    float inTangent();
    float outTangent();
    float inWeight();
    float outWeight();
    WeightedMode weightedMode();
    EvaluateMode evaluateMode();
    // set
    Keyframe time(float time);
    Keyframe value(float value);
    Keyframe inTangent(float inTangent);
    Keyframe outTangent(float outTangent);
    Keyframe inWeight(float inWeight);
    Keyframe outWeight(float outWeight);
    Keyframe weightedMode(WeightedMode weightedMode);
    Keyframe evaluateMode(EvaluateMode evaluateMode);

    Comparator<Keyframe> TIME_COMPARATOR = (Keyframe k1, Keyframe k2) -> Float.compare(k1.time(), k2.time());

    static Keyframe create(float time, float value) {
        return new MultiKeyframe(time, value);
    }

    static Keyframe linear(float time, float value) {
        return create(time, value).evaluateMode(EvaluateMode.LINEAR);
    }

    static Keyframe step(float time, float value) {
        return create(time, value).evaluateMode(EvaluateMode.STEP);
    }

    static Keyframe hermite(float time, float value, float inTangent, float outTangent) {
        return create(time, value).inTangent(inTangent).outTangent(outTangent).evaluateMode(EvaluateMode.HERMITE);
    }

    static Keyframe hermite(float time, float value, float inTangent, float inWeight, float outTangent, float outWeight, WeightedMode weightedMode) {
        return create(time, value).inTangent(inTangent).inWeight(inWeight).outTangent(outTangent).outWeight(outWeight).weightedMode(weightedMode).evaluateMode(EvaluateMode.HERMITE);
    }
}
