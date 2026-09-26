package cn.anecansaitin.free_camera_api_tripod;

import net.neoforged.neoforge.common.ModConfigSpec;

/// 编辑器界面的客户端配置：只负责记住窗口布局，让下次打开编辑器时保持原样。
///
/// 布局在编辑器关闭时写入，因此拖拽过程中不会反复落盘。
public final class EditorConfig {
    /// 默认布局，与 {@code CameraEditorScreen} 的初始面板与比例保持一致。
    ///
    /// 整串由 {@code ;} 分成若干段，段为 {@code 键=值}：
    /// {@code v} 为布局版本，与代码里的版本不符时整串作废并回落到本默认值；
    /// {@code top} / {@code bottom} 表示上排与下排，排内单元用 {@code |} 分隔，
    /// 单元写作 {@code 单元权重:面板:叠放权重+面板:叠放权重}（单元内自上而下叠放）；
    /// {@code float} 表示悬浮窗口，用 {@code ,} 分隔，写作 {@code id:x:y:宽:高}。
    public static final String DEFAULT_LAYOUT =
            "v=3;top=0.3000:viewport:1.0000|0.4000:animation:1.0000+path_node:0.3400+graph:0.6600"
                    + "|0.3000:keyframe:0.6500+variables:0.3500;bottom=1.0000:timeline:1.0000;float=";
    public static final double DEFAULT_BOTTOM_HEIGHT = 0.28;
    /// 路径编辑器的默认布局，与 {@code PathEditorScreen} 的初始面板与比例保持一致
    /// （视口 / 节点列表 / 节点详情 / 路径信息 四列，没有下排）。格式同上。
    public static final String DEFAULT_PATH_LAYOUT =
            "v=3;top=0.3000:viewport:1.0000|0.2200:path_nodes:1.0000|0.2800:path_detail:1.0000"
                    + "|0.2000:path_info:1.0000;bottom=;float=";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.ConfigValue<String> LAYOUT;
    public static final ModConfigSpec.DoubleValue LAYOUT_BOTTOM_HEIGHT;
    public static final ModConfigSpec.ConfigValue<String> LAYOUT_COLLAPSED;
    public static final ModConfigSpec.BooleanValue VIEWPORT_HINTS_COLLAPSED;
    public static final ModConfigSpec.ConfigValue<String> PATH_LAYOUT;
    public static final ModConfigSpec.ConfigValue<String> PATH_LAYOUT_COLLAPSED;
    public static final ModConfigSpec.BooleanValue DEV_TEST_KEYS;
    /// 编辑器是否使用深色主题；在「视图 → 深色模式」里切换
    public static final ModConfigSpec.BooleanValue DARK_MODE;
    /// 编辑器界面是否优先使用 Modern UI 渲染（客户端装了 Modern UI 时才生效）
    public static final ModConfigSpec.BooleanValue MODERN_UI_COMPAT;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(" Camera animation editor window layout.",
                        " Written when the editor closes; delete an entry to fall back to the default.")
                .push("layout");
        LAYOUT = BUILDER.comment(" Docked panels, their stacking and the floating windows.",
                        " Sections separated by ';': v / top / bottom / float.",
                        " 'v' is the layout version; a saved layout from another version is discarded",
                        " so the editor falls back to the current default.",
                        " A row is cells separated by '|'; a cell is \"weight:id:stackWeight+id:stackWeight\".",
                        " A floating window is \"id:x:y:width:height\", separated by ','.",
                        " Example: " + DEFAULT_LAYOUT)
                .define("dock", DEFAULT_LAYOUT);
        LAYOUT_BOTTOM_HEIGHT = BUILDER.comment(" Bottom row height as a fraction of the editor content height.")
                .defineInRange("bottom_height", DEFAULT_BOTTOM_HEIGHT, 0.0, 1.0);
        LAYOUT_COLLAPSED = BUILDER.comment(" Ids of collapsed panels, comma separated, empty for none.",
                        " Example: viewport,timeline")
                .define("collapsed", "");
        VIEWPORT_HINTS_COLLAPSED = BUILDER.comment(" Whether the viewport hides its operation hint block.")
                .define("viewport_hints_collapsed", false);
        PATH_LAYOUT = BUILDER.comment(" The path editor's own layout string, same format as 'dock'.",
                        " Kept separate from the main editor so the two screens can be arranged independently.",
                        " Example: " + DEFAULT_PATH_LAYOUT)
                .define("path_dock", DEFAULT_PATH_LAYOUT);
        PATH_LAYOUT_COLLAPSED = BUILDER.comment(" Ids of the path editor's collapsed panels, comma separated.",
                        " Example: path_info,path_detail")
                .define("path_collapsed", "");
        DARK_MODE = BUILDER.comment(" Dark theme for the editor UI. Turn it off for the light theme.",
                        " Toggled from the editor menu: View -> Dark Mode.")
                .define("dark_mode", true);
        BUILDER.pop();

        BUILDER.comment(" Editor user interface.").push("ui");
        MODERN_UI_COMPAT = BUILDER.comment(" Render the editor screens with the Modern UI framework when it is installed.",
                        " Requires Modern UI 3.13+ on the client;",
                        " without it (or with this off) the editor keeps its built-in renderer.")
                .define("modern_ui_compat", true);
        BUILDER.pop();

        BUILDER.comment(" Developer helpers. Leave everything here off for normal use.").push("dev");
        DEV_TEST_KEYS = BUILDER.comment(" Enable the developer test keys in the camera editor:",
                        " F9 pans the timeline one second to the left (checks the 0s left bound),",
                        " F10 delivers a right click at the current cursor position (drives context menus",
                        " from scripts, which cannot send a right click themselves),",
                        " F12 toggles the full-screen world view.")
                .define("test_keys", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private EditorConfig() {
    }

    public static void save() {
        SPEC.save();
    }
}
