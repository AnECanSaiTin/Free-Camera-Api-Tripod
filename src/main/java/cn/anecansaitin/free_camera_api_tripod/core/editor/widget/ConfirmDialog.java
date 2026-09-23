package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/// 二次确认弹窗：居中的一行提示 + 「确认 / 取消」两个按钮。
///
/// 会丢掉数据的操作（例如绑定路径会清空坐标关键帧）在执行前都要过一遍它。
/// 弹窗自己持有两个按钮，屏幕只需要在最上层调用 {@link #render} 并优先派发点击，
/// 处理完再通过 {@link #finished()} 判断是否该把它关掉。
public final class ConfirmDialog {
    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 11;
    private static final int BUTTON_WIDTH = 64;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 6;
    private static final int MIN_WIDTH = 150;

    private final Component message;
    private final Runnable onConfirm;
    private final WidgetHost widgets = new WidgetHost();
    private UiRect rect = new UiRect(0, 0, 0, 0);
    private int screenWidth;
    private int screenHeight;
    private boolean finished;
    private boolean built;

    public ConfirmDialog(Component message, Runnable onConfirm) {
        this.message = message;
        this.onConfirm = onConfirm;
    }

    /// 按屏幕尺寸重新居中；窗口缩放后位置会跟着更新
    public void update(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        int textWidth = Draw.font().width(message.getString());
        int width = Math.max(MIN_WIDTH, Math.min(Math.max(MIN_WIDTH, screenWidth - 20), textWidth + PADDING * 2));
        int height = PADDING * 2 + LINE_HEIGHT + 6 + BUTTON_HEIGHT;
        int x = Math.max(0, (screenWidth - width) / 2);
        int y = Math.max(0, (screenHeight - height) / 2);
        rect = new UiRect(x, y, width, height);

        if (!built) {
            widgets.add(new ButtonWidget(new UiRect(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT), EditorLang.t("common.cancel"),
                    () -> finished = true));
            widgets.add(new ButtonWidget(new UiRect(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT), EditorLang.t("common.confirm"),
                    this::confirm).accent(true));
            built = true;
        }

        placeButtons();
    }

    private void placeButtons() {
        int rowY = rect.bottom() - PADDING - BUTTON_HEIGHT;
        int total = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int x = rect.centerX() - total / 2;

        for (EditorWidget widget : widgets.widgets()) {
            widget.rect(new UiRect(x, rowY, BUTTON_WIDTH, BUTTON_HEIGHT));
            x += BUTTON_WIDTH + BUTTON_GAP;
        }
    }

    /// 用户点了确认：先标记结束再执行动作，动作里即使再弹一个确认框也不会被覆盖。
    ///
    /// 外部界面（例如附属 mod 的 Modern UI 界面）没有这个弹窗，它会自己画提示并直接调用本方法。
    public void confirm() {
        finished = true;
        onConfirm.run();
    }

    /// 弹窗正文
    public Component message() {
        return message;
    }

    public boolean finished() {
        return finished;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 整屏压暗，明确此时只能先处理这个弹窗
        graphics.fill(0, 0, screenWidth, screenHeight, 0x80000000);
        Draw.canvas(graphics, rect, Draw.FLOATING_BG);
        Draw.border(graphics, rect, Draw.BORDER);
        Draw.textEllipsized(graphics, message.getString(), rect.x() + PADDING, rect.y() + PADDING,
                rect.width() - PADDING * 2, Draw.TEXT);
        widgets.render(graphics, mouseX, mouseY);
    }

    /// 消耗弹窗范围内的点击；点在弹窗外相当于取消
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (rect.contains(event.x(), event.y())) {
            widgets.mouseClicked(event, doubleClick);
        } else {
            finished = true;
        }

        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return widgets.mouseReleased(event);
    }
}
