package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.core.animation.AnimationChannelRegistry;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 时间轴面板：标尺、轨道行、关键帧增删改拖拽、播放头拖拽与缩放平移。
///
/// 同一分组的通道（如旋转的 X/Y/Z）合并成一个可折叠的分组行，折叠后只留一行概览。
public class TimelinePanel extends EditorPanel {
    private static final int TRACK_COLUMN_WIDTH = 132;
    private static final int RULER_HEIGHT = 14;
    private static final int ROW_HEIGHT = 16;
    private static final int KEY_RADIUS = 3;
    private static final int KEY_GRAB_RADIUS = 5;
    private static final int KEY_DELETE = 261;

    private final EditorContext context;
    private double scrollY;

    private enum Drag {
        NONE,
        PLAYHEAD,
        KEY
    }

    private Drag drag = Drag.NONE;
    private int dragRow = -1;
    private int dragKey = -1;
    /// 已折叠的分组 id
    private final Set<String> collapsedGroups = new HashSet<>();

    public TimelinePanel(EditorContext context) {
        super("timeline", EditorLang.t("panel.timeline"));
        this.context = context;
    }

    // region 几何

    private UiRect laneRect(UiRect content) {
        return new UiRect(content.x() + TRACK_COLUMN_WIDTH, content.y(), Math.max(0, content.width() - TRACK_COLUMN_WIDTH), content.height());
    }

    private int rowsTop(UiRect content) {
        return content.y() + RULER_HEIGHT;
    }

    private int rowY(UiRect content, int row) {
        return rowsTop(content) + row * ROW_HEIGHT - (int) scrollY;
    }

    private int timeToX(UiRect content, float time) {
        return Math.round(laneRect(content).x() + (time - context.viewStartTime()) * context.pixelsPerSecond());
    }

    private float xToTime(UiRect content, double x) {
        return context.viewStartTime() + (float) (x - laneRect(content).x()) / context.pixelsPerSecond();
    }

    private List<AnimationTrack> tracks() {
        return context.animation().tracks();
    }

    /// 时间轴上的一行：分组头，或一条具体轨道
    private sealed interface Row permits GroupRow, TrackRow {
    }

    /// 分组头；组内轨道在折叠时不再单独占行
    private static final class GroupRow implements Row {
        private final String id;
        private final Component label;
        private final List<AnimationTrack> members = new ArrayList<>();

        private GroupRow(String id, Component label) {
            this.id = id;
            this.label = label;
        }
    }

    private record TrackRow(AnimationTrack track) implements Row {
    }

    /// 当前可见的行：分组头始终显示，成员轨道在被折叠时隐藏
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        Map<String, GroupRow> groups = new LinkedHashMap<>();

        for (AnimationTrack track : tracks()) {
            String groupId = AnimationChannelRegistry.group(track.id());

            if (groupId == null) {
                rows.add(new TrackRow(track));
                continue;
            }

            GroupRow group = groups.get(groupId);

            if (group == null) {
                group = new GroupRow(groupId, AnimationChannelRegistry.groupLabel(groupId));
                groups.put(groupId, group);
                rows.add(group);
            }

            group.members.add(track);

            if (!collapsedGroups.contains(groupId)) {
                rows.add(new TrackRow(track));
            }
        }

