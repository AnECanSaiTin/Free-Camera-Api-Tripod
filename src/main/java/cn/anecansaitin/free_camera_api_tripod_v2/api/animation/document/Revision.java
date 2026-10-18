package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import org.jspecify.annotations.NullMarked;

/// 发布序号：某一次文档发布的单调递增身份（主设计 §3.3）。
///
/// 存储实例用 `volatile AnimationSnapshot` 保存当前状态，快照里的本类型与文档
/// **一起读取**，不允许分别读取文档与计数器再拼成快照（不变量 I3）。
///
/// 撤销恢复旧文档时也生成**新的**发布序号：旧文档内容可以回来，但“这次发布”是新的。
/// 版本号只在**同一个**文档存储实例内单调；不同存储实例的相同数值不代表同一文档
/// （主设计 §3.4）。
///
/// @param value 发布序号，非负；比较只用于同一存储实例内的先后判断
@NullMarked
public record Revision(long value) implements Comparable<Revision> {

    /// 初始发布序号。
    public static final Revision INITIAL = new Revision(0);

    public Revision {
        if (value < 0) {
            throw new IllegalArgumentException("Revision must be non-negative: " + value);
        }
    }

    /// 下一次发布序号。
    public Revision next() {
        return new Revision(value + 1);
    }

    /// 是否严格晚于 `other`。
    public boolean isAfter(Revision other) {
        return value > other.value;
    }

    @Override
    public int compareTo(Revision other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
