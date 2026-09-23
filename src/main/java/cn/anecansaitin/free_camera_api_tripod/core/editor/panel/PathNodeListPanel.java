package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathMode;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.Selected;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// 路径节点列表面板：列出全部路径节点并支持选中，顶部是取点与删除两个操作。
///
/// 只负责「选哪个节点」，节点参数的编辑在 {@link PathNodeDetailPanel} 里。
public class PathNodeListPanel extends EditorPanel {
    public static final String ID = "path_nodes";

    private static final int ROW_HEIGHT = 15;
    private static final int FIELD_HEIGHT = 13;
    private static final int ACTION_HEIGHT = 17;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_MARGIN = 4;

    private final EditorContext context;
    private final List<LabelDraw> labels = new ArrayList<>();
    private @Nullable String lastRevision;
    private int scrollY;
    private int totalHeight;

    /// maxWidth 大于 0 时超出宽度会被省略号截断
    private record LabelDraw(Component text, int x, int y, int color, int maxWidth) {
    }

    public PathNodeListPanel(EditorContext context) {
        super(ID, EditorLang.t("path_editor.nodes"));
        this.context = context;
    }

    // region 几何

    /// 节点行区域：顶部动作行之下
    private UiRect rowsRect(UiRect content) {
        int top = content.y() + ACTION_HEIGHT * 2;
        return new UiRect(content.x(), top, content.width(), Math.max(0, content.bottom() - top));
    }

    // endregion

    @Override
    protected void layoutWidgets(UiRect content) {
        rebuild(content);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, UiRect content, int mouseX, int mouseY) {
        Draw.canvas(graphics, content, Draw.CANVAS_BG);

        String revision = revision();

        if (!revision.equals(lastRevision)) {
            rebuild(content);
        }

        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());

        for (LabelDraw label : labels) {
            if (label.maxWidth() > 0) {
                Draw.textEllipsized(graphics, label.text().getString(), label.x(), label.y(), label.maxWidth(), label.color());
            } else {
                Draw.text(graphics, label.text(), label.x(), label.y(), label.color());
            }
        }

