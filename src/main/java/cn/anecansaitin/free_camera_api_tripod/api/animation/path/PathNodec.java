package cn.anecansaitin.free_camera_api_tripod.api.animation.path;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.DynamicField;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/// 路径节点的只读视图：渲染与取点只需要这些信息，改切线、改位置由
/// {@link PathNode} 提供。
@NullMarked
public interface PathNodec {
    Vector3fc position();

    Vector3fc inTangent();

    Vector3fc outTangent();

    PathMode pathMode();

    boolean smooth();

    /// 挂了公式的坐标 / 切线分量；默认没有。
    /// 只读视图不一定要暴露动态字段，所以给一个空表作为默认实现
    default Map<DynamicField, String> expressions() {
        return Map.of();
    }
}
