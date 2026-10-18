package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import org.jspecify.annotations.NullMarked;

/// 恢复槽的用户决定（主设计 §8.4）。
///
/// 决定本身不执行 IO；调用方按决定选择 `restore(...)` / `discard(...)`。`KEEP` 保留槽位供
/// 后续处理，不能被实现静默解释为恢复或删除。
@NullMarked
public enum RecoveryDecision {

    /// 恢复到编辑器 / 新候选；仍须走文档校验再发布。
    RESTORE,

    /// 丢弃该恢复槽。
    DISCARD,

    /// 暂不处理，保留槽位。
    KEEP
}
