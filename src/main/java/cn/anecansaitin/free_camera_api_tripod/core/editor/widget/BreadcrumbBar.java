package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// 路径面包屑：把当前路径按层级拆成一段段可点击的文本，段尾的小箭头可展开「同级目录」下拉，
/// 交互与 Windows 资源管理器的地址栏一致。
///
/// 组件不关心数据来自本地文件系统还是存档，段内容每帧由使用方重新提供：
/// 先 {@link #clear()}，再从根到当前目录依次 {@link #add}，最后用 {@link #layout} 指定可用区域。
/// 跳转与建目录都由使用方在回调里完成，组件自身不改动任何数据。
public final class BreadcrumbBar {
    /// 下拉列表行高，与右键菜单保持一致
    private static final int ROW_HEIGHT = 13;
    /// 段尾箭头占用的宽度
    private static final int ARROW_WIDTH = 9;
    /// 段文本左右内边距
    private static final int TEXT_PADDING = 3;
    /// 段与段之间的间隔
    private static final int GAP = 1;
    private static final int MENU_PADDING = 4;
    private static final int MENU_MIN_WIDTH = 88;
    private static final int SCROLLBAR_WIDTH = 3;
    /// 顶栏内与左右边缘保持的空隙
    private static final int EDGE = 2;
    /// 放不下时用来占位的省略号
    private static final String ELLIPSIS = "...";

    /// 一段路径。{@code siblings} 是这一段的同级候选（点箭头后列出的内容），
    /// {@code pick} 接收被选中的同级名字；{@code siblings} 为空时该段不显示下拉箭头。
    public record Crumb(String label, Runnable jump, List<String> siblings, Consumer<String> pick) {
    }

    /// 一段在栏上的位置；{@code crumb} 为 null 表示放不下时用来占位的省略号，{@code index} 为 -1
    private record Slot(@Nullable Crumb crumb, int index, int x, int width) {
        int arrowX() {
            return x + width - ARROW_WIDTH;
        }
    }

    private final List<Crumb> crumbs = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private UiRect rect = new UiRect(0, 0, 0, 0);
    private int screenWidth;
    private int screenHeight;
    /// 展开下拉的段下标（crumbs 里的下标），-1 表示没有展开
    private int openIndex = -1;
    private int menuScroll;
    private int menuX;
    private int menuY;
    private int menuWidth;
    private int menuHeight;
    private int menuContentHeight;

    /// 清空上一帧的段
    public void clear() {
        crumbs.clear();
    }

    public void add(String label, Runnable jump, List<String> siblings, Consumer<String> pick) {
        crumbs.add(new Crumb(label, jump, siblings, pick));
    }

