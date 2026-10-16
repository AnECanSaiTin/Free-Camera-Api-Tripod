package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import org.jspecify.annotations.NullMarked;

/// 能直接给出一个数值的取值来源：固定值或公式。
///
/// 约定（主设计 §6.1）：
///
/// - 求值结果只有三种状态：`ABSENT`（没编写）、`VALUE`（有限合法数值）、
///   `ERROR`（已编写但本帧求值失败）。状态由求值层给出，
///   **本接口不提供“失败就返回 NaN”的宽松入口**。
/// - 公式编译失败不阻止它被存进文档（界面要显示作者写错的内容），
///   但求值必须报错，不得静默替换。
/// - 值源均为不可变 `record`，放进别的对象时不需要复制。
@NullMarked
public sealed interface NumberSource extends ValueSource permits Constant, Formula {
}