        return rows;
    }

    private int rowIndexAt(UiRect content, double mouseY) {
        double local = mouseY - rowsTop(content) + scrollY;

        if (local < 0) {
            return -1;
        }

        int index = (int) (local / ROW_HEIGHT);
        return index >= 0 && index < rows().size() ? index : -1;
    }

    /// 关键帧命中检测；分组头行没有可操作的关键帧
    private int keyIndexAt(UiRect content, Row row, int index, double mouseX, double mouseY) {
        if (!(row instanceof TrackRow trackRow)) {
            return -1;
        }

        AnimationTrack track = trackRow.track();
        int centerY = rowY(content, index) + ROW_HEIGHT / 2;

        if (Math.abs(mouseY - centerY) > KEY_GRAB_RADIUS) {
            return -1;
        }

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < track.keyCount(); i++) {
            TrackKey key = track.key(i);

            if (key == null) {
                continue;
            }

            int distance = (int) Math.abs(mouseX - timeToX(content, key.time()));

            if (distance <= KEY_GRAB_RADIUS && distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }

        return best;
    }

    // endregion

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, UiRect content, int mouseX, int mouseY) {
        Draw.canvas(graphics, content, Draw.CANVAS_BG);
        UiRect lane = laneRect(content);
        List<Row> rows = rows();

        // 轨道名称列
        graphics.fill(content.x(), content.y(), content.x() + TRACK_COLUMN_WIDTH - 1, content.bottom(), 0x66000000);
        Draw.text(graphics, EditorLang.t("timeline.tracks"), content.x() + 5, content.y() + 3, Draw.TEXT_DIM);

        // 标尺
        graphics.fill(lane.x(), content.y(), lane.right(), content.y() + RULER_HEIGHT - 1, Draw.PANEL_HEADER_BG);
        renderRuler(graphics, content, lane);

        Draw.hLine(graphics, content.x(), content.right(), content.y() + RULER_HEIGHT - 1, Draw.BORDER);
        Draw.vLine(graphics, content.x() + TRACK_COLUMN_WIDTH - 1, content.y(), content.bottom(), Draw.BORDER);

        // 超出动画时长的区域压暗
        int endX = timeToX(content, context.duration());

        if (endX < lane.right()) {
            graphics.fill(Math.max(endX, lane.x()), rowsTop(content), lane.right(), content.bottom(), 0x50000000);
        }

        if (rows.isEmpty()) {
            Draw.text(graphics, EditorLang.t("timeline.empty"), content.x() + 6, rowsTop(content) + 6, Draw.TEXT_DISABLED);
            renderPlayhead(graphics, content, lane);
            return;
        }

        // 轨道行
        graphics.enableScissor(content.x(), rowsTop(content), content.right(), content.bottom());

        int hoveredRow = -1;
        int hoveredKey = -1;

        if (lane.contains(mouseX, mouseY) && mouseY >= rowsTop(content)) {
            hoveredRow = rowIndexAt(content, mouseY);

            if (hoveredRow >= 0) {
                hoveredKey = keyIndexAt(content, rows.get(hoveredRow), hoveredRow, mouseX, mouseY);
            }
        }

        for (int row = 0; row < rows.size(); row++) {
            int y = rowY(content, row);

            if (y + ROW_HEIGHT <= rowsTop(content) || y >= content.bottom()) {
                continue;
            }

            renderRow(graphics, content, lane, rows.get(row), row, y, hoveredRow == row ? hoveredKey : -1);
        }

        graphics.disableScissor();
        renderPlayhead(graphics, content, lane);
    }

    private void renderRow(GuiGraphicsExtractor graphics, UiRect content, UiRect lane, Row row, int index, int y, int hoveredKey) {
        switch (row) {
            case GroupRow group -> renderGroupRow(graphics, content, lane, group, y);
            case TrackRow trackRow -> renderTrackRow(graphics, content, lane, trackRow.track(), index, y, hoveredKey);
        }
    }

    /// 分组头：折叠箭头、组名与成员数；时间轴区用成员各自的颜色画出关键帧概览
    private void renderGroupRow(GuiGraphicsExtractor graphics, UiRect content, UiRect lane, GroupRow group, int y) {
        boolean collapsed = collapsedGroups.contains(group.id);
        int centerY = y + ROW_HEIGHT / 2;

        graphics.fill(content.x(), y, lane.right(), y + ROW_HEIGHT, 0x50000000);
        Draw.text(graphics, collapsed ? Icons.EXPAND : Icons.COLLAPSE, content.x() + 4, centerY - 4, Draw.TEXT_DIM);
        Draw.textEllipsized(graphics, group.label.getString(), content.x() + 14, centerY - 4, TRACK_COLUMN_WIDTH - 22, Draw.TEXT);
        Draw.textRight(graphics, String.valueOf(group.members.size()), lane.x() - 6, centerY - 4, Draw.TEXT_DISABLED);

        graphics.enableScissor(lane.x(), rowsTop(content), lane.right(), content.bottom());

        for (AnimationTrack member : group.members) {
            for (int i = 0; i < member.keyCount(); i++) {
                TrackKey key = member.key(i);

                if (key == null) {
                    continue;
                }

                Draw.diamond(graphics, timeToX(content, key.time()), centerY, KEY_RADIUS - 1, member.color());
            }
        }

        graphics.disableScissor();
    }

    private void renderTrackRow(GuiGraphicsExtractor graphics, UiRect content, UiRect lane, AnimationTrack track, int row, int y, int hoveredKey) {
        boolean selectedTrack = track.id().equals(context.editor().selectedTrackId());

        if (selectedTrack) {
            graphics.fill(content.x(), y, lane.right(), y + ROW_HEIGHT, Draw.ROW_SELECTED);
        } else if ((row & 1) == 1) {
            graphics.fill(content.x(), y, lane.right(), y + ROW_HEIGHT, Draw.ROW_ALT);
        }

        int centerY = y + ROW_HEIGHT / 2;
        graphics.fill(content.x() + 4, centerY - 1, content.x() + 7, centerY + 1, track.color());
        Draw.textEllipsized(graphics, track.label().getString(), content.x() + 10, centerY - 4, TRACK_COLUMN_WIDTH - 16, selectedTrack ? Draw.TEXT : Draw.TEXT_DIM);
        Draw.textRight(graphics, context.info().keyCountText(track).getString(), lane.x() - 6, centerY - 4, Draw.TEXT_DISABLED);

        graphics.enableScissor(lane.x(), rowsTop(content), lane.right(), content.bottom());

        for (int i = 0; i < track.keyCount(); i++) {
            TrackKey key = track.key(i);

            if (key == null) {
                continue;
            }

            int x = timeToX(content, key.time());
            boolean selected = selectedTrack && i == context.editor().selectedKeyIndex();
            boolean hovered = hoveredKey == i;
            int color = selected ? Draw.SELECTED : hovered ? Draw.TEXT : track.color();
            Draw.diamond(graphics, x, centerY, selected || hovered ? KEY_RADIUS + 1 : KEY_RADIUS, color);
        }

        graphics.disableScissor();
    }

    private void renderRuler(GuiGraphicsExtractor graphics, UiRect content, UiRect lane) {
        float step = context.majorTickStep();
        float minorStep = step / 5f;
        int rulerBottom = content.y() + RULER_HEIGHT - 1;

        graphics.enableScissor(lane.x(), content.y(), lane.right(), content.bottom());

        float startTime = context.viewStartTime();
        float viewEnd = xToTime(content, lane.right());
        int first = (int) Math.floor(startTime / minorStep) - 1;
        int last = (int) Math.ceil(viewEnd / minorStep) + 1;

        // 极端缩放下限制循环规模
        if (last - first > 600) {
            first = (int) Math.floor(startTime / step) - 1;
            last = (int) Math.ceil(viewEnd / step) + 1;
        }

        for (int i = first; i <= last; i++) {
            float time = i * minorStep;

            if (time < 0) {
                continue;
            }

            int x = timeToX(content, time);

            if (x < lane.x() - 1) {
                continue;
            }

            if (x > lane.right()) {
                break;
            }

            boolean major = Math.abs(time / step - Math.round(time / step)) < 1E-4f;
            Draw.vLine(graphics, x, rulerBottom - (major ? 5 : 2), rulerBottom, major ? Draw.GRID_STRONG : Draw.GRID);

            if (major) {
                Draw.vLine(graphics, x, rulerBottom, content.bottom(), Draw.GRID);
                String label = context.formatTick(time, step);

                // 末端刻度的标签会越出车道右边界被裁掉，此时不画标签
                if (x + 2 + Draw.font().width(label) <= lane.right()) {
                    Draw.text(graphics, label, x + 2, content.y() + 2, Draw.TEXT_DIM);
                }
            }
        }

        graphics.disableScissor();
    }

    private void renderPlayhead(GuiGraphicsExtractor graphics, UiRect content, UiRect lane) {
        int x = timeToX(content, context.player().time());

        if (x < lane.x() - 1 || x > lane.right()) {
            return;
        }

        graphics.enableScissor(lane.x(), content.y(), lane.right(), content.bottom());
        Draw.vLine(graphics, x, content.y() + RULER_HEIGHT - 1, content.bottom(), Draw.PLAYHEAD);
        graphics.fill(x - 3, content.y() + 1, x + 4, content.y() + 4, Draw.PLAYHEAD);
        graphics.disableScissor();
    }

    // region 交互

    @Override
    protected boolean contentMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        UiRect content = contentRect();
        UiRect lane = laneRect(content);
        List<Row> rows = rows();
        int index = rowIndexAt(content, event.y());
        Row row = index >= 0 && index < rows.size() ? rows.get(index) : null;
        AnimationTrack track = row instanceof TrackRow trackRow ? trackRow.track() : null;
        int keyIndex = row == null ? -1 : keyIndexAt(content, row, index, event.x(), event.y());

        // 右键：打开时间轴操作菜单；鼠标下有没有关键帧决定菜单里是否出现「删除关键帧」
        if (event.button() == 1) {
            openMenu(buildMenu(lane, track, keyIndex), event.x(), event.y());
            return true;
        }

        // 分组头：点击整行切换折叠
        if (row instanceof GroupRow group) {
            if (!collapsedGroups.remove(group.id)) {
                collapsedGroups.add(group.id);
            }

            return true;
        }

        // 名称列：选中轨道
        if (event.x() < content.x() + TRACK_COLUMN_WIDTH) {
            if (track != null) {
                context.editor().selectTrack(track.id());
            }

            return true;
        }

        // 标尺：拖拽播放头
        if (event.y() < content.y() + RULER_HEIGHT) {
            context.player().seek(context.snapTime(xToTime(content, event.x())));
            drag = Drag.PLAYHEAD;
            return true;
        }

        if (track == null) {
            return true;
        }

        if (keyIndex >= 0) {
            context.editor().selectTrack(track.id());
            context.editor().selectKey(keyIndex);
            drag = Drag.KEY;
            dragRow = index;
            dragKey = keyIndex;
            return true;
        }

        context.editor().selectTrack(track.id());

        if (doubleClick) {
            float time = context.snapTime(xToTime(content, event.x()));

            if (context.editor().addKey(track, time) >= 0) {
                context.notify(EditorLang.t("notify.key_added", Draw.num(time, 2)));
            }
        }

        return true;
    }

    @Override
    protected boolean contentMouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        UiRect content = contentRect();

        switch (drag) {
            case PLAYHEAD -> {
                context.player().seek(context.snapTime(xToTime(content, event.x())));
                return true;
            }
            case KEY -> {
                List<Row> rows = rows();

                if (dragRow >= 0 && dragRow < rows.size() && rows.get(dragRow) instanceof TrackRow trackRow) {
                    int moved = context.editor().moveKey(trackRow.track(), dragKey, context.snapTime(xToTime(content, event.x())));

                    if (moved >= 0) {
                        dragKey = moved;
                    }
                }

                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            dragRow = -1;
            dragKey = -1;
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    protected boolean contentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isCtrlDown()) {
            zoomAt(mouseX, scrollY > 0 ? 1.15f : 0.87f);
        } else if (isShiftDown()) {
            context.viewStartTime(context.viewStartTime() - (float) scrollY * 20f / context.pixelsPerSecond());
        } else {
            double maxScroll = Math.max(0, rows().size() * ROW_HEIGHT - (contentRect().height() - RULER_HEIGHT));
            this.scrollY = Mth.clamp(this.scrollY - scrollY * ROW_HEIGHT * 1.5, 0, maxScroll);
        }

        return true;
    }

    @Override
    protected boolean contentKeyPressed(KeyEvent event) {
        if (event.key() == KEY_DELETE && context.editor().selectedKey() != null) {
            context.editor().removeSelectedKey();
            context.notify(EditorLang.t("notify.key_removed"));
            return true;
        }

        return false;
    }

    private boolean isCtrlDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private boolean isShiftDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /// 以某个像素位置为中心缩放
    private void zoomAt(double centerX, float factor) {
        UiRect content = contentRect();
        float timeAtCursor = xToTime(content, centerX);
        context.pixelsPerSecond(context.pixelsPerSecond() * factor);
        context.viewStartTime(timeAtCursor - (float) (centerX - laneRect(content).x()) / context.pixelsPerSecond());
    }

    /// 让整段动画适配当前宽度
    private void fitView() {
        UiRect content = contentRect();
        float duration = Math.max(0.5f, context.duration());
        context.pixelsPerSecond((laneRect(content).width() - 24f) / duration);
        context.viewStartTime(-8f / context.pixelsPerSecond());
    }

    /// 时间轴右键菜单；鼠标落在关键帧上时额外给出删除入口
    private ContextMenu buildMenu(UiRect lane, @Nullable AnimationTrack track, int keyIndex) {
        ContextMenu menu = new ContextMenu();

        if (track != null && keyIndex >= 0) {
            menu.item(Icons.REMOVE, EditorLang.t("timeline.remove_key"), () -> {
                context.editor().removeKey(track, keyIndex);
                context.notify(EditorLang.t("notify.key_removed"));
            });
            menu.separator();
        }

        menu.item(Icons.FIT, EditorLang.t("timeline.fit"), this::fitView);
        menu.item(Icons.ZOOM_IN, EditorLang.t("timeline.zoom_in"), () -> zoomAt(lane.centerX(), 1.25f));
        menu.item(Icons.ZOOM_OUT, EditorLang.t("timeline.zoom_out"), () -> zoomAt(lane.centerX(), 0.8f));
        menu.separator();
        menu.toggle(Icons.SNAP, EditorLang.t("toolbar.snap"),
                context::snapEnabled, () -> context.snapEnabled(!context.snapEnabled()));
        return menu;
    }

    // endregion
}
