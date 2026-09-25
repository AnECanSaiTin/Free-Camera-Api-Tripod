package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationChannelRegistry;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
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
/// 轨道行按 {@link AnimationChannelRegistry} 的分组路径组织成树：分组的路径用 '/' 分隔，
/// 逐层建树，因此任意层级都可独立折叠（当前注册表只定义了单层的「旋转」分组）。
///
/// 关键帧区支持框选：左键从空白处拉出矩形多选，多选可整体平移与批量删除。
public class TimelinePanel extends EditorPanel {
    private static final int DEFAULT_TRACK_COLUMN_WIDTH = 132;
    private static final int MIN_TRACK_COLUMN_WIDTH = 70;
    private static final int MAX_TRACK_COLUMN_WIDTH = 320;
    /// 名称列分割线的拖拽命中半径
    private static final int COLUMN_GRAB_RADIUS = 3;
    private static final int RULER_HEIGHT = 14;
    private static final int ROW_HEIGHT = 16;
    /// 播放状态条高度。它属于时间轴面板本身（位于标题栏正下方），面板拖到哪就跟到哪
    private static final int PLAY_BAR_HEIGHT = 22;
    private static final int PLAY_BAR_BUTTON_WIDTH = 20;
    /// 播放栏按钮高度：取面板统一的控件高度，与其它界面按钮一致
    private static final int PLAY_BAR_BUTTON_HEIGHT = EditorPanel.CONTROL_HEIGHT;
    private static final int KEY_RADIUS = 3;
    private static final int KEY_GRAB_RADIUS = 5;
    private static final int KEY_DELETE = 261;
    /// 每层树形深度的缩进像素
    private static final int INDENT_STEP = 10;
    /// 分组行左侧折叠箭头的宽度；点在这段区间内切换折叠，其余位置选中并拖拽整个分组
    private static final int GROUP_ARROW_WIDTH = 12;
    /// 框选矩形的填充与描边色
    private static final int MARQUEE_FILL = 0x304EA1FF;
    private static final int MARQUEE_BORDER = 0xFF4EA1FF;
    /// 位置分组的路径标识，与 {@link AnimationChannelRegistry} 注册的一致
    private static final String POSITION_GROUP = "position";
    /// 拖拽排序时被拖动行的高亮底色（ACCENT 的半透明版本，不遮住行内文字）
    private static final int TRACK_DRAG_HIGHLIGHT = 0x604EA1FF;

    private final EditorContext context;
    private double scrollY;

    private enum Drag {
        NONE,
        PLAYHEAD,
        KEY,
        /// 中键整体平移视图
        PAN,
        /// 拖拽名称列与关键帧区之间的分割线
        COLUMN,
        /// 在空白处拉框多选关键帧
        MARQUEE,
        /// 整体平移多选中的关键帧
        SELECTION,
        /// 在名称列的轨道行上拖动，调整轨道顺序
        TRACK_ORDER
    }

    private Drag drag = Drag.NONE;
    private int dragRow = -1;
    private int dragKey = -1;
    /// 正在拖拽排序的轨道 id；不在排序时为 null
    private String dragTrackId;
    /// 正在拖拽排序的分组路径（折叠轴）；不在排序时为 null
    private String dragGroupPath;
    /// 当前选中的分组路径（折叠轴）；选中轨道时为 null
    private String selectedGroupPath;
    /// 排序拖拽的纵向累计位移：每满半个行高交换一次，交换后扣掉一个行高
    private double trackOrderAccum;
    /// 名称列宽度，可由分割线拖动调整
    private int trackColumnWidth = DEFAULT_TRACK_COLUMN_WIDTH;
    /// 已折叠的分组路径（如 "rotation"、"a/b"）
    private final Set<String> collapsedGroups = new HashSet<>();
    /// 多选的关键帧引用；与单选状态互斥
    private final List<CameraEditorModel.KeyRef> multiSelection = new ArrayList<>();
    /// 框选的起点与当前点（屏幕坐标）
    private double marqueeStartX;
    private double marqueeStartY;
    private double marqueeEndX;
    private double marqueeEndY;
    /// 整体平移时上一帧吸附后的指针时间，用于按增量平移
    private float selectionSnapTime;
    /// 播放状态条里按钮的刷新器（播放/暂停图标随状态切换）
    private final List<Runnable> barRefreshers = new ArrayList<>();
    /// 播放状态条按钮区右端，状态文字从它右侧开始排
    private int barEndX;

    public TimelinePanel(EditorContext context) {
        super("timeline", EditorLang.t("panel.timeline"));
        this.context = context;
    }

    // region 播放状态条

    private UiRect playBarRect(UiRect content) {
        return new UiRect(content.x(), content.y(), content.width(), Math.min(PLAY_BAR_HEIGHT, content.height()));
    }