    /// 指定可用区域与屏幕尺寸（下拉贴到屏幕下沿时需要后者）
    public void layout(UiRect rect, int screenWidth, int screenHeight) {
        this.rect = rect;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /// 是否展开了下拉：展开时应把点击优先交给本组件，避免穿透到列表
    public boolean menuOpen() {
        return openIndex >= 0;
    }

    public void closeMenu() {
        openIndex = -1;
    }

    // region 布局

    /// 计算各段位置：总宽放不下时从左侧截断，被截掉的部分用一个省略号占位，
    /// 保证当前目录那一段始终可见
    private void measure() {
        slots.clear();

        if (rect.width() <= 0 || crumbs.isEmpty()) {
            openIndex = -1;
            return;
        }

        int count = crumbs.size();
        int[] widths = new int[count];
        int available = Math.max(0, rect.width() - EDGE * 2);

        for (int i = 0; i < count; i++) {
            Crumb crumb = crumbs.get(i);
            widths[i] = Draw.font().width(crumb.label()) + TEXT_PADDING * 2
                    + (crumb.siblings().isEmpty() ? 0 : ARROW_WIDTH);
        }

        // 从最后一段往前收，确定放得下的最前一段
        int start = count;
        int used = 0;

        for (int i = count - 1; i >= 0; i--) {
            int cost = widths[i] + GAP;

            if (used + cost > available + GAP) {
                break;
            }

            used += cost;
            start = i;
        }

        boolean ellipsis = start > 0;
        int ellipsisWidth = 0;

        if (ellipsis) {
            ellipsisWidth = Draw.font().width(ELLIPSIS) + TEXT_PADDING * 2;

            // 省略号也要占位置，空间不够就继续丢掉靠前的段
            while (start < count && used + ellipsisWidth > available + GAP) {
                used -= widths[start] + GAP;
                start++;
            }

            if (start >= count) {
                // 连最后一段都放不下：只画它，不再放省略号
                start = count - 1;
                ellipsis = false;
            }
        }

        int x = rect.x() + EDGE;

        if (ellipsis) {
            slots.add(new Slot(null, -1, x, ellipsisWidth));
            x += ellipsisWidth + GAP;
        }

        for (int i = start; i < count; i++) {
            slots.add(new Slot(crumbs.get(i), i, x, widths[i]));
            x += widths[i] + GAP;
        }

        if (openIndex >= 0 && slotAt(openIndex) == null) {
            // 展开的那一段被截掉了，下拉随之收起
            openIndex = -1;
        }

        measureMenu();
    }

    /// 下拉菜单的位置与尺寸：宽度按最长一项自适应，高度超出屏幕下沿时截断并允许滚动
    private void measureMenu() {
        if (openIndex < 0) {
            return;
        }

        Slot open = slotAt(openIndex);

        if (open == null) {
            openIndex = -1;
            return;
        }

        List<String> items = open.crumb().siblings();
        menuWidth = MENU_MIN_WIDTH;

        for (String item : items) {
            menuWidth = Math.max(menuWidth, Draw.font().width(item) + MENU_PADDING * 2 + SCROLLBAR_WIDTH + 6);
        }

        menuContentHeight = items.size() * ROW_HEIGHT + MENU_PADDING * 2;
        menuWidth = Math.min(menuWidth, Math.max(MENU_MIN_WIDTH, screenWidth - 4));
        menuX = Mth.clamp(open.x(), 0, Math.max(0, screenWidth - menuWidth - 2));
        menuY = rect.bottom();

        int minHeight = ROW_HEIGHT + MENU_PADDING * 2;
        int maxHeight = Math.max(minHeight, screenHeight - menuY - 2);
        menuHeight = Math.min(menuContentHeight, maxHeight);
        menuScroll = Mth.clamp(menuScroll, 0, Math.max(0, menuContentHeight - menuHeight));
    }

    private @Nullable Slot slotAt(int index) {
        for (Slot slot : slots) {
            if (slot.index() == index) {
                return slot;
            }
        }

        return null;
    }

    private UiRect menuRect() {
        return new UiRect(menuX, menuY, menuWidth, menuHeight);
    }

    /// 鼠标是否落在某一段上
    private boolean inside(Slot slot, double mouseX, double mouseY) {
        return mouseX >= slot.x() && mouseX < slot.x() + slot.width()
                && mouseY >= rect.y() && mouseY < rect.bottom();
    }

    // endregion

    // region 绘制

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        measure();

        for (Slot slot : slots) {
            if (slot.crumb() == null) {
                Draw.text(graphics, ELLIPSIS, slot.x() + TEXT_PADDING, rect.centerY() - 4, Draw.TEXT_DISABLED);
                continue;
            }

            boolean open = slot.index() == openIndex;
            boolean hovered = inside(slot, mouseX, mouseY);

            if (hovered || open) {
                Draw.canvas(graphics, new UiRect(slot.x(), rect.y() + 1, slot.width(), Math.max(1, rect.height() - 2)),
                        Draw.BUTTON_BG_HOVER);
            }

            int color = hovered || open ? Draw.TEXT : Draw.TEXT_DIM;
            Draw.textEllipsized(graphics, slot.crumb().label(), slot.x() + TEXT_PADDING, rect.centerY() - 4,
                    Math.max(1, slot.width() - TEXT_PADDING * 2), color);

            if (!slot.crumb().siblings().isEmpty()) {
                Draw.text(graphics, Icons.COLLAPSE, slot.arrowX(), rect.centerY() - 4, color);
            }
        }

        if (openIndex >= 0) {
            renderMenu(graphics, mouseX, mouseY);
        }
    }

    /// 同级目录下拉
    private void renderMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Slot open = slotAt(openIndex);

        if (open == null) {
            return;
        }

        List<String> items = open.crumb().siblings();
        UiRect menu = menuRect();
        Draw.canvas(graphics, menu, Draw.MENU_BG);
        Draw.border(graphics, menu, Draw.BORDER);

