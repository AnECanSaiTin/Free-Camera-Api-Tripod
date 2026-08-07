package cn.anecansaitin.free_camera_api_tripod.api.animation;

public interface Keyframec {
    // get
    float time();

    float value();

    float inTangent();

    float outTangent();

    float inWeight();

    float outWeight();

    WeightedMode weightedMode();

    EvaluateMode evaluateMode();
}
