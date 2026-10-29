package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 有类型属性绑定：显式关闭，或启用一个与属性类型兼容的来源。
///
/// 所有可动画属性默认 [Disabled]；关闭表示“不接管”，不读取来源、不产生错误。启用后的
/// 空轨道引用、缺失引用、类型不符与求值失败分别由轨道求值/统一校验处理，不能退化成关闭。
///
/// @param <T> 属性来源类型
@NullMarked
public sealed interface PropertyBinding<T> permits PropertyBinding.Disabled, PropertyBinding.Enabled {

    /// 作者未启用该属性。
    record Disabled<T>() implements PropertyBinding<T> {
    }

    /// 启用并绑定来源。
    ///
    /// @param source 与属性类型兼容的来源
    record Enabled<T>(T source) implements PropertyBinding<T> {
        public Enabled {
            Objects.requireNonNull(source, "source");
        }
    }

    /// 共享的关闭实例。
    static <T> PropertyBinding<T> disabled() {
        return new Disabled<>();
    }

    /// 启用给定来源。
    static <T> PropertyBinding<T> enabled(T source) {
        return new Enabled<>(Objects.requireNonNull(source, "source"));
    }

    /// 默认关闭。
    static <T> PropertyBinding<T> defaults() {
        return disabled();
    }

    /// 是否启用。
    default boolean enabled() {
        return this instanceof Enabled<?>;
    }

    /// 是否关闭。
    default boolean isDisabled() {
        return this instanceof Disabled<?>;
    }

    /// 启用来源；关闭时为空。
    @SuppressWarnings("unchecked")
    default java.util.Optional<T> sourceOptional() {
        return this instanceof Enabled<?> enabled
                ? java.util.Optional.of((T) enabled.source())
                : java.util.Optional.empty();
    }

    /// 按来源 Codec 生成绑定 Codec。
    static <T> Codec<PropertyBinding<T>> codec(Codec<T> sourceCodec) {
        Objects.requireNonNull(sourceCodec, "sourceCodec");

        return Codecs.variant("disabled", "enabled").<PropertyBinding<T>>partialDispatch(
                "type",
                binding -> DataResult.success(binding.enabled() ? "enabled" : "disabled"),
                type -> switch (type) {
                    case "disabled" -> DataResult.<MapCodec<? extends PropertyBinding<T>>>success(
                            MapCodec.unit(PropertyBinding.disabled()));
                    case "enabled" -> DataResult.<MapCodec<? extends PropertyBinding<T>>>success(
                            sourceCodec.fieldOf("source").xmap(PropertyBinding::enabled, binding -> {
                                if (!(binding instanceof Enabled<T> enabled)) {
                                    throw new IllegalStateException("enabled binding expected");
                                }

                                return enabled.source();
                            }));
                    default -> DataResult.error(() -> "Unknown property binding: " + type);
                });
    }
}
