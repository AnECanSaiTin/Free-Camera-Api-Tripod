package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import org.jspecify.annotations.NullMarked;

/// 固定数值。
///
/// 关键帧字段处于“数值模式”时的取值来源；这也是把公式切回数值模式时用来初始化输入的值
/// （主设计 §6.1：旧实现里的 `Formula.literal` 只保留这一个用途）。
///
/// @param value 取值。`-0.0` 规范化为 `0.0`，其余不变
@NullMarked
public record Constant(float value) implements NumberSource {
    public Constant {
        // 主设计 §4.4：输入构造时统一 -0.0 为 0.0，不改变其他有效时间与数值
        if (value == 0f) {
            value = 0f;
        }
    }
}
