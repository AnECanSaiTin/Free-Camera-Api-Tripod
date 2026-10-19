package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;

/// 可随时间变化的数值：**常量值源或数值曲线的封闭分支**（主设计 §3.1.2）。
///
/// 服务布局的 `x / y / width / height` 一类连续属性：它们既可以是固定值或公式，
/// 也可以是一整条数值曲线。两条分支都是封闭集，**不增加第四类开放注册表**，
/// 也不增加布局专用的隐式求值路径：求值仍走 `NumberSource` 与数值曲线的既有规则
/// （编译、关键帧、插值、缓存，见主设计 §6.1、§3.4）。
///
/// ## 两条分支
///
/// | 分支 | 数据 | 语义 |
/// | --- | --- | --- |
/// | [Value] | `NumberSource`（`Constant` \\| `Formula`） | 任意时刻由值源给出；公式按统一求值上下文求值 |
/// | [Keyed] | 数值 `Curve` + 显式默认值 | 有键时按键插值；**空曲线使用显式默认值**；非空曲线首末之外保持端点 |
///
/// ## 为什么空曲线要有显式默认值
///
/// 空曲线若被隐式当作 `0`，会让“还没打键”变成“值为零”，与作者意图无关：
/// 宽度为 0 的矩形会被判为非法，而作者其实只是尚未打第一个键。
/// 显式默认值把这件事写进数据，与状态轨道“首键前使用显式默认值”的口径一致
/// （主设计 §3.1.1）。
///
/// ## 端点保持（CLAMP）
///
/// 主设计 §3.1.2 规定“非空曲线首末范围外保持端点”。因此 [Keyed] 只接受
/// `CLAMP / CLAMP` 的曲线：`LOOP` / `PING_PONG` 对布局数值没有定义语义，
/// 构造时直接拒绝，而不是留到求值层做一次隐式替换。
///
/// ## 布局读数不对外
///
/// 布局数值**不通过 `TrackRef` 对外提供读数**：它不是轨道，没有 `TrackId`，
/// 不能被变量或公式引用（主设计 §3.1.2）。这样不会扩大“机位通道引用”的语义。
/// 反向允许：布局自己的公式可以读文档时间、上下文无关的符号与世界采样；
/// **绑定时拒绝隐式读取当前编辑机位的空间事实**，显式 `TrackRef` 仍在被引用通道
/// 所属机位的上下文求值（主设计 §3.1.2）。这条约束由求值层实施，本类型只保证数据形状。
///
/// ## 静态与动态
///
/// [isStatic] / [staticValue] 区分“能否在没有时间与求值上下文时确定取值”：
/// 常量分支的 `Constant`、以及空曲线的显式默认值是静态的；公式与非空曲线是动态的。
/// 校验器据此做**静态**检查（例如常量宽度必须为正），运行期仍须逐帧检查
/// （曲线过冲与公式产生的非法中间帧属于运行期错误，终止整场，见主设计 §3.1.2）。
@NullMarked
public sealed interface AnimatedNumber permits AnimatedNumber.Value, AnimatedNumber.Keyed {

    /// 分支标签：`value`（常量值源）/ `keyed`（数值曲线）。
    Codec<String> VARIANT = Codecs.variant("value", "keyed");

    /// 常量分支的结构编码：`{"type": "value", "source": {...}}`。
    MapCodec<Value> VALUE_MAP_CODEC =
            NumberSource.CODEC.fieldOf("source").xmap(Value::new, Value::source);

