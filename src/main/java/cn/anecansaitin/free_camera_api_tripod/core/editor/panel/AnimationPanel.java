package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// 动画面板：展示动画整体信息，支持修改动画名称与切换运动模式。
public class AnimationPanel extends EditorPanel {
    public static final String ID = "animation";

    private static final int ROW_HEIGHT = 15;
    private static final int FIELD_HEIGHT = 13;
    private static final int LABEL_WIDTH = 62;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_MARGIN = 4;

    private final EditorContext context;
    private final List<LabelDraw> labels = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private @Nullable String lastRevision;
    private int scrollY;
    private int totalHeight;
    private int contentRight;

    /// maxWidth 大于 0 时超出宽度会被省略号截断
    private record LabelDraw(Component text, int x, int y, int color, int maxWidth) {
    }

    public AnimationPanel(EditorContext context) {
        super(ID, EditorLang.t("panel.animation"));
        this.context = context;
    }

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
        renderScrollbar(graphics, content);
    }

    /// 时长与轨道/路径摘要变化时重建标签；动画名由输入框的刷新器同步，不参与重建
    private String revision() {
        return Draw.num(context.duration(), 3) + '|' + context.info().trackSummary().getString() + '|'
                + context.info().pathSummary().getString() + '|' + context.animation().motionMode();
    }

    private void rebuild(UiRect content) {
        lastRevision = revision();
        labels.clear();
        widgets.clear();
        refreshers.clear();

        int x = content.x() + 5;
        contentRight = content.right() - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        int fieldWidth = Math.max(40, contentRight - x - LABEL_WIDTH);
        int y = content.y() + 4 - scrollY;

        y = section(x, y, EditorLang.t("inspector.section.animation"));
        y = nameRow(x, y, fieldWidth);
        y = motionModeRow(x, y, fieldWidth);
        y = textRow(x, y, EditorLang.t("inspector.animation.duration"), Component.literal(Draw.num(context.duration(), 2) + "s"));
        y = textRow(x, y, EditorLang.t("inspector.animation.tracks"), context.info().trackSummary());
        y = textRow(x, y, EditorLang.t("inspector.animation.path"), context.info().pathSummary());

        totalHeight = y + scrollY - content.y() + 8;
    }

    // region 行构建

    /// 动画名可编辑：内容变化时写回动画数据模型
    private int nameRow(int x, int y, int width) {
        labels.add(new LabelDraw(EditorLang.t("inspector.animation.name"), x, y + 3, Draw.TEXT_DIM, -1));
        TextFieldWidget field = new TextFieldWidget(new UiRect(x + LABEL_WIDTH, y + 1, width, FIELD_HEIGHT),
                context.animation().name(), name -> context.animation().name(name));
        widgets.add(field);
        refreshers.add(() -> field.value(context.animation().name()));
        return y + ROW_HEIGHT;
    }

    /// 运动模式：路径模式与直接坐标模式互斥，切换会清掉另一种模式的通道
    private int motionModeRow(int x, int y, int width) {
        labels.add(new LabelDraw(EditorLang.t("inspector.animation.motion_mode"), x, y + 3, Draw.TEXT_DIM, -1));
        CameraAnimation.MotionMode[] values = CameraAnimation.MotionMode.values();
        int cell = Math.max(1, (width - (values.length - 1) * 2) / values.length);

        for (int i = 0; i < values.length; i++) {
            CameraAnimation.MotionMode value = values[i];
            ButtonWidget button = new ButtonWidget(new UiRect(x + LABEL_WIDTH + i * (cell + 2), y + 1, cell, FIELD_HEIGHT),
                    EditorLang.t("mode.motion." + value.name().toLowerCase(Locale.ROOT)), () -> switchMotionMode(value));
            widgets.add(button);
            refreshers.add(() -> button.toggled(context.animation().motionMode() == value));
        }

        return y + ROW_HEIGHT;
    }

    /// 切换运动模式。
    ///
    /// 两种模式都会丢掉另一种模式的位置关键帧，因此一律先二次确认；切到路径模式还要先选一条
    /// 已保存的路径（文件管理界面），也就是「先选路径 → 再二次确认」两步。
    private void switchMotionMode(CameraAnimation.MotionMode mode) {
        if (context.animation().motionMode() == mode) {
            return;
        }

        if (mode == CameraAnimation.MotionMode.PATH) {
            context.switchToPathMode();
            return;
        }

        context.switchToCoordinateMode();
    }

    private int section(int x, int y, Component title) {
        labels.add(new LabelDraw(title, x, y + 2, Draw.ACCENT, -1));
        return y + ROW_HEIGHT;
    }

    private int textRow(int x, int y, Component label, Component value) {
        labels.add(new LabelDraw(label, x, y + 3, Draw.TEXT_DIM, -1));
        labels.add(new LabelDraw(value, x + LABEL_WIDTH, y + 3, Draw.TEXT, Math.max(8, contentRight - LABEL_WIDTH - x)));
        return y + ROW_HEIGHT;
    }

    /// 内容超出高度时在右侧绘制滚动条指示器：轨道底色 + 反映当前滚动位置的滑块
    private void renderScrollbar(GuiGraphicsExtractor graphics, UiRect content) {
        int maxScroll = Math.max(0, totalHeight - content.height());

        if (maxScroll <= 0) {
            return;
        }

        int trackX = content.right() - SCROLLBAR_MARGIN;
        int trackTop = content.y() + 1;
        int trackHeight = Math.max(1, content.height() - 2);
        int thumbHeight = Mth.clamp(Math.round((float) trackHeight * content.height() / totalHeight), 8, trackHeight);
        int thumbTop = trackTop + Math.round((float) (trackHeight - thumbHeight) * scrollY / maxScroll);
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + trackHeight, Draw.GRID);
        graphics.fill(trackX, thumbTop, trackX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, Draw.BORDER);
    }

    // endregion

    @Override
    protected boolean contentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiRect content = contentRect();
        int maxScroll = Math.max(0, totalHeight - content.height());
        int delta = (int) (scrollY * ROW_HEIGHT * 1.5);

        if (delta == 0 && scrollY != 0) {
            delta = scrollY > 0 ? ROW_HEIGHT : -ROW_HEIGHT;
        }

        this.scrollY = Mth.clamp(this.scrollY - delta, 0, maxScroll);
        rebuild(content);
        return true;
    }
}
