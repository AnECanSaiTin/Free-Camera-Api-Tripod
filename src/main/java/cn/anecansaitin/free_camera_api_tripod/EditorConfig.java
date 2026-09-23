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
            "v=2;top=0.3000:viewport:1.0000|0.4000:animation:1.0000+path_node:0.3400+graph:0.6600"
                    + "|0.3000:keyframe:1.0000;bottom=1.0000:timeline:1.0000;float=";
    public static final double DEFAULT_BOTTOM_HEIGHT = 0.28;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.ConfigValue<String> LAYOUT;
    public static final ModConfigSpec.DoubleValue LAYOUT_BOTTOM_HEIGHT;
    public static final ModConfigSpec.ConfigValue<String> LAYOUT_COLLAPSED;
    public static final ModConfigSpec.BooleanValue VIEWPORT_HINTS_COLLAPSED;
    public static final ModConfigSpec.BooleanValue DEV_TEST_KEYS;
    /// 编辑器是否使用深色主题；在「视图 → 深色模式」里切换
    public static final ModConfigSpec.BooleanValue DARK_MODE;
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
        DARK_MODE = BUILDER.comment(" Dark theme for the editor UI. Turn it off for the light theme.",
                        " Toggled from the editor menu: View -> Dark Mode.")
                .define("dark_mode", true);
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
