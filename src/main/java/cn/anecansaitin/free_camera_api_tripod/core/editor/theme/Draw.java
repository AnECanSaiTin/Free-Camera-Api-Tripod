package cn.anecansaitin.free_camera_api_tripod.core.editor.theme;

import cn.anecansaitin.free_camera_api_tripod.EditorConfig;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.render.FloatFill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// 编辑器配色与绘制辅助。默认深色，可在「视图 → 深色模式」里切换为浅色。
///
/// 配色字段随主题整体重新赋值，因此不是 {@code final} 常量：全项目都用
/// {@code Draw.TEXT} 这样的字段名引用，切主题时无需改动任何调用点。
public final class Draw {
    /// 主题：深色 / 浅色。只影响配色，不影响布局与数据
    public enum Theme {
        DARK,
        LIGHT
    }

    public static int TOOLBAR_BG;
    /// 编辑界面整体底衬：不透明，面板收起或留缝时也不会露出游戏画面
    public static int SCREEN_BG;
    public static int PANEL_BG;
    public static int PANEL_HEADER_BG;
    public static int BORDER;
    public static int SPLITTER;
    public static int SPLITTER_HOVER;
    public static int CANVAS_BG;
    public static int GRID;
    public static int GRID_STRONG;
    /// 工具栏、弹窗等浮层的底色
    public static int FLOATING_BG;
    /// 右键菜单底色（比浮层略透一点，能看见底下一点内容）
    public static int MENU_BG;

    public static int TEXT;
    public static int TEXT_DIM;
    public static int TEXT_DISABLED;

    public static int ACCENT;
    public static int SELECTED;
    public static int PLAYHEAD;
    public static int PATH_NODE;

    public static int BUTTON_BG;
    public static int BUTTON_BG_HOVER;
    public static int BUTTON_BG_ACTIVE;
    public static int FIELD_BG;
    public static int ROW_ALT;
    public static int ROW_SELECTED;
    /// 分组行与浮层高亮
    public static int ROW_GROUP;
    /// 压暗区域用的半透明遮罩：深色主题用黑，浅色主题用更淡的黑，避免把文字压得看不清
    public static int OVERLAY_DIM;
    /// 文字阴影色：0 表示沿用原版阴影（把文字色压暗四分之一）；
    /// 浅色主题改用浅色阴影，否则深色文字压着深色阴影在浅底上会显得发糊
    public static int TEXT_SHADOW;

    /// 浮点线段按像素步进填充的步数上限，防止异常长度把一帧拖死
    private static final int MAX_LINE_STEPS = 4096;

    private static final List<TruncatedText> TRUNCATED = new ArrayList<>();

    /// 当前主题；初始为 null，静态块里铺第一套配色时会落下 DARK
    private static @Nullable Theme theme;
    /// 文字样式：浅色主题需要显式指定阴影色，深色主题留空以沿用原版
    private static Style textStyle = Style.EMPTY;

    static {
        // 配色字段初始为 0（全透明），这里必须先铺一套，否则首帧画出来是一片空白
        applyTheme(Theme.DARK);
    }

    private Draw() {
    }

    // region 主题

    /// 当前是否为深色主题
    public static boolean darkMode() {
        return theme == Theme.DARK;
    }

    /// 切换到指定主题；已经是该主题时不做事
    public static void darkMode(boolean dark) {
        applyTheme(dark ? Theme.DARK : Theme.LIGHT);
    }

    /// 在深色与浅色之间切换并写入配置；下一帧 {@link #beginFrame()} 时生效
    public static void toggleTheme() {
        EditorConfig.DARK_MODE.set(!darkMode());
        EditorConfig.save();
    }