        graphics.enableScissor(menu.x() + 1, menu.y() + 1, menu.right() - 1, menu.bottom() - 1);

        for (int i = 0; i < items.size(); i++) {
            int rowY = menu.y() + MENU_PADDING + i * ROW_HEIGHT - menuScroll;

            if (rowY + ROW_HEIGHT <= menu.y() || rowY >= menu.bottom()) {
                continue;
            }

            boolean hovered = mouseX >= menu.x() + 1 && mouseX < menu.right() - 1
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (hovered) {
                Draw.canvas(graphics, new UiRect(menu.x() + 1, rowY, Math.max(1, menu.width() - 2), ROW_HEIGHT),
                        Draw.BUTTON_BG_HOVER);
            }

            Draw.textEllipsized(graphics, items.get(i), menu.x() + MENU_PADDING + 2, rowY + (ROW_HEIGHT - 8) / 2,
                    Math.max(1, menu.width() - MENU_PADDING * 2 - SCROLLBAR_WIDTH - 4), Draw.TEXT);
        }

        graphics.disableScissor();

        if (menuContentHeight <= menuHeight) {
            return;
        }

        int trackX = menu.right() - SCROLLBAR_WIDTH - 1;
        int trackTop = menu.y() + 2;
        int trackHeight = Math.max(1, menu.height() - 4);
        int maxScroll = Math.max(1, menuContentHeight - menuHeight);
        int thumbHeight = Mth.clamp(Math.round((float) trackHeight * menuHeight / menuContentHeight), 8, trackHeight);
        int thumbTop = trackTop + Math.round((float) (trackHeight - thumbHeight) * menuScroll / maxScroll);
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + trackHeight, Draw.GRID);
        graphics.fill(trackX, thumbTop, trackX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, Draw.BORDER);
    }

    // endregion

    // region 输入

    /// 命中段或下拉时返回 true；点在栏外返回 false，由调用方继续处理
    public boolean mouseClicked(MouseButtonEvent event) {
        measure();

        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // 其它键先收起下拉
            if (openIndex >= 0) {
                closeMenu();
                return true;
            }

            return false;
        }

        if (openIndex >= 0 && menuRect().contains(event.x(), event.y())) {
            pickSibling(event);
            return true;
        }

        for (Slot slot : slots) {
            if (slot.crumb() == null || !inside(slot, event.x(), event.y())) {
                continue;
            }

            if (!slot.crumb().siblings().isEmpty() && event.x() >= slot.arrowX()) {
                // 点箭头：展开 / 收起同级目录
                openIndex = openIndex == slot.index() ? -1 : slot.index();
                menuScroll = 0;
                return true;
            }

            closeMenu();
            slot.crumb().jump().run();
            return true;
        }

        if (openIndex >= 0) {
            // 点在栏内空白处：只收起下拉；点在栏外则交给调用方继续处理（例如列表点击）
            boolean inBar = rect.contains(event.x(), event.y());
            closeMenu();
            return inBar;
        }

        return false;
    }

    /// 点中下拉里的一项：收起菜单并回调使用方跳转
    private void pickSibling(MouseButtonEvent event) {
        Slot open = slotAt(openIndex);

        if (open == null) {
            closeMenu();
            return;
        }

        List<String> items = open.crumb().siblings();
        int index = (int) ((event.y() - menuY - MENU_PADDING + menuScroll) / ROW_HEIGHT);

        if (index < 0 || index >= items.size()) {
            return;
        }

        Consumer<String> pick = open.crumb().pick();
        String name = items.get(index);
        closeMenu();
        pick.accept(name);
    }

    /// 鼠标在下拉里滚动；其它位置返回 false
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (openIndex < 0 || !menuRect().contains(mouseX, mouseY)) {
            return false;
        }

        int maxScroll = Math.max(0, menuContentHeight - menuHeight);

        if (maxScroll == 0) {
            return true;
        }

        int delta = (int) (scrollY * ROW_HEIGHT * 2);

        if (delta == 0 && scrollY != 0) {
            delta = scrollY > 0 ? ROW_HEIGHT : -ROW_HEIGHT;
        }

        menuScroll = Mth.clamp(menuScroll - delta, 0, maxScroll);
        return true;
    }

    // endregion
}
