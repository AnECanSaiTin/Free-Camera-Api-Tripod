package cn.anecansaitin.hm.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class Codecs {
    /// 有限变体标签：只接受列出的名字，其余返回错误。
    ///
    /// 用于“多分支类型”的 `"type"` 字段：先校验标签，再用 `partialDispatch` 分派到
    /// 各分支的 `MapCodec`。这样未知标签是**数据错误**，而不是空指针或默认分支。
    public static Codec<String> variant(String... names) {
        Set<String> allowed = Set.of(names);

        return Codec.STRING.comapFlatMap(
                name -> allowed.contains(name)
                        ? DataResult.success(name)
                        : DataResult.error(() -> "Unknown variant: " + name + ", expected one of " + allowed),
                name -> name);
    }

    /// 密封类标签：只接受直接子类的名称（按照小写蛇形命名法），其余返回错误。
    public static Codec<String> sealedClass(Class<?> clazz) {
        if (!clazz.isSealed()) {
            throw new IllegalArgumentException("Class " + clazz + " is not sealed");
        }

        Set<String> allowed = Arrays.stream(clazz.getPermittedSubclasses())
                .map(Class::getName)
                .map(Codecs::toSnakeCaseEnhanced)
                .collect(Collectors.toSet());

        return Codec.STRING.comapFlatMap(
                name -> allowed.contains(name)
                        ? DataResult.success(name)
                        : DataResult.error(() -> "Unknown sealed class: " + name + ", expected one of " + allowed),
                name -> name);
    }

    public static Codec<Float> positiveFloat() {
        return Codec.FLOAT.comapFlatMap(
                value -> !Float.isFinite(value) || value < 0f
                        ? DataResult.error(() -> "Float must be finite and non-negative: " + value)
                        : DataResult.success(value),
                value -> value);
    }

    public static String toSnakeCaseEnhanced(String className) {
        String simpleName = className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1)
                : className;
        return simpleName
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .toLowerCase();
    }

    /// 类型转换，避免编译器警告。
    ///
    /// 注意：使用时必须确保类型转换是安全的，否则会引发运行时异常。
    private static <T> T cast(Object object) {
        return (T) object;
    }
}