    /// 曲线分支的结构编码：`{"type": "keyed", "curve": {...}, "default": <float>}`。
    MapCodec<Keyed> KEYED_MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Curve.CODEC.fieldOf("curve").forGetter(Keyed::curve),
            Codecs.finiteFloat().fieldOf("default").forGetter(Keyed::defaultValue)
    ).apply(instance, Keyed::new));

    /// 结构编码：按 `type` 分派到两个封闭分支。
    ///
    /// 解码时 `Keyed` 的 `CLAMP` 约束由构造器检查：非 `CLAMP` 的曲线属于非法布局数据，
    /// 由解码边界转成结构错误，而不是静默改成端点保持（主设计 §3.1.2）。
    Codec<AnimatedNumber> CODEC = VARIANT.<AnimatedNumber>partialDispatch(
            "type",
            number -> DataResult.success(number.isKeyed() ? "keyed" : "value"),
            name -> switch (name) {
                case "value" -> DataResult.<MapCodec<? extends AnimatedNumber>>success(VALUE_MAP_CODEC);
                case "keyed" -> DataResult.<MapCodec<? extends AnimatedNumber>>success(KEYED_MAP_CODEC);
                default -> DataResult.error(() -> "Unknown animated number: " + name);
            });

    /// 常量值源分支：任意时刻由 `NumberSource` 给出取值。
    ///
    /// @param source 固定值或公式，非空
    record Value(NumberSource source) implements AnimatedNumber {
        public Value {
            Objects.requireNonNull(source, "source");
        }

        /// 换值源，分支不变。
        public Value withSource(NumberSource newSource) {
            return new Value(newSource);
        }
    }

    /// 数值曲线分支：由关键帧曲线驱动。
    ///
    /// 键值是 `NumberSource`（固定值或公式），与数值通道完全同构；键时间秒基准、
    /// 严格递增且同曲线唯一（见 [Curve]）。曲线为空时取 [defaultValue]。
    ///
    /// `KeyId` 的作用域是**本条曲线**：布局曲线不是轨道，没有 `TrackId`，
    /// 因此不与“`TrackId` 在整份文档内唯一”的规则冲突。键身份仍然稳定：
    /// 移动、改值保留 `KeyId`，复制时由 `IdGenerator` 生成新的。
    ///
    /// @param curve        数值曲线；只接受 `CLAMP / CLAMP`
    /// @param defaultValue 空曲线时的显式默认值，必须有限
    record Keyed(Curve curve, float defaultValue) implements AnimatedNumber {
        public Keyed {
            Objects.requireNonNull(curve, "curve");

            if (!Float.isFinite(defaultValue)) {
                throw new IllegalArgumentException(
                        "AnimatedNumber default value must be finite: " + defaultValue);
            }

            // 主设计 §3.1.2：非空曲线首末范围外保持端点
            if (curve.preMode() != Curve.Wrap.CLAMP || curve.postMode() != Curve.Wrap.CLAMP) {
                throw new IllegalArgumentException(
                        "AnimatedNumber curve must hold endpoints outside the key range (CLAMP/CLAMP), got "
                                + curve.preMode() + "/" + curve.postMode());
            }

            // -0.0 规范化，与其他数值入口一致
            if (defaultValue == 0f) {
                defaultValue = 0f;
            }
        }

        /// 该曲线末键的时刻；空曲线为 0。
        ///
        /// 布局轨道参与播放时长推导（主设计 §3.1），因此这个值要被上层读取；
        /// 空曲线不产生时长，静态属性不会把动画拖成无限长。
        public float endTime() {
            return curve.isEmpty() ? 0f : curve.last().time();
        }

        /// 换曲线，默认值不变。
        public Keyed withCurve(Curve newCurve) {
            return new Keyed(newCurve, defaultValue);
        }

        /// 换空曲线默认值，曲线不变。
        public Keyed withDefaultValue(float newDefaultValue) {
            return new Keyed(curve, newDefaultValue);
        }
    }

    // region 工厂

    /// 固定数值。
    static AnimatedNumber constant(float value) {
        return new Value(new Constant(value));
    }

    /// 公式。
    static AnimatedNumber formula(String expression) {
        return new Value(new Formula(expression));
    }

    /// 曲线分支；`defaultValue` 只在曲线为空时生效。
    static AnimatedNumber keyed(Curve curve, float defaultValue) {
        return new Keyed(curve, defaultValue);
    }

    // endregion

    // region 查询

    /// 没有曲线键时为真：此时取值恒为显式默认值。
    default boolean isKeyed() {
        return this instanceof Keyed;
    }

    /// 是否可在没有时间与求值上下文时确定取值。
    ///
    /// - `Value(Constant)`：是；
    /// - `Value(Formula)`：否（公式需要求值上下文）；
    /// - `Keyed(空曲线)`：是（恒为默认值）；
    /// - `Keyed(非空曲线)`：否（取决于时间与帧版本）。
    ///
    /// 静态检查（例如“常量宽度必须为正”）只看 `true` 的情形；`false` 的情形必须留到
    /// 运行期逐帧检查（主设计 §3.1.2：静态存在合法键不等于连续区间合法）。
    default boolean isStatic() {
        return switch (this) {
            case Value value -> value.source() instanceof Constant;
            case Keyed keyed -> keyed.curve().isEmpty();
        };
    }

    /// 静态取值；[isStatic] 为假时返回空。
    ///
    /// 不用 `NaN` 表示“未知”：本项目禁止用哨兵值同时表达“未解析”和“错误”
    /// （主设计 §3.4、§6.1）。
    default Optional<Float> staticValue() {
        return switch (this) {
            case Value value -> value.source() instanceof Constant constant
                    ? Optional.of(constant.value())
                    : Optional.<Float>empty();
            case Keyed keyed -> keyed.curve().isEmpty()
                    ? Optional.of(keyed.defaultValue())
                    : Optional.<Float>empty();
        };
    }

    /// 曲线分支的曲线；常量分支为空。供编辑层显示“这条布局值有没有曲线”。
    ///
    /// 命名带 `as` 前缀是为了与 record 组件访问器 [Keyed#curve] 区分：
    /// 那条返回本分支的曲线本身，这条返回可选视图。
    default Optional<Curve> asCurve() {
        return this instanceof Keyed keyed ? Optional.of(keyed.curve()) : Optional.empty();
    }

    /// 该布局值在时间轴上的结束时刻：曲线末键时刻；常量分支与空曲线为 0。
    ///
    /// 静态属性与末键保持都不产生无限时长（主设计 §3.1）。
    default float endTime() {
        return this instanceof Keyed keyed ? keyed.endTime() : 0f;
    }

    // endregion

    // region 派生

    // 派生只在本分支上提供：跨分支的“顺便转换”会静默丢掉曲线或默认值，
    // 让编辑失去撤销语义，也让作者看不出数据去了哪里。切分支请显式构造另一分支。

    // endregion
}
