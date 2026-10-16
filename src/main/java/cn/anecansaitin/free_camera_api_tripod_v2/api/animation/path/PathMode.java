package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path;

import org.jspecify.annotations.NullMarked;

/// 路径节点之间线段的插值方式（主设计 §3.3：线性 / 贝塞尔 / Catmull-Rom）。
///
/// 与旧实现的取值一致，两点差别在于：
///
/// - 节点身份从**下标**改成稳定 ID（`PathNodeId`）：插值方式属于节点，而节点由 ID 定位，
///   插入或删除不会让选中漂移。
/// - 切线与插值模式的关系被显式化：只有 [BEZIER] 使用两侧切线，
///   另外两种模式在 `PathNode` 构造时把切线归一为零。
@NullMarked
public enum PathMode {
    /// 直线段：忽略两侧切线。
    LINEAR,

    /// 三次贝塞尔：使用两侧切线向量作为控制点。
    BEZIER,

    /// Catmull-Rom：切线由相邻节点自动生成，节点上的切线字段不参与。
    CATMULL_ROM
}