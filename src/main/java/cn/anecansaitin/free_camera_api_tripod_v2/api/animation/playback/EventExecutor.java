package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 单一轨道类型的文档事件执行器（主设计 §7.4）。
///
/// 注册表按轨道 `type()` 分派；执行器只接收原发生序中**连续的同类型片段或单事件**，可被多次
/// 调用，不得跨类型或跨周期聚合重排。必须使用帧内捕获快照解析载荷，不得读取当前 store；
/// 执行器异常终止后续执行并报告已发生的部分执行。
@NullMarked
@FunctionalInterface
public interface EventExecutor {

    /// 执行同类型事件。
    ///
    /// @param frame  已准备帧（快照、事实与时钟）
    /// @param events 原发生序中连续的同类型片段或单事件，顺序已冻结
    EventDispatcher.Result execute(PreparedFrame frame, List<PreparedFrame.Event> events);
}
