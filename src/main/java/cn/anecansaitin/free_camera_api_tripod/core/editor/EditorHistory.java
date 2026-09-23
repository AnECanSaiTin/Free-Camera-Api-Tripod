package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationCodec;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/// 编辑器的撤销 / 重做快照栈。
///
/// 快照直接复用 {@link AnimationCodec#animationToJson(CameraAnimation)} 的序列化结果，
/// 因此不必为每种数据单独定义差异结构：两次内容相同的字符串即视为没有变化。
/// 恢复时用 {@link CameraAnimation#copyFrom(CameraAnimation)} 原地替换内容，
/// 因为动画实例被播放器与编辑器各处持有，不能换成另一个对象。
@NullMarked
public final class EditorHistory {
    /// 快照栈长度上限，超出后丢弃最旧的快照
    private static final int MAX_SNAPSHOTS = 64;
    /// 两次压栈之间的最短间隔（毫秒）：拖拽时每帧都会调用 capture，靠它避免逐帧压栈
    private static final long CAPTURE_INTERVAL_MILLIS = 250;

    private final List<String> snapshots = new ArrayList<>();
    /// 当前所处的快照下标；栈为空时为 -1
    private int cursor = -1;
    /// 上次压栈的时间戳
    private long lastCaptureMillis;
    /// 正在用历史快照恢复动画；置位期间 capture 直接跳过，避免把恢复动作本身又压成新快照
    private boolean restoring;

    /// 记录当前动画内容。
    ///
    /// 与当前快照内容相同、或距上次压栈不足 {@link #CAPTURE_INTERVAL_MILLIS} 毫秒时什么都不做；
    /// 否则压入新快照，并丢弃从当前游标往后的重做分支。
    public void capture(CameraAnimation animation) {
        if (restoring) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastCaptureMillis < CAPTURE_INTERVAL_MILLIS) {
            return;
        }

        String json = AnimationCodec.animationToJson(animation);

        if (cursor >= 0 && json.equals(snapshots.get(cursor))) {
            return;
        }

        // 出现新改动后，原来的重做分支不再可达，先丢掉
        while (snapshots.size() > cursor + 1) {
            snapshots.remove(snapshots.size() - 1);
        }

        snapshots.add(json);
        cursor = snapshots.size() - 1;

        // 超出上限时丢掉最旧的快照，游标一同前移
        if (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.remove(0);
            cursor--;
        }

        lastCaptureMillis = now;
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor >= 0 && cursor + 1 < snapshots.size();
    }

    /// 回退一格并原地恢复动画内容；没有可撤销的快照时返回 false
    public boolean undo(CameraAnimation animation) {
        return canUndo() && restore(animation, cursor - 1);
    }

    /// 前进一格并原地恢复动画内容；没有可重做的快照时返回 false
    public boolean redo(CameraAnimation animation) {
        return canRedo() && restore(animation, cursor + 1);
    }

    /// 把动画内容原地替换为目标快照，并把游标移到该快照；快照解析失败时保持原状态
    private boolean restore(CameraAnimation animation, int target) {
        CameraAnimation loaded = AnimationCodec.animationFromJson(snapshots.get(target));

        if (loaded == null) {
            return false;
        }

        restoring = true;

        try {
            animation.copyFrom(loaded);
            cursor = target;
        } finally {
            restoring = false;
        }

        // 恢复后的内容与目标快照一致，这里让压栈节流重新计时，避免紧接着的 capture 立刻又压一帧
        lastCaptureMillis = System.currentTimeMillis();
        return true;
    }
}
