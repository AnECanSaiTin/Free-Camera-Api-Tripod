package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;

/// 试算完成状态（主设计 §4.3）。
///
/// 试算是有预算的诊断检查，不是全时间域安全证明：
///
/// - [PASSED] 仅表示已检查点未发现错误；
/// - [FAILED] 表示确定发现求值错误，应拒绝候选；
/// - [INCOMPLETE] 表示预算不足或缺少环境事实，允许提交但必须提示。
@NullMarked
public enum TrialStatus {

    /// 已检查点未发现错误。
    PASSED,

    /// 确定发现求值错误。
    FAILED,

    /// 预算不足 / 缺少事实，未覆盖全部计划范围。
    INCOMPLETE
}
