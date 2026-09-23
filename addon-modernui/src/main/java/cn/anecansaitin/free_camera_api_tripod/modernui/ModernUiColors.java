package cn.anecansaitin.free_camera_api_tripod.modernui;

/// 附属 mod 自己的配色。
///
/// 主 mod 的主题（深/浅色）在它的内部包里、不对外暴露，所以附属 mod 用自己的一套配色，
/// 这样也便于以后直接接 Modern UI 的主题与深浅色。
final class ModernUiColors {
    static final int TEXT = 0xFFE8E8EC;
    static final int TEXT_DIM = 0xFFA8A8B0;
    static final int TEXT_DISABLED = 0xFF6A6A74;
    static final int PANEL_BG = 0xFF232329;
    static final int HEADER_BG = 0xFF2C2C34;
    static final int CANVAS_BG = 0xFF1B1B21;
    static final int BORDER = 0xFF3A3A44;
    static final int GRID = 0xFF34343E;
    static final int ACCENT = 0xFF6FA8FF;
    static final int PLAYHEAD = 0xFFFF7A6B;
    static final int ROW_SELECTED = 0xFF2A4A78;
    static final int NODE = 0xFF9AD8FF;

    private ModernUiColors() {
    }
}
