package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 相机通道所在的空间（主设计 §6.3）。
///
/// `Space` 决定**参考系解释**，`MotionMode` 决定**位置来自路径还是坐标通道**，
/// 两者正交：四种组合都合法。
///
/// ## 切换空间不换算坐标
///
/// 主设计 §6.4：切换 World / Local **只改变空间解释，不自动换算已有坐标**。
/// 原因是坐标值可能是公式，机械换算会改变求值语义。界面必须提示当前参考系并显示预览结果。
///
/// ## 局部空间可以没有默认锚点
///
/// 局部空间允许 `defaultAnchor` 为空，但**开始播放前必须由请求提供有效锚点**；
/// 没有锚点就求值属于“缺输入”，按结构化失败处理，不能悄悄退回世界空间。
///
/// 该空值也让编辑会话有明确起点：会话按 `RigId` 独立保存预览锚点，初始化可用机位文档默认值，
/// 无默认值时**显式建立当前相机位置的固定预览锚点**（主设计 §6.3），此操作不修改文档。
@NullMarked
public sealed interface Space {

    /// 世界空间：使用单位参考系，复用同一套合成规则。
    ///
    /// 位置直接是 `world = local`；旋转的 `followMask` 无意义（固定为不启用全部轴），
    /// 因为单位参考系不存在“锚点姿态”。
    record World() implements Space {
    }

    /// 局部空间：相对一个锚点。
    ///
    /// @param defaultAnchor 机位文档默认锚点；可空（见类型说明）
    /// @param followMask    锚点旋转沿哪几个轴跟随宿主。主设计 D4：支持平移与逐轴启用的旋转，
    ///                       **不支持缩放**——缩放会让格数含义失真，也超出宿主相机能力
    record Local(@Nullable Anchor defaultAnchor, RotationMask followMask) implements Space {
    }
}
