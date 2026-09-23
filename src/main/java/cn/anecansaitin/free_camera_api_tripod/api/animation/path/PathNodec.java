package cn.anecansaitin.free_camera_api_tripod.api.animation.path;

import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

/// 路径节点的只读视图：渲染与取点只需要这些信息，改切线、改位置由
/// {@link PathNode} 提供。
@NullMarked
public interface PathNodec {
    Vector3fc position();

    Vector3fc inTangent();

    Vector3fc outTangent();

    PathMode pathMode();

    boolean smooth();
}
