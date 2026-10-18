package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 文档事件执行器：宿主按轨道类型注册实现（主设计 §7.4）。
///
/// 事件准备是纯操作；执行阶段不承诺对宿主副作用事务回滚。执行器异常必须终止后续分发并
/// 报告已经发生的部分执行；定位、暂停、拖动与试算不得调用本接口。
///
/// 同时刻跨轨道事件按文档轨道顺序执行；从零开始及每个新循环单独包含零时刻事件一次；
/// 正向推进区间为 `(from, to]`。
@NullMarked
@FunctionalInterface
public interface EventDispatcher {

    /// 执行本帧事件。
    ///
    /// @param events 按文档顺序排列的事件
    /// @param budget 事件与区间预算
    /// @return 执行结果；预算超限或执行器异常时携带失败
    Result dispatch(List<PreparedFrame.Event> events, EventBudget budget);

    /// 执行结果。
    ///
    /// @param dispatched 已成功分发的事件数，非负；失败时表示已发生的部分执行
    /// @param failure    失败；完全成功时为空
    record Result(int dispatched, @Nullable PlaybackFailure failure) {

        public Result {
            if (dispatched < 0) {
                throw new IllegalArgumentException("EventDispatcher.Result dispatched must be non-negative");
            }
        }

        /// 是否完全成功。
        public boolean succeeded() {
            return failure == null;
        }
    }
}