    private static void applyTheme(Theme target) {
        if (theme == target) {
            return;
        }

        theme = target;

        if (target == Theme.DARK) {
            TOOLBAR_BG = 0xFF1B1B21;
            SCREEN_BG = 0xFF0C0C10;
            PANEL_BG = 0xFF141418;
            PANEL_HEADER_BG = 0xFF24242C;
            BORDER = 0xFF4E4E5E;
            SPLITTER = 0xFF16161A;
            SPLITTER_HOVER = 0xFF4EA1FF;
            CANVAS_BG = 0xFF101014;
            GRID = 0xFF262630;
            GRID_STRONG = 0xFF3A3A48;
            FLOATING_BG = 0xFF1A1A20;
            MENU_BG = 0xF51B1B21;
            TEXT = 0xFFE2E2EA;
            TEXT_DIM = 0xFF9A9AA8;
            TEXT_DISABLED = 0xFF62626E;
            ACCENT = 0xFF4EA1FF;
            SELECTED = 0xFFFFB74D;
            PLAYHEAD = 0xFFFF5252;
            PATH_NODE = 0xFF7CFC9A;
            BUTTON_BG = 0xFF2A2A33;
            BUTTON_BG_HOVER = 0xFF3A3A48;
            BUTTON_BG_ACTIVE = 0xFF2F5F9F;
            FIELD_BG = 0xFF121216;
            ROW_ALT = 0xFF1B1B22;
            ROW_SELECTED = 0xFF2A4A78;
            ROW_GROUP = 0xFF1F1F27;
            OVERLAY_DIM = 0x66000000;
            // 深色底上用原版阴影（文字色压暗四分之一），层次正好
            TEXT_SHADOW = 0;
            textStyle = Style.EMPTY;
        } else {
            TOOLBAR_BG = 0xFFE6E6EC;
            SCREEN_BG = 0xFFF1F1F5;
            PANEL_BG = 0xFFF8F8FB;
            PANEL_HEADER_BG = 0xFFDCDCE6;
            BORDER = 0xFF9C9CAC;
            SPLITTER = 0xFFC6C6D2;
            SPLITTER_HOVER = 0xFF2F6FD0;
            CANVAS_BG = 0xFFFFFFFF;
            GRID = 0xFFE2E2EA;
            GRID_STRONG = 0xFFBFBFCC;
            FLOATING_BG = 0xFFF4F4F8;
            MENU_BG = 0xF5F6F6FA;
            TEXT = 0xFF1E1E26;
            TEXT_DIM = 0xFF5A5A68;
            TEXT_DISABLED = 0xFF9A9AA6;
            ACCENT = 0xFF2F6FD0;
            SELECTED = 0xFFD98200;
            PLAYHEAD = 0xFFD93025;
            PATH_NODE = 0xFF1E8E3E;
            BUTTON_BG = 0xFFE8E8EF;
            BUTTON_BG_HOVER = 0xFFD4D4DF;
            BUTTON_BG_ACTIVE = 0xFFB9D2F6;
            FIELD_BG = 0xFFFFFFFF;
            ROW_ALT = 0xFFEDEDF4;
            ROW_SELECTED = 0xFFB9D2F6;
            ROW_GROUP = 0xFFE8E8F0;
            OVERLAY_DIM = 0x24000000;
            // 浅色底上原版阴影会把深色文字再压暗一圈，看着发糊；换成半透明浅灰阴影拉开层次
            TEXT_SHADOW = 0x66999999;
            textStyle = Style.EMPTY.withShadowColor(TEXT_SHADOW);
        }
    }

    // endregion

    /// 被截断的文本：记录屏幕区域与完整内容，供鼠标悬停时提示
    public record TruncatedText(UiRect rect, String text) {
    }

    /// 每帧绘制前清空截断记录，并把主题同步到配置里的选择
    public static void beginFrame() {
        TRUNCATED.clear();
        darkMode(EditorConfig.DARK_MODE.get());
    }

