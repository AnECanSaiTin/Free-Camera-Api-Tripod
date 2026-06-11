package cn.anecansaitin.free_camera_api_tripod.api;

import cn.anecansaitin.free_camera_api_tripod.core.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.core.animation.WeightedMode;

import java.util.Comparator;

public interface Keyframe {
    float time();
    float value();
    float inTangent();
    float outTangent();
    float inWeight();
    float outWeight();
    WeightedMode weightedMode();
    EvaluateMode evaluateMode();
    Comparator<Keyframe> TIME_COMPARATOR = (Keyframe k1, Keyframe k2) -> Float.compare(k1.time(), k2.time());
}
