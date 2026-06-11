package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.api.Keyframe;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ImmutableKeyframe(float time, float value, float inTangent, float inWeight, float outTangent,
                                float outWeight, WeightedMode weightedMode, EvaluateMode evaluateMode) implements Keyframe {
    public ImmutableKeyframe(float time, float value) {
        this(time, value, 0, 0);
    }

    public ImmutableKeyframe(float time, float value, float inTangent, float outTangent) {
        this(time, value, inTangent, 1f / 3f, outTangent, 1f / 3f, WeightedMode.NONE, EvaluateMode.LINEAR);
    }

    public ImmutableKeyframe(Keyframe keyframe) {
        this(keyframe.time(), keyframe.value(), keyframe.inTangent(), keyframe.inWeight(), keyframe.outTangent(), keyframe.outWeight(), keyframe.weightedMode(), keyframe.evaluateMode());
    }
}
