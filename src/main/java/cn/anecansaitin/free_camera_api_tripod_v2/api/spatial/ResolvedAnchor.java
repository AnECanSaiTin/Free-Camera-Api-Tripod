package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import org.jspecify.annotations.NullMarked;

/// 一帧内解析完成的锚点事实。
///
/// ## 生命周期
///
/// 只在**帧准备阶段**产生一次，之后整帧只读（主设计 §7.2 / 不变量 I5）：
/// 同一帧内全部机位、全部通道读的是同一份解析结果，因此不会出现“两个面板看到两个位置”。
///
/// ## 为什么位置与旋转都非空
///
/// 解析器要么给出值，要么给出结构化失败（`AnchorResolution` 的失败分支），
/// 不使用“位置为 null 表示没解析出来”这种写法：那样失败与未展开就无法区分，
/// 而不变量 I9 要求运行期错误不得伪装成合法数值或“未编写”。
///
/// 固定锚点的旋转是单位旋转（主设计 §6.4），不是“没有旋转”。
///
/// ## 插值
///
/// 实体与玩家锚点按本帧 partial tick 插值位置与旋转，旋转沿最短弧；
/// 固定锚点不插值。瞬移、维度切换或无有效历史时直接用当前宿主状态，不跨维度平滑
/// （主设计 §6.3）。这些规则在解析器里实现，本类型只承载结果。
///
/// `partialTickApplied` 如实记录本次结果是否做过插值，供诊断与“当前事实试算”标注使用。
///
/// @param anchor            产生该结果的锚点
/// @param position          世界坐标，已按 partial tick 插值
/// @param rotation          锚点姿态，弧度，YXZ
/// @param partialTickApplied 是否应用了 partial tick 插值
@NullMarked
public record ResolvedAnchor(Anchor anchor, Vec3 position, Rotation rotation, boolean partialTickApplied) {

    /// 只给位置、姿态取单位旋转的便捷构造：固定坐标锚点的常见情形。
    public static ResolvedAnchor at(Anchor anchor, Vec3 position) {
        return new ResolvedAnchor(anchor, position, Rotation.NONE, false);
    }
}
