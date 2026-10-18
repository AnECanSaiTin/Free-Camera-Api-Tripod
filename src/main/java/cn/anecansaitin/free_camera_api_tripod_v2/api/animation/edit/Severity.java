package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;

/// 诊断严重级别（主设计 §4.1）。
///
/// [ERROR] 会阻止发布；[WARNING] 与 [INFO] 不阻止发布，但必须对用户可见。
@NullMarked
public enum Severity {

    /// 错误：提交必须整体拒绝（或运行期终止 / 标记不可用）。
    ERROR,

    /// 警告：不阻止发布，但存在风险或降级。
    WARNING,

    /// 提示：无风险说明。
    INFO
}