    @Override
    protected void layoutWidgets(UiRect content) {
        barRefreshers.clear();
        UiRect bar = playBarRect(content);
        int y = bar.y() + (PLAY_BAR_HEIGHT - PLAY_BAR_BUTTON_HEIGHT) / 2;
        int[] x = {bar.x() + 6};

        ButtonWidget playPause = addBarButton(x, y, Component.literal(Icons.PLAY), this::togglePlay,
                EditorLang.t("toolbar.play_pause"));
        // 播放中把图标切成暂停，让同一个按钮同时表达两个状态
        barRefreshers.add(() -> playPause.label(Component.literal(context.player().playing() ? Icons.PAUSE : Icons.PLAY)));

        addBarButton(x, y, Component.literal(Icons.STOP), () -> context.player().stop(), EditorLang.t("toolbar.stop"));
        x[0] += 6;
        // 比例工具：时间轴的缩放不再只靠滚轮，这里给出明确的缩小 / 放大 / 适配
        addBarButton(x, y, Component.literal(Icons.ZOOM_OUT), this::zoomOut, EditorLang.t("timeline.zoom_out"));
        addBarButton(x, y, Component.literal(Icons.ZOOM_IN), this::zoomIn, EditorLang.t("timeline.zoom_in"));
        addBarButton(x, y, Component.literal(Icons.FIT), this::fitView, EditorLang.t("timeline.fit"));
        barEndX = x[0];
    }

    /// 以可视区中心为基准放大时间轴
    public void zoomIn() {
        zoomAt(laneRect(contentRect()).centerX(), 1.25f);
    }

    /// 以可视区中心为基准缩小时间轴
    public void zoomOut() {
        zoomAt(laneRect(contentRect()).centerX(), 0.8f);
    }

    private ButtonWidget addBarButton(int[] cursor, int y, Component label, Runnable action, Component tooltip) {
        ButtonWidget button = new ButtonWidget(new UiRect(cursor[0], y, PLAY_BAR_BUTTON_WIDTH, PLAY_BAR_BUTTON_HEIGHT), label, action);
        button.tooltip(tooltip);
        widgets.add(button);
        cursor[0] += PLAY_BAR_BUTTON_WIDTH + 3;
        return button;
    }

    /// 播放状态条：只保留播放控制，其余操作都在时间轴右键菜单里
    private void renderPlayBar(GuiGraphicsExtractor graphics, UiRect content) {
        UiRect bar = playBarRect(content);
        Draw.canvas(graphics, bar, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, bar.x(), bar.right(), bar.bottom() - 1, Draw.BORDER);

        for (Runnable refresher : barRefreshers) {
            refresher.run();
        }

        String readout = context.info().stateText().getString() + "  " + context.info().timeText().getString();
        int readoutX = bar.right() - 6 - Draw.font().width(readout);
        Component status = context.statusMessage();

        if (status != null) {
            Draw.textEllipsized(graphics, status.getString(), barEndX + 8, bar.y() + 7,
                    Math.max(8, readoutX - 4 - (barEndX + 8)), Draw.ACCENT);
        }

        Draw.text(graphics, readout, readoutX, bar.y() + 7, Draw.TEXT_DIM);
    }

    /// 播放 / 暂停。播放前先把视角切回预览视角，否则自由视角会盖住动画，播放看不到效果。
    public void togglePlay() {
        if (context.player().playing()) {
            context.player().pause();
            return;
        }

        if (context.editor().viewMode() != CameraEditorModel.ViewMode.PREVIEW) {
            context.editor().viewMode(CameraEditorModel.ViewMode.PREVIEW);
        }

        context.player().play();
    }

    // endregion

    // region 几何

    /// 标尺区顶端：播放状态条之下
    private int rulerTop(UiRect content) {
        return content.y() + PLAY_BAR_HEIGHT;
    }

    private UiRect laneRect(UiRect content) {
        return new UiRect(content.x() + trackColumnWidth, rulerTop(content), Math.max(0, content.width() - trackColumnWidth), Math.max(0, content.height() - PLAY_BAR_HEIGHT));
    }

    /// 名称列与关键帧区之间的分割线
    private UiRect columnSplitterRect(UiRect content) {
        return new UiRect(content.x() + trackColumnWidth - COLUMN_GRAB_RADIUS, rulerTop(content), COLUMN_GRAB_RADIUS * 2, Math.max(0, content.height() - PLAY_BAR_HEIGHT));
    }

    private int rowsTop(UiRect content) {
        return rulerTop(content) + RULER_HEIGHT;
    }

    /// 轨道区可见高度：内容区扣掉播放状态条与标尺
    private static int rowsViewHeight(UiRect content) {
        return Math.max(0, content.height() - PLAY_BAR_HEIGHT - RULER_HEIGHT);
    }

    private int rowY(UiRect content, int row) {
        return rowsTop(content) + row * ROW_HEIGHT - (int) scrollY;
    }

    /// 分割线是否处于拖拽或悬停状态
    private boolean columnSplitterHovered(int mouseX, int mouseY) {
        return drag == Drag.COLUMN || columnSplitterRect(contentRect()).contains(mouseX, mouseY);
    }

    /// 标尺右端的比例读数：用起止时间与总时长明确表达当前缩放
    private void renderSpanReadout(GuiGraphicsExtractor graphics, UiRect content, UiRect lane) {
        float start = Math.max(0f, context.viewStartTime());
        float end = start + lane.width() / context.pixelsPerSecond();
        String readout = EditorLang.t("timeline.span",
                Draw.num(start, 2), Draw.num(end, 2), Draw.num(context.duration(), 2)).getString();
        int textWidth = Draw.font().width(readout);
        int x = lane.right() - textWidth - 4;

        if (x <= lane.x()) {
            return;
        }

        Draw.canvas(graphics, new UiRect(x - 3, rulerTop(content) + 1, textWidth + 6, RULER_HEIGHT - 3), Draw.PANEL_HEADER_BG);
        Draw.text(graphics, readout, x, rulerTop(content) + 3, Draw.TEXT_DIM);
    }

