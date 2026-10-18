package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

/// 多机位合成服务（主设计 §7.6）。
///
/// 宿主实现接收 [PreparedFrame]，以同一目标时钟绘制当前活动机位，再按矩形、层级与稳定顺序
/// 合成。世界 / 实体模拟与文档事件每帧只执行一次；各视角的提取、剔除与提交可能重复。
///
/// 渲染期间不接受重入发布或编辑操作；渲染目标、尺寸与临时状态不暴露到 common API。
/// 固定层序：机位画面 → 成片叠加 → HUD → 编辑器辅助 → 工作区 → 菜单。
@NullMarked
@FunctionalInterface
public interface CameraCompositionService {

    /// 合成一帧。
    ///
    /// @param frame   已准备的活动机位、来源、矩形与姿态
    /// @param request 输出尺寸与玩家底图选项
    CompositionResult compose(PreparedFrame frame, CompositionRequest request);
}
