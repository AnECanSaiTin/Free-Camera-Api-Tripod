package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
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

    /// 分支标签：`constant` / `formula`。
    Codec<String> VARIANT = Codecs.variant("constant", "formula");

    /// 结构编码：固定分支写 `{"type": "constant", "value": ...}`，
    /// 公式分支写 `{"type": "formula", "expression": "..."}`。
    ///
    /// 采用**显式分支标签**而不是“数字即常量、字符串即公式”的隐式判别：
    /// 后者在诊断与将来扩展分支时都不可读，也无法给出稳定的错误码路径。
    Codec<NumberSource> CODEC = VARIANT.<NumberSource>partialDispatch(
            "type",
            source -> DataResult.success(source instanceof Formula ? "formula" : "constant"),
            name -> switch (name) {
                case "constant" -> DataResult.<MapCodec<? extends NumberSource>>success(Constant.MAP_CODEC);
                case "formula" -> DataResult.<MapCodec<? extends NumberSource>>success(Formula.MAP_CODEC);
                default -> DataResult.error(() -> "Unknown number source: " + name);
            });
}
