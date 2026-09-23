package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 路径信息面板：承载整条路径的信息——名称（可改）、节点数量、总长度与距离口径。
///
/// 路径名称原先放在工具栏上，现在统一收进这里，工具栏只留文件 / 编辑 / 视图三个标准菜单。
public class PathInfoPanel extends EditorPanel {
    public static final String ID = "path_info";

    private static final int ROW_HEIGHT = 15;
    private static final int FIELD_HEIGHT = 13;
    private static final int LABEL_WIDTH = 56;

    private final EditorContext context;
    private final List<LabelDraw> labels = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private @Nullable String lastRevision;

    /// maxWidth 大于 0 时超出宽度会被省略号截断
    private record LabelDraw(Component text, int x, int y, int color, int maxWidth) {
    }

    public PathInfoPanel(EditorContext context) {
        super(ID, EditorLang.t("panel.path_info"));
        this.context = context;
    }

    @Override
    protected void layoutWidgets(UiRect content) {
        rebuild(content);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, UiRect content, int mouseX, int mouseY) {
        Draw.canvas(graphics, content, Draw.CANVAS_BG);

        if (!revision().equals(lastRevision)) {
            rebuild(content);
        }

        for (Runnable refresher : refreshers) {
            refresher.run();
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
    }

    /// 路径规模变化时重建；名称不参与——它由输入框自己维护，参与进来会在输入时重建控件
    private String revision() {
        Path path = context.editor().path();
        return path.size() + "|" + Draw.num((float) path.totalLength(), 2);
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        labels.clear();
        // 控件同样要清空，避免重建时叠层导致重叠与点不中
        widgets.clear();
        refreshers.clear();

        int x = content.x() + 5;
        int right = content.right() - 5;
        int width = Math.max(20, right - x - LABEL_WIDTH);
        int y = content.y() + 4;
        Path path = context.editor().path();

        labels.add(new LabelDraw(EditorLang.t("path_editor.name"), x, y + 3, Draw.TEXT_DIM, -1));
        TextFieldWidget name = new TextFieldWidget(new UiRect(x + LABEL_WIDTH, y + 1, width, FIELD_HEIGHT),
                path.name(), value -> path.name(value));
        widgets.add(name);
        refreshers.add(() -> name.value(path.name()));
        y += ROW_HEIGHT;

        y = textRow(x, y, EditorLang.t("inspector.path.count"), Component.literal(String.valueOf(path.size())), right);
        textRow(x, y, EditorLang.t("inspector.path.length"),
                Component.literal(Draw.num((float) path.totalLength(), 2)), right);
    }

    // region 行构建

    private int textRow(int x, int y, Component label, Component value, int right) {
        labels.add(new LabelDraw(label, x, y + 3, Draw.TEXT_DIM, -1));
        labels.add(new LabelDraw(value, x + LABEL_WIDTH, y + 3, Draw.TEXT, Math.max(8, right - x - LABEL_WIDTH)));
        return y + ROW_HEIGHT;
    }

    // endregion
}
