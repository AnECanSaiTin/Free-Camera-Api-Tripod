package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

/// 可以挂公式的数值字段。
///
/// 关键帧与路径节点都用这里的常量定位"哪个数值"：公式本身存在各自的键 / 节点上，
/// 求值时按字段取出公式、交给 {@link Expression} 算出一个值顶替原来的固定数值。
public enum DynamicField {
    /// 关键帧的取值
    KEY_VALUE,
    /// 关键帧的入切线
    KEY_IN_TANGENT,
    /// 关键帧的出切线
    KEY_OUT_TANGENT,
    /// 关键帧的入权重
    KEY_IN_WEIGHT,
    /// 关键帧的出权重
    KEY_OUT_WEIGHT,
    /// 路径节点坐标的 x 分量
    NODE_X,
    /// 路径节点坐标的 y 分量
    NODE_Y,
    /// 路径节点坐标的 z 分量
    NODE_Z,
    /// 路径节点入切线的 x 分量
    NODE_IN_X,
    /// 路径节点入切线的 y 分量
    NODE_IN_Y,
    /// 路径节点入切线的 z 分量
    NODE_IN_Z,
    /// 路径节点出切线的 x 分量
    NODE_OUT_X,
    /// 路径节点出切线的 y 分量
    NODE_OUT_Y,
    /// 路径节点出切线的 z 分量
    NODE_OUT_Z
}
