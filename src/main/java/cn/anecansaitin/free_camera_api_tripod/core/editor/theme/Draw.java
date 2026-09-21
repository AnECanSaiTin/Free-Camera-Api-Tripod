package cn.anecansaitin.free_camera_api_tripod.core.editor.theme;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.render.FloatFill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;

import java.util.Locale;

/// 编辑器配色与绘制辅助。整体为深色编辑器风格，面板半透明以便透过视口看到世界。
public final class Draw {
    public static final int TOOLBAR_BG = 0xF01B1B21;
    public static final int PANEL_BG = 0xE6141418;
    public static final int PANEL_HEADER_BG = 0xF024242C;
    public static final int BORDER = 0xFF3A3A44;
    public static final int SPLITTER = 0xFF16161A;
    public static final int SPLITTER_HOVER = 0xFF4EA1FF;
    public static final int CANVAS_BG = 0x99101014;
    public static final int GRID = 0xFF262630;
    public static final int GRID_STRONG = 0xFF3A3A48;

    public static final int TEXT = 0xFFE2E2EA;
    public static final int TEXT_DIM = 0xFF9A9AA8;
    public static final int TEXT_DISABLED = 0xFF62626E;

    public static final int ACCENT = 0xFF4EA1FF;
    public static final int SELECTED = 0xFFFFB74D;
    public static final int PLAYHEAD = 0xFFFF5252;
    public static final int PATH_NODE = 0xFF7CFC9A;

    public static final int BUTTON_BG = 0xFF2A2A33;
    public static final int BUTTON_BG_HOVER = 0xFF3A3A48;
    public static final int BUTTON_BG_ACTIVE = 0xFF2F5F9F;
    public static final int FIELD_BG = 0xFF121216;
    public static final int ROW_ALT = 0x12FFFFFF;
    public static final int ROW_SELECTED = 0x3A4EA1FF;

    private Draw() {
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
        graphics.text(font(), text, x, y, color);
    }

    public static void text(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font(), text, x, y, color);
    }

    public static void textCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color) {
        graphics.centeredText(font(), text, centerX, y, color);
    }

    public static void textCentered(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color) {
        graphics.centeredText(font(), text, centerX, y, color);
    }

    public static void textRight(GuiGraphicsExtractor graphics, String text, int rightX, int y, int color) {
        graphics.text(font(), text, rightX - font().width(text), y, color);
    }

    public static void textEllipsized(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        String value = text;

        if (font().width(value) > maxWidth) {
            value = font().plainSubstrByWidth(value, Math.max(0, maxWidth - font().width("..."))) + "...";
        }

        graphics.text(font(), value, x, y, color);
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

        if (length < 1.0E-4f) {
            return;
        }

        int steps = Math.max(1, (int) Math.ceil(length));
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
