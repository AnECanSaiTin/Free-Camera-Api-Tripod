package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.api.Keyframe;
import org.jetbrains.annotations.NotNull;

public record ImmutableKeyframe(float time, float value, float inTangent, float inWeight, float outTangent,
                                float outWeight, @NotNull WeightedMode weightedMode) implements Keyframe {
    public ImmutableKeyframe(float time, float value) {
        this(time, value, 0, 0);
    }

    public ImmutableKeyframe(float time, float value, float inTangent, float outTangent) {
        this(time, value, inTangent, 1f / 3f, outTangent, 1f / 3f, WeightedMode.NONE);
    }

    public ImmutableKeyframe(Keyframe keyframe) {
        this(keyframe.time(), keyframe.value(), keyframe.inTangent(), keyframe.inWeight(), keyframe.outTangent(), keyframe.outWeight(), keyframe.weightedMode());
    }
}
