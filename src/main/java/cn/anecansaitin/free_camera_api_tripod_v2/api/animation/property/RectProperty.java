package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 布局矩形四个分量的显式绑定。
///
/// 坐标分支与角落是静态数据；四个分量各自可以是常量、公式或数值轨道。未接管分量使用
/// 合法静态默认值（全屏矩形）。宽高必须在求值后保持有限正数。
@NullMarked
public sealed interface RectProperty permits RectProperty.Normalized, RectProperty.Anchored {

    /// 归一化分支。
    record Normalized(
            PropertyBinding<NumericSource> x,
            PropertyBinding<NumericSource> y,
            PropertyBinding<NumericSource> width,
            PropertyBinding<NumericSource> height) implements RectProperty {

        public Normalized {
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
            Objects.requireNonNull(width, "width");
            Objects.requireNonNull(height, "height");
        }
    }

    /// 锚定角落分支。
    record Anchored(
            RigView.Rect.Corner corner,
            PropertyBinding<NumericSource> x,
            PropertyBinding<NumericSource> y,
            PropertyBinding<NumericSource> width,
            PropertyBinding<NumericSource> height) implements RectProperty {

        public Anchored {
            Objects.requireNonNull(corner, "corner");
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
            Objects.requireNonNull(width, "width");
            Objects.requireNonNull(height, "height");
        }
    }

    /// 静态全屏、四分量全部关闭。
    static RectProperty fullScreen() {
        return new Normalized(
                PropertyBinding.disabled(),
                PropertyBinding.disabled(),
                PropertyBinding.disabled(),
                PropertyBinding.disabled());
    }

    /// 结构编码。
    Codec<RectProperty> CODEC = Codecs.variant("normalized", "anchored")
            .<RectProperty>partialDispatch(
                    "type",
                    rect -> DataResult.success(rect instanceof Anchored ? "anchored" : "normalized"),
                    type -> switch (type) {
                        case "normalized" -> DataResult.<MapCodec<? extends RectProperty>>success(
                                RecordCodecBuilder.<Normalized>mapCodec(instance -> instance.group(
                                        PropertyBinding.codec(NumericSource.CODEC)
                                                .fieldOf("x").forGetter(Normalized::x),
                                        PropertyBinding.codec(NumericSource.CODEC)
                                                .fieldOf("y").forGetter(Normalized::y),
                                        PropertyBinding.codec(NumericSource.CODEC)
                                                .fieldOf("width").forGetter(Normalized::width),
                                        PropertyBinding.codec(NumericSource.CODEC)
                                                .fieldOf("height").forGetter(Normalized::height)
                                ).apply(instance, Normalized::new)));
                        case "anchored" -> DataResult.<MapCodec<? extends RectProperty>>success(
                                RecordCodecBuilder.<Anchored>mapCodec(instance -> instance.group(
                                        Codecs.enumByName(RigView.Rect.Corner.class)
                                                .fieldOf("corner").forGetter(Anchored::corner),
                                        PropertyBinding.codec(NumericSource.CODEC)
                                                .fieldOf("x").forGetter(Anchored::x),
                                        PropertyBinding.codec(NumericSource.CODEC)
                                                .fieldOf("y").forGetter(Anchored::y),
                                        PropertyBinding.codec(NumericSource.CODEC)
                                                .fieldOf("width").forGetter(Anchored::width),
                                        PropertyBinding.codec(NumericSource.CODEC)
                                                .fieldOf("height").forGetter(Anchored::height)
                                ).apply(instance, Anchored::new)));
                        default -> DataResult.error(() -> "Unknown rect property: " + type);
                    });
}
