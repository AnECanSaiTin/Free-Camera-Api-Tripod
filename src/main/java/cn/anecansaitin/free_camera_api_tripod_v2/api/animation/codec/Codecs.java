package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

/// 编解码公共工具（主设计 §8.1、§8.2）。
///
/// ## 为什么需要一层工具
///
/// 文档的值类型全部用 `record` 构造器做**局部约束**（时间非负、名字非空、尺寸为正…）。
/// 解码器必须把这些约束变成 `DataResult` 错误，而不是让异常穿透到调用方：
/// 主设计 §8.1 要求“必填字段缺失、类型错误、非法枚举及未知类型必须报错”，
/// §8.2 要求存储实现“报告错误”，两者都不允许把非法数据变成异常崩溃或默认值。
///
/// 本工具只提供**与业务无关**的映射：
///
/// - 枚举按**名字**序列化（不用 ordinal，避免调整顺序即破坏存档）；
/// - 有限 / 非负数值的字段级校验；
/// - 非空字符串；
/// - 把构造器的 `IllegalArgumentException` 转成 `DataResult` 错误。
///
/// 业务形状（字段名、分支结构）仍由各数据类型自己的 Codec 声明，不在本类里集中。
@NullMarked
public final class Codecs {

    private Codecs() {
    }

    /// 按 `Enum.name()` 序列化的枚举 Codec。
    ///
    /// 未知名字返回 `DataResult` 错误，不回退默认值：主设计 §8.1 要求非法枚举必须报错。
    /// 不用序号，是为了让“新增枚举常量”不会静默改变已有存档的语义。
    public static <E extends Enum<E>> Codec<E> enumByName(Class<E> type) {
        return Codec.STRING.comapFlatMap(
                name -> {
                    for (E constant : type.getEnumConstants()) {
                        if (constant.name().equals(name)) {
                            return DataResult.success(constant);
                        }
                    }

                    return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + name);
                },
                Enum::name);
    }

    /// 有限变体标签：只接受列出的名字，其余返回错误。
    ///
    /// 用于“多分支类型”的 `"type"` 字段：先校验标签，再用 `partialDispatch` 分派到
    /// 各分支的 `MapCodec`。这样未知标签是**数据错误**，而不是空指针或默认分支。
    public static Codec<String> variant(String... names) {
        List<String> allowed = List.of(names);

        return Codec.STRING.comapFlatMap(
                name -> allowed.contains(name)
                        ? DataResult.success(name)
                        : DataResult.error(() -> "Unknown variant: " + name + ", expected one of " + allowed),
                name -> name);
    }

    /// 严格整数：只接受**数学整数**且落在 `int` 范围内的数值。
    ///
    /// 不能直接用 `Codec.INT`：它把数值交给 `Number#intValue()`，于是
    /// `1.9` 会被静默截断成 `1`、`4294967297` 会溢出成 `1`，既改变输入又绕过后续版本 /
    /// 正整数检查（复核 R34 C2）。
    ///
    /// 也不能先经 `Codec.DOUBLE` 再判断：双精度会先把高精度小数字面量舍入成整数，
    /// 于是 `1.0000000000000001` 或 `2147483647.0000001` 可能被误收（复核 R36-N1）。
    /// 这里直接读 Ops 保留的原始 `Number`，按其十进制文本用 `BigDecimal` 做**精确**
    /// 整数性与范围检查，再转换。
    public static Codec<Integer> strictInt() {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<Integer, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getNumberValue(input)
                        .flatMap(Codecs::exactInt)
                        .map(value -> Pair.of(value, ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Integer input, DynamicOps<T> ops, T prefix) {
                return ops.mergeToPrimitive(prefix, ops.createInt(input));
            }
        };
    }

    /// 按原始十进制文本精确判断是否为 `int` 范围内的数学整数。
    ///
    /// 按 `Number` 的实际类型做精确判断。
    ///
    /// - `Float` / `Double`：`toString()` 只保证“最短可回读文本”，不代表二进制值的精确十进制
    ///   表示（例如 `100000008f` 输出 `1.0000001E8`）。这两种类型直接使用**已表示的二进制
    ///   数值**：`Float` 精确拓宽为 `double` 后检查有限性、整数性与范围（复核 R38-N1）。
    /// - 其它 `Number`（含 Gson 保留字面量的 `LazilyParsedNumber`、`BigDecimal` 与整数类型）：
    ///   按十进制文本用 `BigDecimal` 精确检查，避免重新引入 R36-N1 的舍入问题。
    private static DataResult<Integer> exactInt(Number number) {
        if (number instanceof Float || number instanceof Double) {
            return exactIntFromBinary(number.doubleValue(), number);
        }

        return exactIntFromDecimalText(number);
    }

    /// 从二进制浮点值判断。调用方只传 `Float` / `Double`：`Float` 拓宽为 `double` 是精确的，
    /// `Double` 保持原值。
    private static DataResult<Integer> exactIntFromBinary(double value, Number origin) {
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            return DataResult.error(() -> "Value must be an integer: " + origin);
        }

        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            return DataResult.error(() -> "Integer out of range: " + origin);
        }

        return DataResult.success((int) value);
    }

    /// 从十进制文本判断；`1.0`、`1e3` 视为整数，`1.0000000000000001` 不是。
    private static DataResult<Integer> exactIntFromDecimalText(Number number) {
        BigDecimal decimal;

        try {
            decimal = new BigDecimal(number.toString());
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Value must be a decimal integer: " + number);
        }

        try {
            if (decimal.stripTrailingZeros().scale() > 0) {
                return DataResult.error(() -> "Value must be an integer: " + number);
            }

            if (decimal.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0
                    || decimal.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                return DataResult.error(() -> "Integer out of range: " + number);
            }

            return DataResult.success(decimal.intValueExact());
        } catch (ArithmeticException e) {
            return DataResult.error(() -> "Value must be an integer and fit in int: " + number);
        }
    }

    /// 有限浮点：拒绝 NaN 与 Infinity。
    public static Codec<Float> finiteFloat() {
        return Codec.FLOAT.comapFlatMap(
                value -> Float.isFinite(value)
                        ? DataResult.success(value)
                        : DataResult.error(() -> "Value must be finite: " + value),
                value -> value);
    }

    /// 有限双精度：拒绝 NaN 与 Infinity。
    public static Codec<Double> finiteDouble() {
        return Codec.DOUBLE.comapFlatMap(
                value -> Double.isFinite(value)
                        ? DataResult.success(value)
                        : DataResult.error(() -> "Value must be finite: " + value),
                value -> value);
    }

    /// 时间（秒）：有限且非负。
    ///
    /// 时间以秒存储、不做量化（主设计 §4.4）。`-0.0` 由值类型构造器统一成 `0.0`，
    /// 这里只做有限与非负校验，不改变有效时间。
    public static Codec<Float> seconds() {
        return Codec.FLOAT.comapFlatMap(
                value -> !Float.isFinite(value) || value < 0f
                        ? DataResult.error(() -> "Time must be finite and non-negative: " + value)
                        : DataResult.success(value),
                value -> value);
    }

    /// 非空白字符串。
    public static Codec<String> nonBlankString() {
        return Codec.STRING.comapFlatMap(
                value -> value.isBlank()
                        ? DataResult.error(() -> "String must not be blank")
                        : DataResult.success(value),
                value -> value);
    }

    /// 把构造器的局部约束转成 `DataResult`。
    ///
    /// 用于“字段已通过各自 Codec 校验，但构造器仍可能因跨字段约束拒绝”的位置。
    /// 只捕获 `IllegalArgumentException`：其它异常是程序错误，不应被当成数据问题吞掉。
    public static <T> DataResult<T> checked(Supplier<T> factory) {
        try {
            return DataResult.success(factory.get());
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }
}
