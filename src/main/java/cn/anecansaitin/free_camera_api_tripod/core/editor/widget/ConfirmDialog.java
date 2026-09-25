package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 居中的询问弹窗，两种形态：
///
/// - **二次确认**：一行提示 + 「确认 / 取消」，用于会丢数据的操作（绑定路径会清空坐标关键帧等）；
/// - **多选一**：一行提示 + 若干个并列选项，用于需要用户先做选择再继续的地方（切换运动模式时问
///   「新建还是绑定已有」、绑定已有路径时问「从存档还是本地文件」）。
///
/// 弹窗自己持有按钮，屏幕只需要在最上层调用 {@link #render} 并优先派发点击，
/// 处理完再通过 {@link #finished()} 判断是否该把它关掉。
public final class ConfirmDialog {
    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 11;
    private static final int BUTTON_WIDTH = 64;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 6;
    private static final int MIN_WIDTH = 150;

    /// 多选一形态里的一个选项
    public record Choice(Component label, Runnable action) {
    }

    private final Component message;
    /// 二次确认形态：点「确认」后执行；多选一形态下为 null
    private final @Nullable Runnable onConfirm;
    private final List<Choice> choices;
    private final WidgetHost widgets = new WidgetHost();
    private UiRect rect = new UiRect(0, 0, 0, 0);
    private int screenWidth;
    private int screenHeight;
    private boolean finished;
    private boolean built;

    public ConfirmDialog(Component message, Runnable onConfirm) {
        this.message = message;
        this.onConfirm = onConfirm;
        this.choices = List.of();
    }

    /// 多选一：每个选项一个按钮，没有「取消」按钮（点弹窗外或按 Esc 同样是放弃）
    public ConfirmDialog(Component message, List<Choice> choices) {
        this.message = message;
        this.onConfirm = null;
        this.choices = new ArrayList<>(choices);
    }

    /// 按屏幕尺寸重新居中；窗口缩放后位置会跟着更新
    public void update(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        int textWidth = Draw.font().width(message.getString());
        int buttonsWidth = buttonCount() * BUTTON_WIDTH + (buttonCount() - 1) * BUTTON_GAP;
        int needed = Math.max(textWidth + PADDING * 2, buttonsWidth + PADDING * 2);
        int width = Math.max(MIN_WIDTH, Math.clamp(screenWidth - 20, MIN_WIDTH, needed));
        int height = PADDING * 2 + LINE_HEIGHT + 6 + BUTTON_HEIGHT;
        int x = Math.max(0, (screenWidth - width) / 2);
        int y = Math.max(0, (screenHeight - height) / 2);
        rect = new UiRect(x, y, width, height);

        if (!built) {
            buildButtons();
            built = true;
        }

        placeButtons();
    }

    /// 二次确认是两个按钮；多选一按选项个数来
    private int buttonCount() {
        return choices.isEmpty() ? 2 : choices.size();
    }

    private void buildButtons() {
        if (choices.isEmpty()) {
            widgets.add(new ButtonWidget(new UiRect(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT), EditorLang.t("common.cancel"),
                    () -> finished = true));
            widgets.add(new ButtonWidget(new UiRect(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT), EditorLang.t("common.confirm"),
                    this::confirm).accent(true));
            return;
        }

        for (Choice choice : choices) {
            widgets.add(new ButtonWidget(new UiRect(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT), choice.label(),
                    () -> select(choice)));
        }
    }

    /// 选了某一项：先标记结束再执行动作，动作里即使再弹一个询问框也不会被覆盖
    private void select(Choice choice) {
        finished = true;
        choice.action().run();
    }

    private void placeButtons() {
        int rowY = rect.bottom() - PADDING - BUTTON_HEIGHT;
        int total = buttonCount() * BUTTON_WIDTH + (buttonCount() - 1) * BUTTON_GAP;
        int x = rect.centerX() - total / 2;

        for (EditorWidget widget : widgets.widgets()) {
            widget.rect(new UiRect(x, rowY, BUTTON_WIDTH, BUTTON_HEIGHT));
            x += BUTTON_WIDTH + BUTTON_GAP;
        }
    }

    /// 用户点了确认：先标记结束再执行动作，动作里即使再弹一个确认框也不会被覆盖。
    ///
    /// 多选一形态没有「确认」按钮，这里只把弹窗标记为结束（选项必须由按钮给出）。
    public void confirm() {
        finished = true;

        if (onConfirm != null) {
            onConfirm.run();
        }
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
