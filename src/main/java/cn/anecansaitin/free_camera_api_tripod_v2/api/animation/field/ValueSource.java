package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NullMarked;

/// 关键帧字段的取值来源：**固定值，或一段公式**。
///
/// 这是封闭层级（主设计 §6.1）：
///
/// ```text
/// ValueSource permits NumberSource, TrackRef
/// NumberSource permits Constant, Formula
/// ```
///
/// `TrackRef` **不实现** `NumberSource`，因此轨道读数在类型上根本进不了关键帧字段；
/// 变量读轨道仍然合法，见 `api.animation.field.TrackRef`。
///
/// ## 为什么没有“回退值”
///
/// 旧实现给每段公式配一个固定数值，求值失败就悄悄换回去（`evaluateOrFallback`）。
/// 主设计明确删除该路径（§6.1、§10.2）：求值失败必须如实上报为
/// `api.animation.eval.SampleStatus#ERROR`，不得伪装成合法数值（不变量 I9）。
/// 因此公式**只存文本**，失败诊断由求值层给出。
///
/// 界面上“这个槽位是数值还是公式”由实现类型回答，不需要额外的布尔标志。
@NullMarked
public sealed interface ValueSource permits NumberSource, TrackRef {

    /// 分支标签：`constant` / `formula` / `track_ref`。
    Codec<String> VARIANT = Codecs.variant("constant", "formula", "track_ref");

    /// 结构编码（仅用于变量这类**允许轨道读数**的位置）。
    ///
    /// 关键帧字段使用 [NumberSource#CODEC]，它在类型上就不包含 `track_ref` 分支：
    /// 主设计 §6.1 不允许轨道读数进入关键帧字段。
    Codec<ValueSource> CODEC = VARIANT.<ValueSource>partialDispatch(
            "type",
            source -> DataResult.success(switch (source) {
                case Constant ignored -> "constant";
                case Formula ignored -> "formula";
                case TrackRef ignored -> "track_ref";
            }),
            name -> switch (name) {
                case "constant" -> DataResult.<MapCodec<? extends ValueSource>>success(Constant.MAP_CODEC);
                case "formula" -> DataResult.<MapCodec<? extends ValueSource>>success(Formula.MAP_CODEC);
                case "track_ref" -> DataResult.<MapCodec<? extends ValueSource>>success(TrackRef.MAP_CODEC);
                default -> DataResult.error(() -> "Unknown value source: " + name);
            });
}
