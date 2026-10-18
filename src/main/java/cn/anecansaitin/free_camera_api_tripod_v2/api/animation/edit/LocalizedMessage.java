package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// 不可变、可本地化的消息描述（主设计 §3.2、§4.1；§6.5 C3 裁决）。
///
/// ## 为什么不用 `Component`
///
/// Minecraft 的 `Component` 是可变的：`copy()` 只新建顶层对象与兄弟列表，
/// `ComponentContents`、`TranslatableContents.args` 与子组件仍共享。把它放进契约后，
/// 修改传入对象或访问器返回对象都能改变既有诊断 / 标签，无法满足深不可变（不变量 I2）。
/// 因此契约只保存**不可变描述**，由宿主在显示时渲染为 `Component`。
///
/// ## 支持的范围
///
/// - 纯文本：[Literal]；
/// - 带翻译键与参数：[Translatable]；
/// - 参数：文本、数字、布尔或嵌套消息（[Argument]）；
/// - 受限样式：[Formatting] 的颜色 / 粗体 / 斜体 / 下划线。
///
/// 不支持任意 `Component` 的 click / hover / selector / NBT / 自定义 contents；
/// 诊断与动作标签不需要这些结构，宿主按 `Severity` 与 [Formatting] 决定展示。
///
/// ## 不可变
///
/// 所有值都是 `record`、`String`、基本类型或 `List.copyOf`；没有可共享的可变组件。
@NullMarked
public sealed interface LocalizedMessage
        permits LocalizedMessage.Literal, LocalizedMessage.Translatable {

    /// 本消息整体的受限样式。
    Formatting formatting();

    /// 纯文本消息。
    ///
    /// @param text       文本；非空引用，允许空串
    /// @param formatting 受限样式
    record Literal(String text, Formatting formatting) implements LocalizedMessage {

        public Literal {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(formatting, "formatting");
        }
    }

    /// 带翻译键的消息。
    ///
    /// @param key        翻译键，非空白
    /// @param fallback   缺少翻译时的回退文本；可为空
    /// @param arguments  翻译参数；顺序即占位符顺序
    /// @param formatting 受限样式
    record Translatable(
            String key,
            @Nullable String fallback,
            List<Argument> arguments,
            Formatting formatting) implements LocalizedMessage {

        public Translatable {
            if (key.isBlank()) {
                throw new IllegalArgumentException("LocalizedMessage.Translatable key must not be blank");
            }

            Objects.requireNonNull(formatting, "formatting");

            // 防御性复制：不保留调用方容器（主设计 §3.2）
            arguments = List.copyOf(arguments);
        }
    }

    /// 受限样式：颜色、粗体、斜体、下划线。
    ///
    /// 不提供 click / hover / 插入 / 字体等任意 `Style` 能力；它们会引入可变的嵌套组件或
    /// 依赖资源身份，不属于诊断 / 动作标签的必要信息。
    ///
    /// @param color       颜色 RGB（`0x000000`–`0xFFFFFF`）；为空表示继承
    /// @param bold        粗体
    /// @param italic      斜体
    /// @param underlined  下划线
    record Formatting(@Nullable Integer color, boolean bold, boolean italic, boolean underlined) {

        /// 无额外样式。
        public static final Formatting NONE = new Formatting(null, false, false, false);

        public Formatting {
            if (color != null && (color < 0 || color > 0xFFFFFF)) {
                throw new IllegalArgumentException(
                        "LocalizedMessage.Formatting color must be in 0x000000..0xFFFFFF: " + color);
            }
        }
    }

    /// 翻译参数：文本、数字、布尔或嵌套消息。
    ///
    /// 数字与布尔由宿主按翻译规则转为文本；嵌套消息让参数本身也能带受限样式。
    sealed interface Argument permits Argument.Text, Argument.Number, Argument.Bool, Argument.Message {

        /// 文本参数。
        record Text(String value) implements Argument {
            public Text {
                Objects.requireNonNull(value, "value");
            }
        }

        /// 数字参数；必须有限。
        record Number(double value) implements Argument {
            public Number {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("Argument.Number must be finite: " + value);
                }
            }
        }

        /// 布尔参数。
        record Bool(boolean value) implements Argument {
        }

        /// 嵌套消息参数。
        record Message(LocalizedMessage value) implements Argument {
            public Message {
                Objects.requireNonNull(value, "value");
            }
        }
    }

    /// 纯文本、无额外样式。
    static Literal literal(String text) {
        return new Literal(text, Formatting.NONE);
    }

    /// 纯文本 + 受限样式。
    static Literal literal(String text, Formatting formatting) {
        return new Literal(text, formatting);
    }

    /// 翻译键、无回退、无额外样式。
    static Translatable translatable(String key, Argument... arguments) {
        return new Translatable(key, null, List.of(arguments), Formatting.NONE);
    }

    /// 翻译键 + 回退文本 + 参数 + 样式。
    static Translatable translatable(String key, @Nullable String fallback,
                                     List<Argument> arguments, Formatting formatting) {
        return new Translatable(key, fallback, arguments, formatting);
    }

    /// 换受限样式，内容不变。
    default LocalizedMessage withFormatting(Formatting newFormatting) {
        return switch (this) {
            case Literal literal -> new Literal(literal.text(), newFormatting);
            case Translatable translatable -> new Translatable(
                    translatable.key(), translatable.fallback(), translatable.arguments(), newFormatting);
        };
    }
}
