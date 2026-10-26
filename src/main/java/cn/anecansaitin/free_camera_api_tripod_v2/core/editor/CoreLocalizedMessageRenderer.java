package cn.anecansaitin.free_camera_api_tripod_v2.core.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessageRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 把不可变 [LocalizedMessage] 渲染成宿主 [Component] 的无状态适配器（主设计 §3.2、§4.1）。
///
/// 每次调用都重新创建组件树：纯文本使用 [Component#literal]，翻译键使用
/// [Component#translatable] / [Component#translatableWithFallback]，参数按原顺序转换，
/// 嵌套消息递归渲染。受限样式只应用 [LocalizedMessage.Formatting] 的 RGB 颜色、粗体、斜体与
/// 下划线，不读取或修改消息本身。
///
/// 本类不缓存组件，也不把渲染结果写回契约；调用方修改返回的 `MutableComponent` 不会影响消息
/// 或下一次渲染。语言切换由 Minecraft 的翻译表在 `getString()` / 显示时处理，本类不提前把
/// 翻译键转成纯文本。
@NullMarked
public final class CoreLocalizedMessageRenderer implements LocalizedMessageRenderer {

    @Override
    public Component render(LocalizedMessage message) {
        Objects.requireNonNull(message, "message");

        MutableComponent component = switch (message) {
            case LocalizedMessage.Literal literal -> Component.literal(literal.text());
            case LocalizedMessage.Translatable translatable -> renderTranslatable(translatable);
        };

        return component.withStyle(style(message.formatting()));
    }

    private static MutableComponent renderTranslatable(LocalizedMessage.Translatable message) {
        Object[] arguments = message.arguments().stream()
                .map(CoreLocalizedMessageRenderer::renderArgument)
                .toArray();

        return message.fallback() == null
                ? Component.translatable(message.key(), arguments)
                : Component.translatableWithFallback(message.key(), message.fallback(), arguments);
    }

    private static Object renderArgument(LocalizedMessage.Argument argument) {
        return switch (argument) {
            case LocalizedMessage.Argument.Text text -> text.value();
            case LocalizedMessage.Argument.Number number -> number.value();
            case LocalizedMessage.Argument.Bool bool -> bool.value();
            case LocalizedMessage.Argument.Message message -> renderNested(message.value());
        };
    }

    /// 嵌套消息需要独立渲染；静态方法避免在流式参数转换中依赖实例状态。
    private static Component renderNested(LocalizedMessage message) {
        MutableComponent component = switch (message) {
            case LocalizedMessage.Literal literal -> Component.literal(literal.text());
            case LocalizedMessage.Translatable translatable -> renderTranslatable(translatable);
        };

        return component.withStyle(style(message.formatting()));
    }

    private static Style style(LocalizedMessage.Formatting formatting) {
        Style style = Style.EMPTY;

        if (formatting.color() != null) {
            style = style.withColor(TextColor.fromRgb(formatting.color()));
        }

        if (formatting.bold()) {
            style = style.withBold(true);
        }

        if (formatting.italic()) {
            style = style.withItalic(true);
        }

        if (formatting.underlined()) {
            style = style.withUnderlined(true);
        }

        return style;
    }
}
