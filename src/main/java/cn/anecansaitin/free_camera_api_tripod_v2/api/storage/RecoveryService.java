package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.function.Consumer;

/// 自动恢复服务（主设计 §8.4）。
///
/// 恢复按防抖 / 周期捕获已发布快照，异步写入独立恢复槽；写入使用临时文件与原子替换。
/// 完成回调不得把自动恢复版本标记为用户正式保存。错误可诊断且不阻断编辑；自动恢复不覆盖
/// 用户正式文件，也不保存渲染缓存或撤销栈。
@NullMarked
public interface RecoveryService {

    /// 当前恢复槽；顺序稳定，按捕获时间从新到旧。
    List<RecoverySlot> slots();

    /// 捕获一个已发布快照到恢复槽；完成回调恰好一次，不得在编辑线程阻塞。
    void capture(AnimationSnapshot snapshot, ResourceId resource, Consumer<SaveResult> completion);

    /// 恢复槽内容；返回文档仍需完整校验后才能发布。
    LoadResult restore(RecoverySlot slot);

    /// 丢弃恢复槽；不影响用户正式文件。
    void discard(RecoverySlot slot);

    /// 记录用户决定；实现不得把 `KEEP` 静默当作恢复或删除。
    void decide(RecoverySlot slot, RecoveryDecision decision);
}
