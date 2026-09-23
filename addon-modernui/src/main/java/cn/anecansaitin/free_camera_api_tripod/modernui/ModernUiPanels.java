package cn.anecansaitin.free_camera_api_tripod.modernui;

import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathNodec;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Pathc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorSession;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.PopupMenu;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Modern UI 版编辑器的各个面板。
///
/// 每个方法按「标题 + 信息行 + 操作按钮」搭出一个面板；数据全部来自 {@link EditorSession}，
/// 因此本类不依赖主 mod 的内部实现。
///
/// Modern UI 是保留模式界面，控件建好之后不会自己跟着数据走，所以这里分两手：
/// 会变的值用 {@link ModernUiWidgets#liveField} 登记到刷新器上；
/// 会变的列表（轨道、路径节点）则在集合本身变化时整体重建。
final class ModernUiPanels {
    /// 时间轴左侧轨道名列宽。标尺与各轨道行共用，刻度线才能和关键帧严格对齐
    static final int NAME_WIDTH = 132;

    private static final int ROW_HEIGHT = 20;
    private static final int NODE_ROW_HEIGHT = 18;
    private static final int CONTROL_HEIGHT = 22;
    private static final int MENU_FIT = 1;
    private static final int MENU_ZOOM_IN = 2;
    private static final int MENU_ZOOM_OUT = 3;

    private ModernUiPanels() {
    }

    // region 面板

