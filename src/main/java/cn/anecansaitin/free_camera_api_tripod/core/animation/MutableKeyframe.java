package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.api.Keyframe;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class MutableKeyframe implements Keyframe {
    private float time;
    private float value;
    private float inTangent;
    private float inWeight;
    private float outTangent;
    private float outWeight;
    private WeightedMode weightedMode;
    private EvaluateMode evaluateMode;

    public MutableKeyframe(float time, float value) {
        this(time, value, 0, 0);
    }

    public MutableKeyframe(float time, float value, float inTangent, float outTangent) {
        this(time, value, inTangent, 1f / 3f, outTangent, 1f / 3f, WeightedMode.NONE, EvaluateMode.LINEAR);
    }

    public MutableKeyframe(float time, float value, float inTangent, float inWeight, float outTangent, float outWeight, WeightedMode weightedMode, EvaluateMode evaluateMode) {
        this.time = time;
        this.value = value;
        this.inTangent = inTangent;
        this.inWeight = inWeight;
        this.outTangent = outTangent;
        this.outWeight = outWeight;
        this.weightedMode = weightedMode;
        this.evaluateMode = evaluateMode;
    }

    public MutableKeyframe(Keyframe keyframe) {
        this(keyframe.time(), keyframe.value(), keyframe.inTangent(), keyframe.inWeight(), keyframe.outTangent(), keyframe.outWeight(), keyframe.weightedMode(), keyframe.evaluateMode());
    }

    @Override
    public float time() {
        return time;
    }

    public MutableKeyframe time(float time) {
        this.time = time;
        return this;
    }

    @Override
    public float value() {
        return value;
    }

    public MutableKeyframe value(float value) {
        this.value = value;
        return this;
    }

    @Override
    public float inTangent() {
        return inTangent;
    }

    public MutableKeyframe inTangent(float inTangent) {
        this.inTangent = inTangent;
        return this;
    }

    @Override
    public float outTangent() {
        return outTangent;
    }

    public MutableKeyframe outTangent(float outTangent) {
        this.outTangent = outTangent;
        return this;
    }

    @Override
    public float inWeight() {
        return inWeight;
    }

    public MutableKeyframe inWeight(float inWeight) {
        this.inWeight = inWeight;
        return this;
    }

    @Override
    public float outWeight() {
        return outWeight;
    }

    public MutableKeyframe outWeight(float outWeight) {
        this.outWeight = outWeight;
        return this;
    }

    @Override
    public WeightedMode weightedMode() {
        return weightedMode;
    }

    public MutableKeyframe weightedMode(WeightedMode weightedMode) {
        this.weightedMode = weightedMode;
        return this;
    }

    @Override
    public EvaluateMode evaluateMode() {
        return evaluateMode;
    }

    public MutableKeyframe evaluateMode(EvaluateMode evaluateMode) {
        this.evaluateMode = evaluateMode;
        return this;
    }

    public MutableKeyframe set(Keyframe keyframe) {
        this.time = keyframe.time();
        this.value = keyframe.value();
        this.inTangent = keyframe.inTangent();
        this.inWeight = keyframe.inWeight();
        this.outTangent = keyframe.outTangent();
        this.outWeight = keyframe.outWeight();
        this.weightedMode = keyframe.weightedMode();
        this.evaluateMode = keyframe.evaluateMode();
        return this;
    }
}
