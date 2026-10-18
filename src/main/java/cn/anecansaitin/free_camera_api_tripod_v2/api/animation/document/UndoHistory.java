package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 撤销 / 重做历史的只读视图（主设计 §4.5）。
///
/// 历史保存**不可变文档引用与不可变动作标签**，不序列化为 JSON；撤销恢复旧文档并生成新的
/// 发布序号，不重复执行全时间轴试算。默认上限为 64 项与估算 32 MiB 双限，超出时移除最旧项。
///
/// 会话状态（播放头、预览锚点、循环区间、视图参数）不进入本历史。
@NullMarked
public interface UndoHistory {

    /// 是否可撤销。
    boolean canUndo();

    /// 是否可重做。
    boolean canRedo();

    /// 历史项，按“最新在前”的顺序；只读。
    List<Entry> entries();

    /// 一个历史项：不可变快照 + 不可变动作标签。
    ///
    /// @param snapshot 该项对应的发布快照
    /// @param label    不可变、可本地化的动作标签
    record Entry(AnimationSnapshot snapshot, LocalizedMessage label) {

        public Entry {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(label, "label");
        }
    }
}