    /// 动画面板：名称、时长、轨道数、缩放、播放头
    static LinearLayout animationPanel(Context context, EditorSession session, ModernUiRefresher refresher) {
        LinearLayout panel = ModernUiWidgets.panel(context, ModernUiText.str("panel.animation"));
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.name"),
                refresher, () -> session.animation().name()), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.duration"),
                refresher, () -> format(session.animation().duration())), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.tracks"),
                refresher, () -> String.valueOf(session.animation().tracks().size())), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.zoom"),
                refresher, () -> format(session.pixelsPerSecond())), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.playhead"),
                refresher, () -> format(session.playheadTime())), ModernUiWidgets.fillWidth());
        return panel;
    }

    /// 路径面板：名称、节点数、当前运动模式与模式切换
    static LinearLayout pathPanel(Context context, EditorSession session, ModernUiRefresher refresher) {
        LinearLayout panel = ModernUiWidgets.panel(context, ModernUiText.str("panel.path"));
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.name"),
                refresher, () -> session.path().name()), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.nodes"),
                refresher, () -> String.valueOf(session.path().size())), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.mode"),
                refresher, () -> ModernUiText.str(session.pathMode()
                        ? "modern_ui.button.path_mode" : "modern_ui.button.coord_mode")),
                ModernUiWidgets.fillWidth());

        LinearLayout buttons = ModernUiWidgets.buttonRow(context, 2.0F);
        buttons.addView(ModernUiWidgets.button(context, ModernUiText.str("modern_ui.button.path_mode"),
                session::switchToPathMode), ModernUiWidgets.equalWeight(1.0F));
        buttons.addView(ModernUiWidgets.button(context, ModernUiText.str("modern_ui.button.coord_mode"),
                session::switchToCoordinateMode), ModernUiWidgets.equalWeight(1.0F));
        panel.addView(buttons, ModernUiWidgets.fillWidth());
        return panel;
    }

    /// 关键帧面板：选中轨道与选中关键帧的信息，加帧落在播放头上
    static LinearLayout keyframePanel(Context context, EditorSession session, ModernUiRefresher refresher) {
        LinearLayout panel = ModernUiWidgets.panel(context, ModernUiText.str("panel.keyframe"));
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.track"),
                refresher, () -> {
                    @Nullable AnimationTrack track = session.selectedTrack();
                    return track == null ? "-" : track.label().getString();
                }), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.keys"),
                refresher, () -> {
                    @Nullable AnimationTrack track = session.selectedTrack();
                    return track == null ? "0" : String.valueOf(track.keyCount());
                }), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.selected_key"),
                refresher, () -> session.selectedKeyIndex() < 0
                        ? "-" : String.valueOf(session.selectedKeyIndex())), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.time"),
                refresher, () -> {
                    @Nullable AnimationTrack track = session.selectedTrack();
                    int index = session.selectedKeyIndex();
                    return track == null || index < 0 || index >= track.keyCount()
                            ? "-" : format(track.key(index).time());
                }), ModernUiWidgets.fillWidth());

        LinearLayout buttons = ModernUiWidgets.buttonRow(context, 2.0F);
        buttons.addView(ModernUiWidgets.button(context, ModernUiText.str("timeline.add_key"),
                () -> addKeyAtPlayhead(session)), ModernUiWidgets.equalWeight(1.0F));
        buttons.addView(ModernUiWidgets.button(context, ModernUiText.str("timeline.remove_key"),
                () -> removeSelectedKey(session)), ModernUiWidgets.equalWeight(1.0F));
        panel.addView(buttons, ModernUiWidgets.fillWidth());
        return panel;
    }

    /// 在当前选中轨道的播放头位置插入关键帧；没有选中轨道时给出提示而不是静默失败
    private static void addKeyAtPlayhead(EditorSession session) {
        if (session.selectedTrack() == null) {
            session.notify(ModernUiText.of("notify.no_track_selected"));
            return;
        }

        float time = session.snapTime(session.playheadTime());

        if (session.addKey(time) >= 0) {
            session.notify(ModernUiText.of("notify.key_added", format(time)));
        }
    }

    private static void removeSelectedKey(EditorSession session) {
        if (session.selectedKeyIndex() < 0) {
            session.notify(ModernUiText.of("notify.no_key_selected"));
            return;
        }

        if (session.removeSelectedKey()) {
            session.notify(ModernUiText.of("notify.key_removed"));
        }
    }

    /// 路径节点面板：可点选的节点列表、选中节点的细节，以及记录当前相机为节点
    static LinearLayout pathNodePanel(Context context, EditorSession session, ModernUiRefresher refresher) {
        LinearLayout panel = ModernUiWidgets.panel(context, ModernUiText.str("panel.path_node"));

        LinearLayout list = ModernUiWidgets.column(context);
        NodeRows rows = new NodeRows(session);
        ScrollView scroll = ModernUiWidgets.scroll(context, list);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0F));
        refresher.add(() -> rows.refresh(context, list));

        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.smooth"),
                refresher, () -> {
                    @Nullable PathNodec node = selectedNode(session);
                    return node == null ? "-" : String.valueOf(node.smooth());
                }), ModernUiWidgets.fillWidth());
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.path_mode"),
                refresher, () -> {
                    @Nullable PathNodec node = selectedNode(session);
                    return node == null ? "-" : node.pathMode().name();
                }), ModernUiWidgets.fillWidth());

        LinearLayout buttons = ModernUiWidgets.buttonRow(context, 1.0F);
        buttons.addView(ModernUiWidgets.button(context, ModernUiText.str("modern_ui.button.record_node"),
                session::recordPathNode), ModernUiWidgets.equalWeight(1.0F));
        panel.addView(buttons, ModernUiWidgets.fillWidth());
        return panel;
    }

    private static @Nullable PathNodec selectedNode(EditorSession session) {
        Pathc path = session.path();
        int index = session.selectedPathIndex();
        return index < 0 || index >= path.size() ? null : path.node(index);
    }

    /// 曲线面板：选中轨道的曲线图，点击曲线上的关键帧即选中它
    static LinearLayout graphPanel(Context context, EditorSession session, ModernUiRefresher refresher) {
        LinearLayout panel = ModernUiWidgets.panel(context, ModernUiText.str("panel.graph"));
        panel.addView(ModernUiWidgets.liveField(context, ModernUiText.str("modern_ui.field.track"),
                refresher, () -> {
                    @Nullable AnimationTrack track = session.selectedTrack();
                    @Nullable Curve curve = track == null ? null : track.curve();

                    if (track == null) {
                        return ModernUiText.str("modern_ui.graph.empty");
                    }

                    return curve == null
                            ? ModernUiText.str("modern_ui.graph.empty")
                            : track.label().getString() + "  (" + curve.size() + ")";
                }), ModernUiWidgets.fillWidth());

        ModernCurveView curve = new ModernCurveView(context, session);
        refresher.add(curve::refresh);
        panel.addView(curve, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0F));
        return panel;
    }

    /// 视口面板：游戏画面的接管仍由主 mod 的内置界面提供，这里先留说明
    static LinearLayout viewportPanel(Context context, EditorSession session) {
        LinearLayout panel = ModernUiWidgets.panel(context, ModernUiText.str("panel.viewport"));

        LinearLayout placeholder = ModernUiWidgets.column(context);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.addView(ModernUiWidgets.dim(context, ModernUiText.str("modern_ui.viewport.placeholder")),
                ModernUiWidgets.wrap());
        panel.addView(placeholder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0F));
        return panel;
    }

    // endregion

    // region 时间轴

    /// 时间轴面板：播放控制、标尺、每轨道一行（轨道名 + 关键帧条）
    static LinearLayout timelinePanel(Context context, EditorSession session,
                                      ModernUiRefresher refresher, ModernRulerView ruler) {
        LinearLayout panel = ModernUiWidgets.panel(context, ModernUiText.str("panel.timeline"));
        panel.addView(controls(context, session, ruler), ModernUiWidgets.fillWidth());

        LinearLayout header = ModernUiWidgets.row(context);
        header.addView(ModernUiWidgets.dim(context, ModernUiText.str("timeline.tracks")),
                new LinearLayout.LayoutParams(NAME_WIDTH, ModernRulerView.HEIGHT));
        header.addView(ruler, new LinearLayout.LayoutParams(0, ModernRulerView.HEIGHT, 1.0F));
        panel.addView(header, ModernUiWidgets.fillWidth());

        LinearLayout list = ModernUiWidgets.column(context);
        TrackRows rows = new TrackRows(session);
        panel.addView(ModernUiWidgets.scroll(context, list), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0F));
        refresher.add(() -> rows.refresh(context, list));
        return panel;
    }

    /// 播放控制与比例工具。缩放和适配要落到标尺上——只有标尺知道自己有多宽
    private static LinearLayout controls(Context context, EditorSession session, ModernRulerView ruler) {
        LinearLayout row = ModernUiWidgets.buttonRow(context, 5.0F);
        row.addView(barButton(context, ModernUiText.str("toolbar.play_pause"), session::togglePlay),
                ModernUiWidgets.equalWeight(1.0F));
        row.addView(barButton(context, ModernUiText.str("toolbar.stop"), session::stopPlayback),
                ModernUiWidgets.equalWeight(1.0F));
        row.addView(barButton(context, ModernUiText.str("timeline.fit"), ruler::fitView),
                ModernUiWidgets.equalWeight(1.0F));
        row.addView(barButton(context, ModernUiText.str("timeline.zoom_out"), () -> ruler.zoom(0.8F)),
                ModernUiWidgets.equalWeight(1.0F));
        row.addView(barButton(context, ModernUiText.str("timeline.zoom_in"), () -> ruler.zoom(1.25F)),
                ModernUiWidgets.equalWeight(1.0F));
        return row;
    }

    private static Button barButton(Context context, String text, Runnable action) {
        Button button = ModernUiWidgets.button(context, text, action);
        button.setMinimumHeight(CONTROL_HEIGHT);
        return button;
    }

    /// 时间轴的轨道行。切换运动模式会重建通道，轨道集合跟着变，所以这里按集合签名重建
    private static final class TrackRows {
        private final EditorSession session;
        private final ModernUiRefresher.Gate gate = new ModernUiRefresher.Gate();
        private final List<ModernKeyStripView> strips = new ArrayList<>();

        private TrackRows(EditorSession session) {
            this.session = session;
        }

        private void refresh(Context context, LinearLayout list) {
            List<? extends AnimationTrack> tracks = session.animation().tracks();
            Object[] signature = new Object[tracks.size() + 1];
            signature[0] = tracks.size();

            for (int index = 0; index < tracks.size(); index++) {
                signature[index + 1] = tracks.get(index).id();
            }

            if (gate.changed(signature)) {
                list.removeAllViews();
                strips.clear();

                if (tracks.isEmpty()) {
                    list.addView(ModernUiWidgets.dim(context, ModernUiText.str("timeline.empty")),
                            ModernUiWidgets.fillWidth());
                }

                for (AnimationTrack track : tracks) {
                    LinearLayout row = ModernUiWidgets.row(context);
                    row.addView(ModernUiWidgets.dim(context, track.label().getString()),
                            new LinearLayout.LayoutParams(NAME_WIDTH, ROW_HEIGHT));
                    ModernKeyStripView strip = new ModernKeyStripView(context, session, track);
                    strips.add(strip);
                    row.addView(strip, new LinearLayout.LayoutParams(0, ROW_HEIGHT, 1.0F));
                    list.addView(row, ModernUiWidgets.fillWidth());
                }
            }

            for (ModernKeyStripView strip : strips) {
                strip.refresh();
            }
        }
    }

    /// 路径节点列表。节点增删后需要重建，选中项变了只需要换一下行的文字
    private static final class NodeRows {
        private final EditorSession session;
        /// 节点集合的签名：变了才重建整列
        private final ModernUiRefresher.Gate listGate = new ModernUiRefresher.Gate();
        /// 选中项：变了才重写行的文字与颜色
        private final ModernUiRefresher.Gate selectionGate = new ModernUiRefresher.Gate();
        private final List<TextView> rows = new ArrayList<>();

        private NodeRows(EditorSession session) {
            this.session = session;
        }

        private void refresh(Context context, LinearLayout list) {
            Pathc path = session.path();

            if (listGate.changed(path.name(), path.size())) {
                list.removeAllViews();
                rows.clear();

                for (int index = 0; index < path.size(); index++) {
                    TextView row = ModernUiWidgets.dim(context, "");
                    int target = index;
                    row.setOnClickListener(view -> session.selectPathNode(target));
                    rows.add(row);
                    list.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, NODE_ROW_HEIGHT));
                }
            }

            int selected = session.selectedPathIndex();

            if (!selectionGate.changed(selected, rows.size())) {
                return;
            }

            for (int index = 0; index < rows.size(); index++) {
                TextView row = rows.get(index);
                Vector3fc position = path.node(index).position();
                row.setText(nodeText(index, selected, position), TextView.BufferType.NORMAL);
                row.setTextColor(index == selected ? ModernUiColors.TEXT : ModernUiColors.TEXT_DIM);
            }
        }

        private static String nodeText(int index, int selected, Vector3fc position) {
            return (index == selected ? "> " : "  ") + (index + 1) + ". "
                    + format(position.x()) + ", " + format(position.y()) + ", " + format(position.z());
        }
    }

    // endregion

    // region 装配用的容器

    /// 顶部菜单条：标题、视图菜单、关闭
    static LinearLayout menuBar(Context context, EditorSession session, ModernRulerView ruler, Runnable close) {
        LinearLayout bar = ModernUiWidgets.row(context);
        bar.setPadding(ModernUiWidgets.PAD, ModernUiWidgets.PAD, ModernUiWidgets.PAD, ModernUiWidgets.PAD);
        bar.addView(ModernUiWidgets.title(context, ModernUiText.str("modern_ui.title")),
                ModernUiWidgets.weighted(1.0F));

        Button view = ModernUiWidgets.textButton(context, ModernUiText.str("modern_ui.menu.view"));
        view.setOnClickListener(clicked -> showViewMenu(context, ruler, view));
        bar.addView(view, ModernUiWidgets.wrap());
        bar.addView(ModernUiWidgets.button(context, ModernUiText.str("modern_ui.close"), close),
                ModernUiWidgets.wrap());
        return bar;
    }

    /// 「视图」下拉：只放能真正落到数据上的比例操作
    private static void showViewMenu(Context context, ModernRulerView ruler, View anchor) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, MENU_FIT, 0, ModernUiText.str("timeline.fit"));
        menu.getMenu().add(0, MENU_ZOOM_IN, 1, ModernUiText.str("timeline.zoom_in"));
        menu.getMenu().add(0, MENU_ZOOM_OUT, 2, ModernUiText.str("timeline.zoom_out"));
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_FIT -> ruler.fitView();
                case MENU_ZOOM_IN -> ruler.zoom(1.25F);
                case MENU_ZOOM_OUT -> ruler.zoom(0.8F);
                default -> {
                    return false;
                }
            }

            return true;
        });
        menu.show();
    }

    /// 底部状态栏：状态提示，以及待确认操作（切换运动模式等）的确认 / 取消
    static LinearLayout statusBar(Context context, EditorSession session, ModernUiRefresher refresher) {
        LinearLayout bar = ModernUiWidgets.row(context);
        bar.setPadding(ModernUiWidgets.PAD, ModernUiWidgets.PAD, ModernUiWidgets.PAD, ModernUiWidgets.PAD);

        TextView status = ModernUiWidgets.dim(context, "");
        bar.addView(status, ModernUiWidgets.weighted(1.0F));
        ModernUiRefresher.Gate statusGate = new ModernUiRefresher.Gate();
        refresher.add(() -> {
            if (statusGate.changed(session.statusMessage())) {
                status.setText(text(session.statusMessage()), TextView.BufferType.NORMAL);
            }
        });

        LinearLayout confirm = ModernUiWidgets.row(context);
        TextView message = ModernUiWidgets.label(context, "");
        confirm.addView(message, ModernUiWidgets.wrap());
        confirm.addView(ModernUiWidgets.button(context, ModernUiText.str("common.confirm"),
                session::confirmPending), ModernUiWidgets.wrap());
        confirm.addView(ModernUiWidgets.button(context, ModernUiText.str("common.cancel"),
                session::dismissPending), ModernUiWidgets.wrap());
        confirm.setVisibility(View.GONE);
        bar.addView(confirm, ModernUiWidgets.wrap());

        ModernUiRefresher.Gate confirmGate = new ModernUiRefresher.Gate();
        refresher.add(() -> {
            if (!confirmGate.changed(session.pendingConfirm())) {
                return;
            }

            message.setText(text(session.pendingConfirm()), TextView.BufferType.NORMAL);
            confirm.setVisibility(session.pendingConfirm() == null ? View.GONE : View.VISIBLE);
        });
        return bar;
    }

    // endregion

    private static String text(@Nullable Component component) {
        return component == null ? "" : component.getString();
    }

    /// 数值统一保留两位小数
    private static String format(float value) {
        return String.format("%.2f", value);
    }
}
