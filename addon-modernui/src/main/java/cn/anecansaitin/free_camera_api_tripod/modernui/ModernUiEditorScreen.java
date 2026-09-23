package cn.anecansaitin.free_camera_api_tripod.modernui;

import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorSession;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.SimpleScreen;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// Modern UI 版的相机编辑器界面。
///
/// 界面是一棵 {@code LinearLayout} 树：菜单条 + 三列主体（动画/路径 · 视口/曲线 · 关键帧/节点）
/// + 时间轴 + 状态栏，见 {@link ModernUiPanels}。数据全部来自主 mod 提供的 {@link EditorSession}。
///
/// 标尺在时间轴面板之外创建：菜单里的比例操作和面板里的按钮都要落到它身上，
/// 而只有标尺自己知道它有多宽。
final class ModernUiEditorScreen {
    /// 时间轴高度
    private static final int TIMELINE_HEIGHT = 148;

    private ModernUiEditorScreen() {
    }

    /// 打开界面；由 {@link ModernUiEditorBackend} 在主 mod 询问时调用
    static void open(EditorSession session) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen previous = minecraft.screen;
        minecraft.setScreen(new SimpleScreen(new EditorFragment(session, previous), null, previous,
                Component.empty()));
    }

    private static final class EditorFragment extends Fragment {
        private final EditorSession session;
        private final @Nullable Screen previous;

        private EditorFragment(EditorSession session, @Nullable Screen previous) {
            this.session = session;
            this.previous = previous;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable DataSet savedInstanceState) {
            Context context = getContext();
            ModernUiRefresher refresher = new ModernUiRefresher();
            ModernRulerView ruler = new ModernRulerView(context, session);
            refresher.add(ruler::refresh);

            LinearLayout root = ModernUiWidgets.column(context);
            root.addView(ModernUiPanels.menuBar(context, session, ruler, this::close), ModernUiWidgets.fillWidth());
            root.addView(ModernUiWidgets.divider(context), ModernUiWidgets.fillWidth());
            root.addView(buildBody(context, refresher), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0F));
            root.addView(ModernUiWidgets.divider(context), ModernUiWidgets.fillWidth());
            root.addView(ModernUiPanels.timelinePanel(context, session, refresher, ruler),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TIMELINE_HEIGHT));
            root.addView(ModernUiWidgets.divider(context), ModernUiWidgets.fillWidth());
            root.addView(ModernUiPanels.statusBar(context, session, refresher), ModernUiWidgets.fillWidth());

            // 控件都搭好之后再开始逐帧把数据灌进去
            refresher.start(root);
            return root;
        }

        /// 三列主体
        private View buildBody(Context context, ModernUiRefresher refresher) {
            LinearLayout body = ModernUiWidgets.row(context);
            body.addView(column(context, 3.0F,
                    ModernUiPanels.animationPanel(context, session, refresher),
                    ModernUiPanels.pathPanel(context, session, refresher)));
            body.addView(column(context, 4.0F,
                    ModernUiPanels.viewportPanel(context, session),
                    ModernUiPanels.graphPanel(context, session, refresher)));
            body.addView(column(context, 3.0F,
                    ModernUiPanels.keyframePanel(context, session, refresher),
                    ModernUiPanels.pathNodePanel(context, session, refresher)));
            return body;
        }

        /// 一列面板：横向按权重占宽，列内各面板平分高度
        private View column(Context context, float weight, View... panels) {
            LinearLayout column = ModernUiWidgets.column(context);
            for (View panel : panels) {
                column.addView(panel, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0F));
            }

            column.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
            return column;
        }

        private void close() {
            Minecraft.getInstance().setScreen(previous);
        }
    }
}
