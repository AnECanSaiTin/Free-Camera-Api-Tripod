package cn.anecansaitin.free_camera_api_tripod_v2.core.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 不可变消息到 Component 的无状态渲染（主设计 §3.2、§4.1）。
class CoreLocalizedMessageRendererTest {

    private final CoreLocalizedMessageRenderer renderer = new CoreLocalizedMessageRenderer();

    @Test
    void literalAppliesRestrictedFormatting() {
        LocalizedMessage message = LocalizedMessage.literal("hello",
                new LocalizedMessage.Formatting(0x112233, true, true, true));

        Component component = renderer.render(message);
        Style style = component.getStyle();

        assertEquals("hello", component.getString());
        assertEquals(0x112233, style.getColor().getValue());
        assertTrue(style.isBold());
        assertTrue(style.isItalic());
        assertTrue(style.isUnderlined());
    }

    @Test
    void translatableUsesFallbackAndPreservesArgumentOrder() {
        LocalizedMessage message = LocalizedMessage.translatable(
                "test.hello",
                "Hello %s %s %s",
                List.of(new LocalizedMessage.Argument.Text("world"),
                        new LocalizedMessage.Argument.Number(1.5),
                        new LocalizedMessage.Argument.Bool(true)),
                new LocalizedMessage.Formatting(0x445566, false, false, false));

        Component component = renderer.render(message);

        assertEquals("Hello world 1.5 true", component.getString());
        assertEquals(0x445566, component.getStyle().getColor().getValue());
    }

    @Test
    void nestedMessageIsRenderedAsAnArgument() {
        LocalizedMessage nested = LocalizedMessage.literal("inner",
                new LocalizedMessage.Formatting(0x00FF00, true, false, false));
        LocalizedMessage message = LocalizedMessage.translatable(
                "test.nested",
                "nested %s",
                List.of(new LocalizedMessage.Argument.Message(nested)),
                LocalizedMessage.Formatting.NONE);

        assertEquals("nested inner", renderer.render(message).getString());
    }

    @Test
    void eachRenderCreatesAnIndependentComponent() {
        LocalizedMessage message = LocalizedMessage.literal("value");

        Component first = renderer.render(message);
        Component second = renderer.render(message);

        assertNotSame(first, second);

        ((MutableComponent) first).append(Component.literal(" changed"));

        assertEquals("value", second.getString());
        assertEquals("value", renderer.render(message).getString());
    }

    @Test
    void emptyLiteralAndNullAreHandled() {
        assertEquals("", renderer.render(LocalizedMessage.literal("")).getString());
        assertThrows(NullPointerException.class, () -> renderer.render(null));
    }
}
