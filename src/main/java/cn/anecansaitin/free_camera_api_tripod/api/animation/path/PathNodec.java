package cn.anecansaitin.free_camera_api_tripod.api.animation.path;

import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

/// 路径节点的只读视图：渲染、取点与界面显示只需要这些信息，改切线、改位置由
/// [PathNode] 提供。
///
/// 路径是**纯几何**：坐标与切线就是固定的向量，不参与表达式求值。
/// 相机随时间变化由曲线通道与路径进度负责，节点形状本身不做动态——弧长、渲染这些
/// 几何度量因此始终有确定的值。
@NullMarked
public interface PathNodec {
    Vector3fc position();

    Vector3fc inTangent();

    Vector3fc outTangent();

    PathMode pathMode();

    boolean smooth();
}
