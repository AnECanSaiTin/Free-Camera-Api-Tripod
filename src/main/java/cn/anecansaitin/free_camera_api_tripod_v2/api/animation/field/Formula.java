package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NullMarked;

/// 公式值：一段表达式文本。
///
/// **只有文本**。没有回退数值，也没有编译产物：
///
/// - 回退数值是旧实现的隐式兜底，主设计 §6.1 / §10.2 明确删除；求值失败必须报错。
/// - 编译结果属于派生数据，按公式文本在 `api.animation.eval` 的编译缓存里驻留，
///   不能进这个 `record`——否则 `equals`、Codec 往返与文档快照都会捎上语法树。
///   同一段文本全局只编译一次。
///
/// 编译不过的公式照样可以存进文档（作者要能看见并修改自己写错的内容），
/// 但求值一律报 `ERROR`，并且必须带结构化诊断（主设计 §6.1）。
///
/// @param expression 表达式文本，非空白
@NullMarked
public record Formula(String expression) implements NumberSource {

    /// 结构编码：`{"type": "formula", "expression": "..."}` 的 `expression` 部分。
    ///
    /// 文本非空白；编译结果不进存档，解码后按文本在编译缓存里驻留（主设计 §3.4）。
    public static final MapCodec<Formula> MAP_CODEC =
            Codecs.nonBlankString().fieldOf("expression").xmap(Formula::new, Formula::expression);

    /// 独立解析本分支时使用的 Codec。
    public static final Codec<Formula> CODEC = MAP_CODEC.codec();

    public Formula {
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Formula expression must not be blank");
        }
    }

    @Override
    public String toString() {
        return expression;
    }
}