    /// 播放时让可见范围跟着播放头走。
    ///
    /// 播放头到达或越过右边界就把视图整体右移，并让它落在约九成宽度的位置：
    /// 停在正好贴边的话下一帧立刻又越界，看起来会一直在边界上抖。
    /// 往回放（播放头跑回视图左侧）不处理，用户没要求，贸然跟随反而会打断手动平移。
    private void followPlayhead(UiRect lane) {
        float pixelsPerSecond = context.pixelsPerSecond();

        if (!context.player().playing() || lane.width() <= 0 || pixelsPerSecond <= 0f) {
            return;
        }

        float visibleSeconds = lane.width() / pixelsPerSecond;
        float end = context.viewStartTime() + visibleSeconds;

        if (context.player().time() < end) {
            return;
        }

        context.viewStartTime(context.player().time() - visibleSeconds * 0.9f);
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

    /// 时间轴上的一行：分组头，或一条具体轨道。
    ///
    /// 两者都带有路径标识与层级，因此树可以任意加深，而不是写死两层。
    private sealed interface Row permits GroupRow, TrackRow {
        /// 唯一路径标识；折叠状态按该路径记录
        String path();

        /// 缩进层级，根层级为 0
        int depth();
    }

    /// 分组节点；子树在折叠时不再占行
    private static final class GroupRow implements Row {
        private final String path;
        private final int depth;
        private final Component label;
        private final List<Row> children = new ArrayList<>();

        private GroupRow(String path, int depth, Component label) {
            this.path = path;
            this.depth = depth;
            this.label = label;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public int depth() {
            return depth;
        }

        public Component label() {
            return label;
        }

        public List<Row> children() {
            return children;
        }
    }

    /// 轨道节点；path 为「父路径 + 轨道 id」，与分组路径处于同一命名空间
    private record TrackRow(AnimationTrack track, String path, int depth) implements Row {
    }

    /// 当前可见的行：按分组路径逐层建树，折叠的节点隐藏其整棵子树
    private List<Row> rows() {
        List<Row> roots = new ArrayList<>();
        Map<String, GroupRow> groups = new LinkedHashMap<>();

        for (AnimationTrack track : tracks()) {
            String groupPath = AnimationChannelRegistry.group(track.id());
            GroupRow parent = null;

            if (groupPath != null && !groupPath.isEmpty()) {
                // 按 '/' 拆分逐层建树：将来注册表返回 "a/b" 这样的路径即可自然形成多层
                StringBuilder path = new StringBuilder();

                for (String segment : groupPath.split("/")) {
                    if (segment.isEmpty()) {
                        continue;
                    }

                    if (!path.isEmpty()) {
                        path.append('/');
                    }

                    path.append(segment);
                    String current = path.toString();
                    GroupRow group = groups.get(current);

                    if (group == null) {
                        group = new GroupRow(current, parent == null ? 0 : parent.depth() + 1,
                                AnimationChannelRegistry.groupLabel(current));
                        groups.put(current, group);

                        if (parent == null) {
                            roots.add(group);
                        } else {
                            parent.children().add(group);
                        }
                    }

                    parent = group;
                }
            }

            TrackRow row = new TrackRow(track, parent == null ? track.id() : parent.path() + "/" + track.id(),
                    parent == null ? 0 : parent.depth() + 1);

            if (parent == null) {
                roots.add(row);
            } else {
                parent.children().add(row);
            }
        }

        List<Row> rows = new ArrayList<>();

        for (Row root : roots) {
            flatten(root, rows);
        }

        return rows;
    }

    /// 深度优先展开：先加入节点本身，未折叠时再递归加入子树
    private void flatten(Row row, List<Row> out) {
        out.add(row);

        if (row instanceof GroupRow group && !collapsedGroups.contains(group.path())) {
            for (Row child : group.children()) {
                flatten(child, out);
            }
        }
    }

    /// 收集分组下所有层级的轨道，用于折叠时的关键帧概览与成员计数
    private void collectTracks(GroupRow group, List<AnimationTrack> out) {
        for (Row child : group.children()) {
            if (child instanceof TrackRow trackRow) {
                out.add(trackRow.track());
            } else if (child instanceof GroupRow nested) {
                collectTracks(nested, out);
            }
        }
    }

    /// 是否位置相关行（路径距离轨道、位置分组、位置三轴）；这些行上右键才提供运动模式切换
    private static boolean isPositionRow(@Nullable Row row) {
        return switch (row) {
            case TrackRow trackRow -> isPositionTrack(trackRow.track().id());
            case GroupRow groupRow -> POSITION_GROUP.equals(groupRow.path());
            case null -> false;
        };
    }

    private static boolean isPositionTrack(String id) {
        return CameraAnimation.CHANNEL_POSITION.equals(id)
                || CameraAnimation.CHANNEL_POSITION_X.equals(id)
                || CameraAnimation.CHANNEL_POSITION_Y.equals(id)
                || CameraAnimation.CHANNEL_POSITION_Z.equals(id);
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
        renderPlayBar(graphics, content);
        UiRect lane = laneRect(content);
        followPlayhead(lane);
        int rulerTop = rulerTop(content);
        validateSelection();
        List<Row> rows = rows();

        // 轨道名称列
        graphics.fill(content.x(), rulerTop, content.x() + trackColumnWidth - 1, content.bottom(), Draw.OVERLAY_DIM);
        Draw.text(graphics, EditorLang.t("timeline.tracks"), content.x() + 5, rulerTop + 3, Draw.TEXT_DIM);

        // 标尺
        graphics.fill(lane.x(), rulerTop, lane.right(), rulerTop + RULER_HEIGHT - 1, Draw.PANEL_HEADER_BG);
        renderRuler(graphics, content, lane);
        renderSpanReadout(graphics, content, lane);

        Draw.hLine(graphics, content.x(), content.right(), rulerTop + RULER_HEIGHT - 1, Draw.BORDER);
        Draw.vLine(graphics, content.x() + trackColumnWidth - 1, rulerTop, content.bottom(),
                columnSplitterHovered(mouseX, mouseY) ? Draw.SPLITTER_HOVER : Draw.BORDER);

        // 超出动画时长的区域压暗
        int endX = timeToX(content, context.duration());

        if (endX < lane.right()) {
            graphics.fill(Math.max(endX, lane.x()), rowsTop(content), lane.right(), content.bottom(), Draw.OVERLAY_DIM);
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
        renderMarquee(graphics, content, lane);
        renderTrackDropIndicator(graphics, content, lane, mouseY);
        renderPlayhead(graphics, content, lane);
        renderScrollbar(graphics, content, rows.size());
    }

    /// 轨道区右侧的滚动条指示器：轨道底色 + 反映当前滚动范围的滑块
    private void renderScrollbar(GuiGraphicsExtractor graphics, UiRect content, int rowCount) {
        int viewHeight = rowsViewHeight(content);
        int totalHeight = rowCount * ROW_HEIGHT;
        int maxScroll = Math.max(0, totalHeight - viewHeight);

        if (maxScroll <= 0 || viewHeight <= 0) {
            return;
        }

        int trackX = content.right() - 4;
        int trackTop = rowsTop(content) + 1;
        int trackHeight = Math.max(1, viewHeight - 2);
        int thumbHeight = Mth.clamp(Math.round((float) trackHeight * viewHeight / totalHeight), 8, trackHeight);
        int thumbTop = trackTop + (int) Math.round((trackHeight - thumbHeight) * scrollY / maxScroll);
        graphics.fill(trackX, trackTop, trackX + 3, trackTop + trackHeight, Draw.GRID);
        graphics.fill(trackX, thumbTop, trackX + 3, thumbTop + thumbHeight, Draw.BORDER);
    }

    private void renderRow(GuiGraphicsExtractor graphics, UiRect content, UiRect lane, Row row, int index, int y, int hoveredKey) {
        switch (row) {
            case GroupRow group -> renderGroupRow(graphics, content, lane, group, y);
            case TrackRow trackRow -> renderTrackRow(graphics, content, lane, trackRow, index, y, hoveredKey);
        }
    }

    /// 分组行：折叠箭头、组名与成员数；时间轴区用各成员自己的颜色画出小一号的关键帧概览。
    ///
    /// 与轨道行一样支持选中与拖拽排序：点左侧箭头切换折叠，点其余位置选中整个分组并进入排序拖拽。
    private void renderGroupRow(GuiGraphicsExtractor graphics, UiRect content, UiRect lane, GroupRow group, int y) {
        boolean collapsed = collapsedGroups.contains(group.path());
        boolean selected = group.path().equals(selectedGroupPath);
        boolean dragging = drag == Drag.TRACK_ORDER && group.path().equals(dragGroupPath);
        int indent = 4 + group.depth() * INDENT_STEP;
        int centerY = y + ROW_HEIGHT / 2;
        List<AnimationTrack> members = new ArrayList<>();
        collectTracks(group, members);

        if (dragging) {
            graphics.fill(content.x(), y, lane.right(), y + ROW_HEIGHT, TRACK_DRAG_HIGHLIGHT);
        } else if (selected) {
            graphics.fill(content.x(), y, lane.right(), y + ROW_HEIGHT, Draw.ROW_SELECTED);
        } else {
            graphics.fill(content.x(), y, lane.right(), y + ROW_HEIGHT, Draw.ROW_GROUP);
        }

        Draw.text(graphics, collapsed ? Icons.EXPAND : Icons.COLLAPSE, content.x() + indent, centerY - 4,
                selected ? Draw.TEXT : Draw.TEXT_DIM);
        Draw.textEllipsized(graphics, group.label().getString(), content.x() + indent + GROUP_ARROW_WIDTH, centerY - 4,
                trackColumnWidth - indent - GROUP_ARROW_WIDTH - 12, Draw.TEXT);
        Draw.textRight(graphics, String.valueOf(members.size()), lane.x() - 6, centerY - 4, Draw.TEXT_DISABLED);

        graphics.enableScissor(lane.x(), rowsTop(content), lane.right(), content.bottom());

        for (AnimationTrack member : members) {
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

    private void renderTrackRow(GuiGraphicsExtractor graphics, UiRect content, UiRect lane, TrackRow trackRow, int row, int y, int hoveredKey) {
        AnimationTrack track = trackRow.track();
        boolean selectedTrack = track.id().equals(context.editor().selectedTrackId());
        boolean dragging = drag == Drag.TRACK_ORDER && track.id().equals(dragTrackId);
        int indent = 4 + trackRow.depth() * INDENT_STEP;

        // 拖拽排序中的行用强调底色标出；底色在行内容之前铺，文字与关键帧不会被盖住
        if (dragging) {
            graphics.fill(content.x(), y, lane.right(), y + ROW_HEIGHT, TRACK_DRAG_HIGHLIGHT);
        } else if (selectedTrack) {
            graphics.fill(content.x(), y, lane.right(), y + ROW_HEIGHT, Draw.ROW_SELECTED);
        } else if ((row & 1) == 1) {
            graphics.fill(content.x(), y, lane.right(), y + ROW_HEIGHT, Draw.ROW_ALT);
        }

        int centerY = y + ROW_HEIGHT / 2;
        graphics.fill(content.x() + indent, centerY - 1, content.x() + indent + 3, centerY + 1, track.color());
        Draw.textEllipsized(graphics, track.label().getString(), content.x() + indent + 6, centerY - 4,
                trackColumnWidth - indent - 12, selectedTrack ? Draw.TEXT : Draw.TEXT_DIM);
        Draw.textRight(graphics, context.info().keyCountText(track).getString(), lane.x() - 6, centerY - 4, Draw.TEXT_DISABLED);

        graphics.enableScissor(lane.x(), rowsTop(content), lane.right(), content.bottom());

        for (int i = 0; i < track.keyCount(); i++) {
            TrackKey key = track.key(i);

            if (key == null) {
                continue;
            }

            int x = timeToX(content, key.time());
            boolean selected = (selectedTrack && i == context.editor().selectedKeyIndex()) || isMultiSelected(track, i);
            boolean hovered = hoveredKey == i;
            int color = selected ? Draw.SELECTED : hovered ? Draw.TEXT : track.color();
            Draw.diamond(graphics, x, centerY, selected || hovered ? KEY_RADIUS + 1 : KEY_RADIUS, color);
        }

        graphics.disableScissor();
    }

    private void renderRuler(GuiGraphicsExtractor graphics, UiRect content, UiRect lane) {
        float step = context.majorTickStep();
        float minorStep = step / 5f;
        int rulerTop = rulerTop(content);
        int rulerBottom = rulerTop + RULER_HEIGHT - 1;

        graphics.enableScissor(lane.x(), rulerTop, lane.right(), content.bottom());

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
                    Draw.text(graphics, label, x + 2, rulerTop + 2, Draw.TEXT_DIM);
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

        graphics.enableScissor(lane.x(), rulerTop(content), lane.right(), content.bottom());
        Draw.vLine(graphics, x, rulerTop(content) + RULER_HEIGHT - 1, content.bottom(), Draw.PLAYHEAD);
        graphics.fill(x - 3, rulerTop(content) + 1, x + 4, rulerTop(content) + 4, Draw.PLAYHEAD);
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

        // 播放状态条是面板自己的控件区，点击不落到轨道上
        if (playBarRect(content).contains(event.x(), event.y())) {
            return true;
        }

        // 右键：打开时间轴操作菜单；鼠标下有没有关键帧决定菜单里是否出现「删除关键帧」
        if (event.button() == 1) {
            float menuTime = context.snapTime(xToTime(content, event.x()));
            openMenu(buildMenu(lane, track, keyIndex, menuTime, isPositionRow(row)), event.x(), event.y());
            return true;
        }

        // 中键：整体平移视图（上下左右）
        if (event.button() == 2) {
            drag = Drag.PAN;
            return true;
        }

        // 分割线：调整名称列宽度
        if (columnSplitterRect(content).contains(event.x(), event.y())) {
            drag = Drag.COLUMN;
            return true;
        }

        // 分组头（折叠轴）：左侧箭头切换折叠，其余位置与轨道行一样选中并进入排序拖拽
        if (row instanceof GroupRow group) {
            int indent = 4 + group.depth() * INDENT_STEP;

            if (event.x() < content.x() + indent + GROUP_ARROW_WIDTH) {
                if (!collapsedGroups.remove(group.path())) {
                    collapsedGroups.add(group.path());
                }

                return true;
            }

            if (event.x() < content.x() + trackColumnWidth) {
                selectedGroupPath = group.path();
                context.editor().selectTrack(null);
                drag = Drag.TRACK_ORDER;
                dragGroupPath = group.path();
                trackOrderAccum = 0;
            }

            return true;
        }

        // 名称列：选中轨道；在轨道行上按下时同时进入排序拖拽（上下拖动调整轨道顺序）。
        // 分割线的命中优先级在它之上，因此这里只可能落在 TrackRow 或空白处
        if (event.x() < content.x() + trackColumnWidth) {
            if (track != null) {
                selectedGroupPath = null;
                context.editor().selectTrack(track.id());
                drag = Drag.TRACK_ORDER;
                dragTrackId = track.id();
                trackOrderAccum = 0;
            }

            return true;
        }

        // 标尺：拖拽播放头
        if (event.y() < rowsTop(content)) {
            context.player().seek(context.snapTime(xToTime(content, event.x())));
            drag = Drag.PLAYHEAD;
            return true;
        }

        // 关键帧区命中关键帧：已在多选里则整体拖动，否则转为单选
        if (track != null && keyIndex >= 0) {
            if (isMultiSelected(track, keyIndex)) {
                beginSelectionDrag(content, event.x());
                return true;
            }

            clearMultiSelection();
            context.editor().selectTrack(track.id());
            context.editor().selectKey(keyIndex);
            drag = Drag.KEY;
            dragRow = index;
            dragKey = keyIndex;
            return true;
        }

        // 落在多选包围盒内（选区内空白处）：同样整体拖动
        UiRect bounds = selectionBounds(content);

        if (bounds != null && bounds.contains(event.x(), event.y())) {
            beginSelectionDrag(content, event.x());
            return true;
        }

        // 双击空白处：在该时间加关键帧
        if (doubleClick && track != null) {
            clearMultiSelection();
            context.editor().selectTrack(track.id());
            float time = context.snapTime(xToTime(content, event.x()));

            if (context.editor().addKey(track, time) >= 0) {
                context.notify(EditorLang.t("notify.key_added", Draw.num(time, 2)));
            }

            return true;
        }

        // 空白处：开始框选；单击而不拖动即等于清空已有框选
        if (track != null) {
            context.editor().selectTrack(track.id());
        }

        clearMultiSelection();
        marqueeStartX = event.x();
        marqueeStartY = event.y();
        marqueeEndX = event.x();
        marqueeEndY = event.y();
        drag = Drag.MARQUEE;
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
            case PAN -> {
                context.viewStartTime(context.viewStartTime() - (float) deltaX / context.pixelsPerSecond());
                double maxScroll = Math.max(0, rows().size() * ROW_HEIGHT - rowsViewHeight(content));
                this.scrollY = Mth.clamp(this.scrollY - deltaY, 0, maxScroll);
                return true;
            }
            case COLUMN -> {
                trackColumnWidth = Mth.clamp((int) (event.x() - content.x()), MIN_TRACK_COLUMN_WIDTH, MAX_TRACK_COLUMN_WIDTH);
                return true;
            }
            case MARQUEE -> {
                marqueeEndX = event.x();
                marqueeEndY = event.y();
                return true;
            }
            case SELECTION -> {
                dragSelection(content, event.x());
                return true;
            }
            case TRACK_ORDER -> {
                dragTrackOrder(deltaY);
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
            if (drag == Drag.MARQUEE) {
                marqueeEndX = event.x();
                marqueeEndY = event.y();
                commitMarquee(contentRect());
            }

            drag = Drag.NONE;
            dragRow = -1;
            dragKey = -1;
            dragTrackId = null;
            dragGroupPath = null;
            trackOrderAccum = 0;
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
            double maxScroll = Math.max(0, rows().size() * ROW_HEIGHT - rowsViewHeight(contentRect()));
            this.scrollY = Mth.clamp(this.scrollY - scrollY * ROW_HEIGHT * 1.5, 0, maxScroll);
        }

        return true;
    }

    @Override
    protected boolean contentKeyPressed(KeyEvent event) {
        if (event.key() != KEY_DELETE) {
            return false;
        }

        // 多选优先：一次删掉框选中的全部关键帧
        validateSelection();

        if (!multiSelection.isEmpty()) {
            int removed = context.editor().removeKeys(multiSelection);
            multiSelection.clear();

            if (removed > 0) {
                context.notify(EditorLang.t("notify.keys_removed", removed));
            }

            return true;
        }

        if (context.editor().selectedKey() != null) {
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
    /// 缩放到刚好容纳整段动画
    public void fitView() {
        UiRect content = contentRect();
        float duration = Math.max(0.5f, context.duration());
        context.pixelsPerSecond((laneRect(content).width() - 24f) / duration);
        context.viewStartTime(-8f / context.pixelsPerSecond());
    }

    /// 时间轴右键菜单；菜单项随鼠标位置变化：落在轨道上即可加帧，落在关键帧上还能删帧
    private ContextMenu buildMenu(UiRect lane, @Nullable AnimationTrack track, int keyIndex, float time, boolean positionRow) {
        ContextMenu menu = new ContextMenu();

        // 路径距离 / 位置轴的名称上右键，可以直接切换运动模式（切换会丢掉该模式的关键帧，内部会二次确认）
        if (positionRow) {
            menu.toggle(Icons.TRACK, EditorLang.t("timeline.mode.path"),
                    () -> context.animation().motionMode() == CameraAnimation.MotionMode.PATH,
                    context::switchToPathMode);
            menu.toggle(Icons.TRACK, EditorLang.t("timeline.mode.coordinate"),
                    () -> context.animation().motionMode() == CameraAnimation.MotionMode.COORDINATE,
                    context::switchToCoordinateMode);
            menu.separator();
        }

        if (track != null) {
            menu.item(Icons.ADD, EditorLang.t("timeline.add_key"), () -> {
                if (context.editor().addKey(track, time) >= 0) {
                    context.notify(EditorLang.t("notify.key_added", Draw.num(time, 2)));
                }
            });
        }

        if (track != null && keyIndex >= 0) {
            // 点在框选范围内时，菜单里的删除等同整体删除
            boolean batch = isMultiSelected(track, keyIndex) && !multiSelection.isEmpty();

            menu.item(Icons.REMOVE, EditorLang.t("timeline.remove_key"), () -> {
                if (batch) {
                    int removed = context.editor().removeKeys(multiSelection);
                    multiSelection.clear();

                    if (removed > 0) {
                        context.notify(EditorLang.t("notify.keys_removed", removed));
                    }
                } else {
                    context.editor().removeKey(track, keyIndex);
                    context.notify(EditorLang.t("notify.key_removed"));
                }
            });
        }

        if (track != null) {
            menu.separator();
        }

        menu.item(Icons.FIT, EditorLang.t("timeline.fit"), this::fitView);
        menu.item(Icons.ZOOM_IN, EditorLang.t("timeline.zoom_in"), () -> zoomAt(lane.centerX(), 1.25f));
        menu.item(Icons.ZOOM_OUT, EditorLang.t("timeline.zoom_out"), () -> zoomAt(lane.centerX(), 0.8f));
        menu.separator();
        menu.toggle(Icons.SNAP, EditorLang.t("toolbar.snap"),
                context::snapEnabled, () -> context.snapEnabled(!context.snapEnabled()));
        menu.item(Icons.RESET, EditorLang.t("timeline.reset_column"), this::resetColumnWidth);
        return menu;
    }

    /// 名称列宽度恢复默认
    private void resetColumnWidth() {
        trackColumnWidth = DEFAULT_TRACK_COLUMN_WIDTH;
    }

    // endregion

    // region 框选多选

    /// 丢弃下标已越界的关键帧引用，避免别处改动数据后引用失效
    private void validateSelection() {
        multiSelection.removeIf(ref -> ref.track().key(ref.index()) == null);
    }

    private void clearMultiSelection() {
        multiSelection.clear();
    }

    /// 某个关键帧是否在多选集合中
    private boolean isMultiSelected(AnimationTrack track, int index) {
        for (CameraEditorModel.KeyRef ref : multiSelection) {
            if (ref.index() == index && ref.track().id().equals(track.id())) {
                return true;
            }
        }

        return false;
    }

    /// 开始整体拖动多选的关键帧；以吸附后的指针时间作为增量基准
    private void beginSelectionDrag(UiRect content, double mouseX) {
        drag = Drag.SELECTION;
        selectionSnapTime = context.snapTime(xToTime(content, mouseX));
    }

    /// 多选整体平移：每帧按吸附后的指针时间增量，把所有选中键平移同样的时间量。
    ///
    /// 每帧都以关键帧当前时间为基准重新计算目标时间，因此平移量不会累积漂移。
    private void dragSelection(UiRect content, double mouseX) {
        if (multiSelection.isEmpty()) {
            return;
        }

        float pointer = context.snapTime(xToTime(content, mouseX));
        float delta = pointer - selectionSnapTime;

        if (delta == 0f) {
            return;
        }

        // 以各键当前时间为基准，并限制整体平移量，避免任何键被推到时间轴负半轴
        List<Float> targets = new ArrayList<>(multiSelection.size());
        float minTime = Float.MAX_VALUE;

        for (CameraEditorModel.KeyRef ref : multiSelection) {
            TrackKey key = ref.track().key(ref.index());
            float time = key == null ? 0f : key.time();
            minTime = Math.min(minTime, time);
            targets.add(time);
        }

        delta = Math.max(delta, -minTime);
        selectionSnapTime = pointer;

        if (delta == 0f) {
            return;
        }

        for (int i = 0; i < targets.size(); i++) {
            targets.set(i, targets.get(i) + delta);
        }

        List<CameraEditorModel.KeyRef> moved = context.editor().moveKeys(multiSelection, targets);
        multiSelection.clear();

        for (CameraEditorModel.KeyRef ref : moved) {
            if (ref.index() >= 0) {
                multiSelection.add(ref);
            }
        }
    }

    /// 框选矩形（屏幕坐标）；未在框选时返回 null
    private @Nullable UiRect marqueeRect() {
        if (drag != Drag.MARQUEE) {
            return null;
        }

        int left = (int) Math.min(marqueeStartX, marqueeEndX);
        int top = (int) Math.min(marqueeStartY, marqueeEndY);
        int right = (int) Math.max(marqueeStartX, marqueeEndX);
        int bottom = (int) Math.max(marqueeStartY, marqueeEndY);
        return new UiRect(left, top, right - left, bottom - top);
    }

    /// 绘制框选矩形：半透明填充 + 边框，用 scissor 限制在关键帧区内
    private void renderMarquee(GuiGraphicsExtractor graphics, UiRect content, UiRect lane) {
        UiRect rect = marqueeRect();

        if (rect == null) {
            return;
        }

        graphics.enableScissor(lane.x(), rowsTop(content), lane.right(), content.bottom());
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), MARQUEE_FILL);
        Draw.border(graphics, rect, MARQUEE_BORDER);
        graphics.disableScissor();
    }

    /// 松开鼠标：把框选矩形覆盖到的可见关键帧收进多选集合。
    ///
    /// 单击而不拖动时矩形只是一个点，落点又没有关键帧，于是等同于清空框选。
    private void commitMarquee(UiRect content) {
        UiRect rect = marqueeRect();

        if (rect == null) {
            return;
        }

        multiSelection.clear();
        List<Row> rows = rows();

        for (int row = 0; row < rows.size(); row++) {
            if (!(rows.get(row) instanceof TrackRow trackRow)) {
                continue;
            }

            AnimationTrack track = trackRow.track();
            int top = rowY(content, row);

            // 行带与框选矩形只要有重叠就算覆盖，不要求行中心落在矩形内，避免拖窄一点就选不中
            if (top + ROW_HEIGHT <= rect.y() || top >= rect.bottom()) {
                continue;
            }

            for (int i = 0; i < track.keyCount(); i++) {
                TrackKey key = track.key(i);

                if (key == null) {
                    continue;
                }

                int x = timeToX(content, key.time());

                if (x >= rect.x() && x <= rect.right()) {
                    multiSelection.add(new CameraEditorModel.KeyRef(track, i));
                }
            }
        }

        if (!multiSelection.isEmpty()) {
            // 多选与单选互斥：清掉单选，Inspector 便不会去编辑某一个关键帧
            context.editor().selectKey(-1);
        }
    }

    /// 多选关键帧的屏幕包围盒，用于「在选区内按下拖动」的命中判断；没有多选时返回 null
    private @Nullable UiRect selectionBounds(UiRect content) {
        if (multiSelection.isEmpty()) {
            return null;
        }

        List<Row> rows = rows();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int row = 0; row < rows.size(); row++) {
            if (!(rows.get(row) instanceof TrackRow trackRow)) {
                continue;
            }

            AnimationTrack track = trackRow.track();
            int centerY = rowY(content, row) + ROW_HEIGHT / 2;

            for (int i = 0; i < track.keyCount(); i++) {
                TrackKey key = track.key(i);

                if (key == null || !isMultiSelected(track, i)) {
                    continue;
                }

                int x = timeToX(content, key.time());
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, centerY);
                maxY = Math.max(maxY, centerY);
            }
        }

        if (minX > maxX) {
            return null;
        }

        return new UiRect(minX, minY, maxX - minX, maxY - minY);
    }

    // endregion

    // region 轨道拖拽排序

    /// 轨道 / 分组排序拖拽：按纵向位移逐格交换顺序。
    ///
    /// 位移累积到半个行高就与相邻项交换一格，交换后扣掉一个行高、余量留待继续累积，
    /// 于是拖动是平滑的逐格换位而不是一步跳到位；已经顶到首 / 末位时交换失败，
    /// 把余量夹在阈值内，指针反向拉回即可立刻生效。
    /// 拖的是分组（折叠轴）时整个分组的成员轨道一起移动。
    private void dragTrackOrder(double deltaY) {
        if (dragTrackId == null && dragGroupPath == null) {
            return;
        }

        trackOrderAccum += deltaY;

        while (trackOrderAccum >= ROW_HEIGHT / 2.0 && moveDragged(1)) {
            trackOrderAccum -= ROW_HEIGHT;
        }

        while (trackOrderAccum <= -ROW_HEIGHT / 2.0 && moveDragged(-1)) {
            trackOrderAccum += ROW_HEIGHT;
        }

        trackOrderAccum = Mth.clamp(trackOrderAccum, -ROW_HEIGHT / 2.0, ROW_HEIGHT / 2.0);
    }

    /// 把正在拖拽的项移动一格；已到边界返回 false
    private boolean moveDragged(int offset) {
        if (dragGroupPath != null) {
            return context.animation().moveTracks(groupTrackIds(dragGroupPath), offset);
        }

        return dragTrackId != null && context.animation().moveTrack(dragTrackId, offset);
    }

    /// 某个分组（含其子分组）下的全部轨道 id，顺序与轨道顺序一致
    private List<String> groupTrackIds(String groupPath) {
        List<String> ids = new ArrayList<>();

        for (AnimationTrack track : tracks()) {
            String group = AnimationChannelRegistry.group(track.id());

            // 分组路径按 '/' 分层，前缀匹配即为该分组的成员
            if (group != null && (group.equals(groupPath) || group.startsWith(groupPath + "/"))) {
                ids.add(track.id());
            }
        }

        return ids;
    }

    /// 排序拖拽时鼠标光标下方的行索引；拖出轨道列表上方 / 下方时夹到首行与末行
    private int clampedRowIndex(UiRect content, double mouseY) {
        List<Row> rows = rows();

        if (rows.isEmpty()) {
            return -1;
        }

        int index = rowIndexAt(content, mouseY);
        return index >= 0 ? index : mouseY < rowsTop(content) ? 0 : rows.size() - 1;
    }

    /// 目标插入位置的指示线：横跨整行，画在鼠标所在行靠拖拽方向的一侧
    private void renderTrackDropIndicator(GuiGraphicsExtractor graphics, UiRect content, UiRect lane, double mouseY) {
        if (drag != Drag.TRACK_ORDER) {
            return;
        }

        int index = clampedRowIndex(content, mouseY);

        if (index < 0) {
            return;
        }

        // 鼠标落在行的上半部时线画在行顶，落在下半部时画在行底
        int rowTop = rowY(content, index);
        int y = Mth.clamp(mouseY - rowTop < ROW_HEIGHT / 2.0 ? rowTop : rowTop + ROW_HEIGHT,
                rowsTop(content) + 1, content.bottom() - 1);

        graphics.enableScissor(content.x(), rowsTop(content), lane.right(), content.bottom());
        graphics.fill(content.x(), y - 1, lane.right(), y + 1, Draw.ACCENT);
        graphics.disableScissor();
    }

    // endregion
}
