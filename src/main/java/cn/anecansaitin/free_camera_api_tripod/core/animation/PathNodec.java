package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.PathMode;
import org.joml.Vector3fc;

public interface PathNodec {
    Vector3fc position();

    Vector3fc inTangent();

    Vector3fc outTangent();

    PathMode pathMode();

    boolean smooth();
}