        graphics.disableScissor();
        renderScrollbar(graphics, rowsRect(content));
    }

    /// 选中项、节点模式与数量变化时重建列表；坐标变化通过刷新器同步
    private String revision() {
        Path path = context.editor().path();
        Selected selected = context.editor().selectedPathNode();
        StringBuilder builder = new StringBuilder();
        builder.append(selected.index()).append('/').append(selected.type()).append('|').append(path.size());

        for (int i = 0; i < path.size(); i++) {
            builder.append('|').append(path.node(i).pathMode());
        }

        return builder.toString();
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        labels.clear();
        // 控件同样要清空：否则每次重建都会再叠一层行按钮，后加的行永远点不中
        widgets.clear();

        Path path = context.editor().path();
        int selectedIndex = context.editor().selectedPathNode().index();
        UiRect rows = rowsRect(content);
        int rowWidth = Math.max(1, rows.width() - 6 - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN);
        totalHeight = path.size() * ROW_HEIGHT + 4;
        // 节点被删除后总高度会变小，滚动位置需要跟着回收，否则列表会停在空白处
        scrollY = Mth.clamp(scrollY, 0, Math.max(0, totalHeight - rows.height()));

        buildActions(content, rowWidth);

        if (path.size() == 0) {
            labels.add(new LabelDraw(EditorLang.t("inspector.path.empty"), rows.x() + 5, rows.y() + 3, Draw.TEXT_DISABLED,
                    Math.max(8, rows.width() - 10)));
        }

        for (int i = 0; i < path.size(); i++) {
            int index = i;
            int rowY = rows.y() + 2 + index * ROW_HEIGHT - scrollY;
            ButtonWidget row = new ButtonWidget(new UiRect(rows.x() + 3, rowY, rowWidth, FIELD_HEIGHT),
                    EditorLang.t("path_editor.node_row", index, modeLabel(path.node(index).pathMode())), () -> selectNode(index));
            row.toggled(index == selectedIndex);
            widgets.add(row);
        }
    }

    /// 顶部动作行：第一行是添加路径点与删除选中节点，第二行是调整节点顺序
    private void buildActions(UiRect content, int width) {
        int y = content.y() + 2;
        int gap = 3;
        int removeWidth = Math.min(64, Math.max(28, width / 3));
        int addWidth = Math.max(1, width - removeWidth - gap);

        ButtonWidget add = new ButtonWidget(new UiRect(content.x() + 3, y, addWidth, FIELD_HEIGHT),
                EditorLang.t("path_editor.add_node"), this::addNodeFromCamera);
        add.tooltip(EditorLang.t("inspector.path.add_from_camera"));
        add.accent(true);
        widgets.add(add);

        ButtonWidget remove = new ButtonWidget(new UiRect(content.x() + 3 + addWidth + gap, y, removeWidth, FIELD_HEIGHT),
                EditorLang.t("inspector.path.remove"), this::removeSelectedNode);
        remove.enabled(context.editor().selectedPathNode().index() >= 0);
        widgets.add(remove);

        buildOrderActions(content, y + FIELD_HEIGHT + 2, width);
    }

    /// 排序行：把选中节点在路径里上移 / 下移一位
    private void buildOrderActions(UiRect content, int y, int width) {
        int selected = context.editor().selectedPathNode().index();
        int size = context.editor().path().size();
        int cell = Math.max(1, (width - 2) / 2);

        ButtonWidget up = new ButtonWidget(new UiRect(content.x() + 3, y, cell, FIELD_HEIGHT),
                EditorLang.t("inspector.path.move_up"), () -> moveSelectedNode(-1));
        up.tooltip(EditorLang.t("inspector.path.move_up.tip"));
        up.enabled(selected > 0 && selected < size);
        widgets.add(up);

        ButtonWidget down = new ButtonWidget(new UiRect(content.x() + 3 + cell + 2, y, cell, FIELD_HEIGHT),
                EditorLang.t("inspector.path.move_down"), () -> moveSelectedNode(1));
        down.tooltip(EditorLang.t("inspector.path.move_down.tip"));
        down.enabled(selected >= 0 && selected < size - 1);
        widgets.add(down);
    }

    /// 与相邻节点交换位置；顺序变了弧长表会整体重建，因此要通知渲染重新采样
    private void moveSelectedNode(int offset) {
        if (!context.editor().movePathNode(context.editor().selectedPathNode().index(), offset)) {
            return;
        }

        context.notify(EditorLang.t("notify.path_node_moved"));
    }

    private void selectNode(int index) {
        context.editor().selectPathNode(new Selected(index, Selected.Type.NODE));
    }

    private void addNodeFromCamera() {
        context.recordPathNode();
    }

    private void removeSelectedNode() {
        if (context.editor().removePathNode(context.editor().selectedPathNode().index())) {
            context.notify(EditorLang.t("notify.path_node_removed"));
        }
    }

    private Component modeLabel(PathMode mode) {
        return EditorLang.t("mode.path." + mode.name().toLowerCase(Locale.ROOT));
    }

    /// 节点超出可视高度时在右侧绘制滚动条指示器
    private void renderScrollbar(GuiGraphicsExtractor graphics, UiRect rows) {
        int maxScroll = Math.max(0, totalHeight - rows.height());

        if (maxScroll <= 0 || rows.height() <= 0) {
            return;
        }

        int trackX = rows.right() - SCROLLBAR_MARGIN;
        int trackTop = rows.y() + 1;
        int trackHeight = Math.max(1, rows.height() - 2);
        int thumbHeight = Mth.clamp(Math.round((float) trackHeight * rows.height() / totalHeight), 8, trackHeight);
        int thumbTop = trackTop + Math.round((float) (trackHeight - thumbHeight) * scrollY / maxScroll);
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + trackHeight, Draw.GRID);
        graphics.fill(trackX, thumbTop, trackX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, Draw.BORDER);
    }

    @Override
    protected boolean contentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiRect content = contentRect();
        UiRect rows = rowsRect(content);
        int maxScroll = Math.max(0, totalHeight - rows.height());
        int delta = (int) (scrollY * ROW_HEIGHT * 1.5);

        if (delta == 0 && scrollY != 0) {
            delta = scrollY > 0 ? ROW_HEIGHT : -ROW_HEIGHT;
        }

        int updated = Mth.clamp(this.scrollY - delta, 0, maxScroll);

        if (updated != this.scrollY) {
            this.scrollY = updated;
            rebuild(content);
        }

        return true;
    }
}
