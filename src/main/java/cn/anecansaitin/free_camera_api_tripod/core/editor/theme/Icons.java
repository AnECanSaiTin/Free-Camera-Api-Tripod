package cn.anecansaitin.free_camera_api_tripod.core.editor.theme;

/// 界面图标。
///
/// 直接复用字体自带的符号字形，不引入额外纹理资源。
/// 字符范围限定在 ASCII 与常用符号区（拉丁补充、标点、箭头、几何图形、
/// 数学运算符），这些码位在原版默认字体及其 Unifont 回退里都有字形。
public final class Icons {
    /// 播放 ▶
    public static final String PLAY = /*"▶"*/"▶";
    /// 暂停 ‖
    public static final String PAUSE = /*"‖"*/"⏸";
    /// 停止 ■
    public static final String STOP = "■";
    /// 新增 +
    public static final String ADD = "+";
    /// 删除 −
    public static final String REMOVE = "−";
    /// 关闭 ×
    public static final String CLOSE = "×";
    /// 重置 ↺
    public static final String RESET = "↺";
    /// 视角切换 ◎
    public static final String VIEW = "◎";
    /// 旋转 ↻
    public static final String ROTATE = "↻";
    /// 视场角 ∠
    public static final String FOV = "∠";
    /// 速度提升 ▲
    public static final String SPEED_UP = "▲";
    /// 速度降低 ▼
    public static final String SPEED_DOWN = "▼";
    /// 展开（提示区收起时用） ▸
    public static final String EXPAND = "▸";
    /// 收起（提示区展开时用） ▾
    public static final String COLLAPSE = "▾";
    /// 适配视图 ⤢
    public static final String FIT = "⤢";
    /// 放大 +
    public static final String ZOOM_IN = "+";
    /// 缩小 −
    public static final String ZOOM_OUT = "−";
    /// 吸附开关 ⊞
    public static final String SNAP = "⊞";
    /// 轨道
    public static final String TRACK = "≣";
    /// 关键帧 ◆
    public static final String KEY = "◆";

    private Icons() {
    }
}
