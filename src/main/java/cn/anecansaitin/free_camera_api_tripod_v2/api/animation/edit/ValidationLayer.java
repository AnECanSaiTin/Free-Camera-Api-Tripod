package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;

/// 静态校验层级（主设计 §4.2）。
///
/// 校验顺序固定为 L1 → L2 → L3 → L4；前一层失败时不继续依赖其结果的校验。
/// 校验器不得修改候选，不得隐式夹取、覆盖或量化输入。
@NullMarked
public enum ValidationLayer {

    /// L1：格式与语法（Codec、公式语法、有限常量）。
    FORMAT(1),

    /// L2：结构与引用（稳定 ID、名称冲突、轨道 / 机位 / 样本引用、键时间顺序）。
    STRUCTURE(2),

    /// L3：跨对象语义（依赖环、函数参数个数、样本名绑定、来源机位活动区间、模式与通道关系）。
    SEMANTICS(3),

    /// L4：可静态判定的领域数值约束（时间非负、FOV 范围、曲柄、布局正尺寸、规模上限）。
    DOMAIN(4);

    private final int level;

    ValidationLayer(int level) {
        this.level = level;
    }

    /// 层级序号，1–4；用于稳定排序与“前一层失败即停止”的判断。
    public int level() {
        return level;
    }
}
