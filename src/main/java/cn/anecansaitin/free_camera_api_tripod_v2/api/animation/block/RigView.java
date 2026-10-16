package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import org.jspecify.annotations.NullMarked;

/// 机位画面的合成设置：矩形、层级、质量请求与启用开关（主设计 §3.1、§7.6）。
///
/// ## 这是创作数据，不是工作区状态
///
/// `view` 随文档保存、分享和撤销（主设计 D23）。停靠面板的宽度、浮窗位置、时间轴缩放
/// 属于**工作区/客户端视图状态**，写客户端配置，不进文档历史。名字里都带“布局”
/// 不构成共享持久化模型的理由（UI 设计 §8.1）。
///
/// 坐标相对**合成输出画面**，不相对某个停靠面板；视口缩放与留白通过画布变换映射，
/// **不能改写作者矩形**。
///
/// ## 字段分组与理由
///
/// 矩形、层级、质量与开关放在**同一个值**里，对应主设计 §3.1 的
/// `view: enabled / rect / zOrder / resolutionScale / updateEveryFrames`：
/// 它们一起决定“这一路画面怎么合成”，一起进文档、一起撤销，
/// 拆成两个字段会让“只改质量却忘了同步矩形”这类错误在构造期无法发现。
///
/// ## 质量是作者请求，不是执行状态
///
/// `resolutionScale` 与 `updateEveryFrames` 是**作者请求**。宿主可以可见地降级
/// （降分辨率、隔帧复用、暂时停用），但**不改写文档**，也不修改作者的 `enabled`
/// （主设计 D23、§7.6）。运行降级状态只在宿主侧，见 `RigRenderStatus`。
///
/// 隔帧复用意味着该路画面的**采样时刻可能与其他路不同**，因此必须显示旧帧的时间标识，
/// 不能宣称所有路始终是同一瞬间（主设计 §7.6）。
///
/// ## 隐藏用 enabled，不用零尺寸
///
/// [Rect] 的正尺寸是**校验约束**（主设计 §4.2 L4「布局正尺寸」）。
/// 用零尺寸表示隐藏会让“隐藏”与“数据非法”无法区分，因此隐藏只能用 [enabled]。
///
/// @param enabled          是否参与合成。作者启用状态属于**文档编辑**；
///                         宿主因性能停用属于运行状态，不改本字段
/// @param rect             画面矩形，相对合成输出画面
/// @param zOrder           层级；**相同层级按机位文档顺序合成**（主设计 §3.1），
///                         所以顺序本身是数据，不靠 ID 或字典序
/// @param resolutionScale  相对最终画布像素尺寸的分辨率系数，`(0, 1]`；
///                         副机位常用 `0.5`
/// @param updateEveryFrames 每几帧重绘一次，`>= 1`；`1` 表示每帧。
///                         取值大于 1 时该路会复用旧帧，界面必须标注旧帧时刻
@NullMarked
public record RigView(
        boolean enabled,
        Rect rect,
        int zOrder,
        double resolutionScale,
        int updateEveryFrames) {

    /// 默认机位画面：启用、占满输出画面、层级 0、全分辨率、每帧更新。
    public static final RigView FULL = new RigView(true, Rect.full(), 0, 1.0, 1);

    public RigView {
        if (!Double.isFinite(resolutionScale) || resolutionScale <= 0 || resolutionScale > 1) {
            throw new IllegalArgumentException(
                    "RigView resolutionScale must be finite and in (0, 1]: " + resolutionScale);
        }

        if (updateEveryFrames < 1) {
            throw new IllegalArgumentException(
                    "RigView updateEveryFrames must be >= 1: " + updateEveryFrames);
        }
    }

    /// 供界面提示：这一路是否被声明为隔帧更新。
    public boolean reusesFrames() {
        return updateEveryFrames > 1;
    }

    public RigView withEnabled(boolean value) {
        return new RigView(value, rect, zOrder, resolutionScale, updateEveryFrames);
    }

    public RigView withRect(Rect value) {
        return new RigView(enabled, value, zOrder, resolutionScale, updateEveryFrames);
    }

    public RigView withZOrder(int value) {
        return new RigView(enabled, rect, value, resolutionScale, updateEveryFrames);
    }

    public RigView withQuality(double newResolutionScale, int newUpdateEveryFrames) {
        return new RigView(enabled, rect, zOrder, newResolutionScale, newUpdateEveryFrames);
    }

    /// 画面矩形的两种**互斥**定位分支（主设计 §3.1、§9.1 的取舍）。
    ///
    /// - [Normalized]：相对输出画面的 `0~1` 矩形。窗口与分辨率变化后布局不散架，是默认口径。
    /// - [Anchored]：锚定角落 + 像素偏移，满足像素级对齐诉求。窗口变化时**不随比例缩放**，
    ///   这是该分支的故意语义，界面须提示。
    ///
    /// 两者是分支而不是叠加：`Anchored` 的像素偏移以角落为基准，不与归一化比例相乘。
    /// 因此用 sealed 分支表达，而不是“一个矩形加一个可空角落”——后者允许
    /// “既是归一化又带角落”这种无法解释的组合存在。
    @NullMarked
    public sealed interface Rect permits Rect.Normalized, Rect.Anchored {

        /// 输出画面的四个角。
        enum Corner {
            TOP_LEFT,
            TOP_RIGHT,
            BOTTOM_LEFT,
            BOTTOM_RIGHT
        }

        /// 归一化矩形：相对输出画面的 `0~1` 坐标。默认口径。
        ///
        /// 越界部分由合成宿主裁剪，但**非法数据仍要校验**：有限坐标与正尺寸是 L4 领域约束。
        /// 这里只拒绝非有限值与非正尺寸；超出 `0~1` 属于“可以裁掉”的正常情形，不拒绝
        /// （作者可以把画面部分移出画布，宿主负责裁剪）。
        ///
        /// @param x      左上角横坐标
        /// @param y      左上角纵坐标
        /// @param width  宽度，必须为正
        /// @param height 高度，必须为正
        record Normalized(double x, double y, double width, double height) implements Rect {
            public Normalized {
                requireFinite(x, "x");
                requireFinite(y, "y");
                requirePositive(width, "width");
                requirePositive(height, "height");
            }
        }

        /// 锚定角落 + 像素偏移。
        ///
        /// @param corner 基准角落
        /// @param x      相对角落的横向像素偏移，可为负
        /// @param y      相对角落的纵向像素偏移，可为负
        /// @param width  宽度，像素，必须为正
        /// @param height 高度，像素，必须为正
        record Anchored(Corner corner, double x, double y, double width, double height) implements Rect {
            public Anchored {
                requireFinite(x, "x");
                requireFinite(y, "y");
                requirePositive(width, "width");
                requirePositive(height, "height");
            }
        }

        /// 供界面提示“此模式是否随窗口缩放”。
        default boolean scalesWithWindow() {
            return this instanceof Normalized;
        }

        // region 预设

        /// 占满输出画面。
        static Normalized full() {
            return new Normalized(0, 0, 1, 1);
        }

        /// 左右分屏的左半。
        static Normalized leftHalf() {
            return new Normalized(0, 0, 0.5, 1);
        }

        /// 左右分屏的右半。
        static Normalized rightHalf() {
            return new Normalized(0.5, 0, 0.5, 1);
        }

        /// 画中画：右下角一块。
        static Normalized pictureInPicture() {
            return new Normalized(0.68, 0.62, 0.3, 0.34);
        }

        /// 四宫格中的一格，`index` 取 0~3，顺序为左上、右上、左下、右下。
        static Normalized quadrant(int index) {
            if (index < 0 || index > 3) {
                throw new IllegalArgumentException("Quadrant index must be 0..3: " + index);
            }

            return new Normalized((index % 2) * 0.5, (index / 2) * 0.5, 0.5, 0.5);
        }

        // endregion

        private static void requireFinite(double value, String name) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("RigView.Rect " + name + " must be finite: " + value);
            }
        }

        private static void requirePositive(double value, String name) {
            if (!Double.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException("RigView.Rect " + name + " must be finite and positive: " + value);
            }
        }
    }
}