    /// 鼠标位置下被截断的文本；同一位置有多个时取最后记录的一个
    public static @Nullable TruncatedText truncatedAt(double mouseX, double mouseY) {
        TruncatedText found = null;

        for (TruncatedText candidate : TRUNCATED) {
            if (candidate.rect().contains(mouseX, mouseY)) {
                found = candidate;
            }
        }

        return found;
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void panel(GuiGraphicsExtractor graphics, UiRect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_BG);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), BORDER);
    }

    public static void canvas(GuiGraphicsExtractor graphics, UiRect rect, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
    }

    public static void border(GuiGraphicsExtractor graphics, UiRect rect, int color) {
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), color);
    }

    public static void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        if (TEXT_SHADOW == 0) {
            graphics.text(font(), text, x, y, color);
            return;
        }

        graphics.text(font(), Component.literal(text).withStyle(textStyle), x, y, color);
    }

    public static void text(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font(), TEXT_SHADOW == 0 ? text : text.copy().withStyle(textStyle), x, y, color);
    }

    public static void textCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color) {
        if (TEXT_SHADOW == 0) {
            graphics.centeredText(font(), text, centerX, y, color);
            return;
        }

        graphics.centeredText(font(), Component.literal(text).withStyle(textStyle), centerX, y, color);
    }

    public static void textCentered(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color) {
        graphics.centeredText(font(), TEXT_SHADOW == 0 ? text : text.copy().withStyle(textStyle), centerX, y, color);
    }

    public static void textRight(GuiGraphicsExtractor graphics, String text, int rightX, int y, int color) {
        text(graphics, text, rightX - font().width(text), y, color);
    }

    public static void textEllipsized(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        String value = text;
        boolean clipped = false;

        if (font().width(value) > maxWidth) {
            value = font().plainSubstrByWidth(value, Math.max(0, maxWidth - font().width("..."))) + "...";
            clipped = true;
        }

        text(graphics, value, x, y, color);

        if (clipped) {
            TRUNCATED.add(new TruncatedText(new UiRect(x, y, font().width(value), 9), text));
        }
    }

    /// 绘制浮层提示框，自动避开鼠标右侧与屏幕下边缘
    public static void tooltip(GuiGraphicsExtractor graphics, String text, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int textWidth = font().width(text);
        int boxWidth = textWidth + 6;
        int boxHeight = 14;
        int x = Math.min(mouseX + 10, Math.max(0, screenWidth - boxWidth - 2));
        int y = mouseY + 14;

        if (y + boxHeight > screenHeight) {
            y = Math.max(0, mouseY - boxHeight - 4);
        }

        UiRect rect = new UiRect(x, y, boxWidth, boxHeight);
        canvas(graphics, rect, FLOATING_BG);
        border(graphics, rect, BORDER);
        text(graphics, text, x + 3, y + 3, TEXT);
    }

    /// 水平分隔线
    public static void hLine(GuiGraphicsExtractor graphics, int x0, int x1, int y, int color) {
        graphics.fill(x0, y, x1, y + 1, color);
    }

    public static void vLine(GuiGraphicsExtractor graphics, int x, int y0, int y1, int color) {
        graphics.fill(x, y0, x + 1, y1, color);
    }

    /// 按钮式背景
    public static void button(GuiGraphicsExtractor graphics, UiRect rect, boolean hovered, boolean active) {
        int bg = active ? BUTTON_BG_ACTIVE : hovered ? BUTTON_BG_HOVER : BUTTON_BG;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), BORDER);
    }

    public static void field(GuiGraphicsExtractor graphics, UiRect rect, boolean focused) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), FIELD_BG);
        graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), focused ? ACCENT : BORDER);
    }

    /// 菱形关键帧标记
    public static void diamond(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = radius - Math.abs(dy);
            graphics.fill(centerX - halfWidth, centerY + dy, centerX + halfWidth + 1, centerY + dy + 1, color);
        }
    }

    /// 浮点坐标填充，用于需要平滑边缘的曲线
    public static void floatFill(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int color) {
        graphics.submitGuiElementRenderState(new FloatFill(
                RenderPipelines.GUI,
                TextureSetup.noTexture(),
                new Matrix3x2f(graphics.pose()),
                x0, y0, x1, y1,
                color, color,
                graphics.peekScissorStack()
        ));
    }

    /// 浮点坐标线段：按步进填充，避免整数取整带来的锯齿
    public static void floatLine(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float thickness, int color) {
        float deltaX = x1 - x0;
        float deltaY = y1 - y0;
        float length = Math.max(Math.abs(deltaX), Math.abs(deltaY));

        // 非有限值的坐标画不出来，直接放弃；否则下面的步数会算成天文数字
        if (!Float.isFinite(length) || length < 1.0E-4f) {
            return;
        }

        // 步数必须有上限：长度异常大时（数据出了问题）按长度取整会让这一帧陷入长时间循环
        int steps = Math.max(1, Math.min((int) Math.ceil(length), MAX_LINE_STEPS));
        float halfThickness = Math.max(0.35f, thickness * 0.5f);

        for (int i = 1; i <= steps; i++) {
            float startX = x0 + deltaX * (i - 1) / steps;
            float startY = y0 + deltaY * (i - 1) / steps;
            float endX = x0 + deltaX * i / steps;
            float endY = y0 + deltaY * i / steps;
            float minX = Math.min(startX, endX);
            float maxX = Math.max(startX, endX);
            float minY = Math.min(startY, endY) - halfThickness;
            float maxY = Math.max(startY, endY) + halfThickness;

            if (maxX - minX < 0.4f) {
                maxX = minX + 0.4f;
            }

            floatFill(graphics, minX, minY, maxX, maxY, color);
        }
    }

    public static void circle(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            graphics.fill(centerX - halfWidth, centerY + dy, centerX + halfWidth + 1, centerY + dy + 1, color);
        }
    }

    public static String num(float value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
