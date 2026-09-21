package cn.anecansaitin.free_camera_api_tripod;

import net.neoforged.neoforge.common.ModConfigSpec;

/// 编辑器界面的客户端配置：只负责记住窗口布局，让下次打开编辑器时保持原样。
///
/// 布局在编辑器关闭时写入，因此拖拽过程中不会反复落盘。
public final class EditorConfig {
    /// 默认上排列布局，与 {@code CameraEditorScreen} 的初始比例保持一致
    public static final String DEFAULT_COLUMNS = "viewport:0.34,graph:0.36,inspector:0.30";
    public static final double DEFAULT_BOTTOM_HEIGHT = 0.28;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.ConfigValue<String> LAYOUT_COLUMNS;
    public static final ModConfigSpec.DoubleValue LAYOUT_BOTTOM_HEIGHT;
    public static final ModConfigSpec.ConfigValue<String> LAYOUT_COLLAPSED;
    public static final ModConfigSpec.BooleanValue VIEWPORT_HINTS_COLLAPSED;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(" Camera animation editor window layout.",
                        " Written when the editor closes; delete an entry to fall back to the default.")
                .push("layout");
        LAYOUT_COLUMNS = BUILDER.comment(" Top row panels from left to right, as \"id:widthWeight\".",
                        " Example: " + DEFAULT_COLUMNS)
                .define("columns", DEFAULT_COLUMNS);
        LAYOUT_BOTTOM_HEIGHT = BUILDER.comment(" Bottom row height as a fraction of the editor content height.")
                .defineInRange("bottom_height", DEFAULT_BOTTOM_HEIGHT, 0.0, 1.0);
        LAYOUT_COLLAPSED = BUILDER.comment(" Ids of collapsed panels, comma separated, empty for none.",
                        " Example: inspector,timeline")
                .define("collapsed", "");
        VIEWPORT_HINTS_COLLAPSED = BUILDER.comment(" Whether the viewport hides its operation hint block.")
                .define("viewport_hints_collapsed", false);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private EditorConfig() {
    }

    public static void save() {
        SPEC.save();
    }
}
