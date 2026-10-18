package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 不可变消息描述与受限样式（主设计 §3.2；§6.5 C3）。
class LocalizedMessageContractTest {

    @Test
    void translatableCopiesArgumentsAndKeepsFallback() {
        List<LocalizedMessage.Argument> arguments = new ArrayList<>(List.of(
                new LocalizedMessage.Argument.Text("value"),
                new LocalizedMessage.Argument.Number(2),
                new LocalizedMessage.Argument.Bool(true),
                new LocalizedMessage.Argument.Message(LocalizedMessage.literal("nested"))));

        LocalizedMessage.Translatable message = new LocalizedMessage.Translatable(
                "test.key", "fallback", arguments, LocalizedMessage.Formatting.NONE);
        arguments.clear();

        assertEquals(4, message.arguments().size());
        assertEquals("fallback", message.fallback());
        assertEquals(LocalizedMessage.literal("nested"),
                ((LocalizedMessage.Argument.Message) message.arguments().get(3)).value());
    }

    @Test
    void formattingIsRestrictedAndReplaceable() {
        LocalizedMessage.Formatting formatting = new LocalizedMessage.Formatting(0x123456, true, false, true);
        LocalizedMessage message = LocalizedMessage.literal("text", formatting);

        assertEquals(formatting, message.formatting());
        assertEquals(LocalizedMessage.Formatting.NONE,
                message.withFormatting(LocalizedMessage.Formatting.NONE).formatting());

        assertThrows(IllegalArgumentException.class,
                () -> new LocalizedMessage.Formatting(-1, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalizedMessage.Formatting(0x1000000, false, false, false));
    }

    @Test
    void argumentsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocalizedMessage.Argument.Number(Double.NaN));
        assertThrows(NullPointerException.class,
                () -> new LocalizedMessage.Argument.Message(null));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalizedMessage.Translatable(" ", null, List.of(), LocalizedMessage.Formatting.NONE));
    }

    @Test
    void messagesCompareByValueAndStyle() {
        assertEquals(LocalizedMessage.literal("a"), LocalizedMessage.literal("a"));
        assertNotEquals(LocalizedMessage.literal("a"),
                LocalizedMessage.literal("a", new LocalizedMessage.Formatting(0xFF0000, true, false, false)));
    }
}
