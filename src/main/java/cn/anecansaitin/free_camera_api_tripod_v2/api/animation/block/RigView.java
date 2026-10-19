package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
import org.jspecify.annotations.NullMarked;

/// 机位画面的合成设置：启用状态、活动轨道、矩形、层级与质量请求（主设计 §3.1、§7.6）。
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
/// ## 三个“启用”不能混为一谈
///
/// - [enabled]：静态作者总开关，属于文档编辑；
/// - [active]：本时刻活动状态，是有类型 STEP 轨道（`KeyframeTrack<Boolean>`，默认 `true`）；
/// - 宿主性能停路 / 降级：运行状态，可见但**不改写文档**（D23、§7.6）。
///
/// 运行活动状态为 `enabled && active.at(t)`。不活动机位不主动准备姿态或渲染；
/// 指向它的 `TrackRef` 仍按实际依赖求值（主设计 §7.2）。
///
/// ## 隐藏用 enabled，不用零尺寸
///
/// [Rect] 的正尺寸是**校验约束**（主设计 §4.2 L4「布局正尺寸」）。
/// 用零尺寸表示隐藏会让“隐藏”与“数据非法”无法区分，因此隐藏只能用 [enabled]；
/// 随时间隐藏则用 [active] 的键，而不是把尺寸缩到 0。
///
/// ## 矩形可随时间变化
///
/// 主设计 §3.1.2：矩形的 `x / y / width / height` 是 [AnimatedNumber]——常量值源或数值曲线，
/// 复用数值通道的编译、关键帧与插值规则，**没有布局专用的隐式求值路径**。
///
/// - 空曲线使用显式默认值；非空曲线首末之外保持端点（`CLAMP`）。
/// - 布局数值**不通过 `TrackRef` 对外提供读数**（它不是轨道，没有 `TrackId`）。
/// - 静态存在合法键不等于连续区间合法：曲线过冲与公式产生的非法中间帧属于**运行期错误**，
///   终止整场；试算不能替代逐帧检查（§3.1.2）。
/// - 两种坐标模式**静态互斥**：分支本身不可动画。
///
/// 布局曲线（非空）计入播放时长：静态属性与末键保持都不产生无限时长（主设计 §3.1）。
///
/// ## 质量是作者请求，不是执行状态
///
/// `resolutionScale` 与 `updateEveryFrames` 是**作者请求**。宿主可以可见地降级
/// （降分辨率、隔帧复用、暂时停用），但**不改写文档**，也不修改作者的 [enabled]
/// （主设计 D23、§7.6）。隔帧复用意味着该路画面的采样时刻可能与其他路不同，
/// 必须显示旧帧的时间标识，不能宣称所有路始终是同一瞬间。
///
/// @param enabled          静态作者总开关；是否参与合成。停用不改矩形与轨道
/// @param active           本时刻活动状态轨道；默认 `true`
/// @param rect             画面矩形，相对合成输出画面；分量可随时间变化
/// @param zOrder           层级；**相同层级按机位文档顺序合成**（主设计 §3.1），
///                         所以顺序本身是数据，不靠 ID 或字典序
/// @param resolutionScale  相对最终画布像素尺寸的分辨率系数，`(0, 1]`；
///                         副机位常用 `0.5`
/// @param updateEveryFrames 每几帧重绘一次，`>= 1`；`1` 表示每帧。
///                         取值大于 1 时该路会复用旧帧，界面必须标注旧帧时刻
@NullMarked
public record RigView(
        boolean enabled,
        ActiveTrack active,
        Rect rect,
        int zOrder,
        double resolutionScale,
        int updateEveryFrames) {

    /// 默认画面：启用、默认活动、占满输出画面、层级 0、全分辨率、每帧更新。
    ///
    /// 活动轨道必须带文档内唯一的 `TrackId`，因此默认画面由 [full] 按轨道构造，
    /// 而不是一个可以直接共享的静态常量。
    public static RigView full(ActiveTrack active) {
        return new RigView(true, active, Rect.full(), 0, 1.0, 1);
    }

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

    /// 布局轨道对文档时长的贡献：四个分量曲线末键时刻的最大值。
    ///
    /// 常量分量与空曲线贡献 0；静态属性不延长动画（主设计 §3.1）。
    public float layoutDuration() {
        return rect.endTime();
    }

    public RigView withEnabled(boolean value) {
        return new RigView(value, active, rect, zOrder, resolutionScale, updateEveryFrames);
    }

    /// 换活动轨道；静态总开关与矩形不变。
    public RigView withActive(ActiveTrack value) {
        return new RigView(enabled, value, rect, zOrder, resolutionScale, updateEveryFrames);
    }

    public RigView withRect(Rect value) {
        return new RigView(enabled, active, value, zOrder, resolutionScale, updateEveryFrames);
    }

    public RigView withZOrder(int value) {
        return new RigView(enabled, active, rect, value, resolutionScale, updateEveryFrames);
    }

    public RigView withQuality(double newResolutionScale, int newUpdateEveryFrames) {
        return new RigView(enabled, active, rect, zOrder, newResolutionScale, newUpdateEveryFrames);
    }

    /// 画面矩形的两种**互斥**定位分支（主设计 §3.1、§9.1 的取舍）。
    ///
    /// - [Normalized]：相对输出画面的 `0~1` 坐标。窗口与分辨率变化后布局不散架，是默认口径。
    /// - [Anchored]：锚定角落 + 像素偏移，满足像素级对齐诉求。窗口变化时**不随比例缩放**，
    ///   这是该分支的故意语义，界面须提示。
    ///
    /// 两者是分支而不是叠加：`Anchored` 的像素偏移以角落为基准，不与归一化比例相乘。
    /// 分支本身**静态互斥、不可动画**（主设计 §3.1.2）。
    @NullMarked
    public sealed interface Rect permits Rect.Normalized, Rect.Anchored {
        /// 结构编码：按 `mode` 分派；两种坐标模式静态互斥（主设计 §3.1.2）。
        Codec<String> MODE = Codecs.variant("normalized", "anchored");

        /// 画布矩形的分派 Codec。
        Codec<Rect> CODEC = MODE.<Rect>partialDispatch(
                "mode",
                rect -> DataResult.success(rect instanceof Rect.Anchored ? "anchored" : "normalized"),
                name -> switch (name) {
                    case "normalized" -> DataResult.<MapCodec<? extends Rect>>success(
                            RecordCodecBuilder.<Rect.Normalized>mapCodec(instance -> instance.group(
                                    AnimatedNumber.CODEC.fieldOf("x").forGetter(Rect.Normalized::x),
                                    AnimatedNumber.CODEC.fieldOf("y").forGetter(Rect.Normalized::y),
                                    AnimatedNumber.CODEC.fieldOf("width").forGetter(Rect.Normalized::width),
                                    AnimatedNumber.CODEC.fieldOf("height").forGetter(Rect.Normalized::height)
                            ).apply(instance, Rect.Normalized::new)));
                    case "anchored" -> DataResult.<MapCodec<? extends Rect>>success(
                            RecordCodecBuilder.<Rect.Anchored>mapCodec(instance -> instance.group(
                                    Codecs.enumByName(Corner.class).fieldOf("corner")
                                            .forGetter(Rect.Anchored::corner),
                                    AnimatedNumber.CODEC.fieldOf("x").forGetter(Rect.Anchored::x),
                                    AnimatedNumber.CODEC.fieldOf("y").forGetter(Rect.Anchored::y),
                                    AnimatedNumber.CODEC.fieldOf("width").forGetter(Rect.Anchored::width),
                                    AnimatedNumber.CODEC.fieldOf("height").forGetter(Rect.Anchored::height)
                            ).apply(instance, Rect.Anchored::new)));
                    default -> DataResult.error(() -> "Unknown rect mode: " + name);
                });

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
        /// 静态可判定的部分（常量值源）在构造时检查；公式与曲线产生的值留到运行期逐帧检查
        /// （主设计 §3.1.2）。超出 `0~1` 属于“可以裁掉”的正常情形，不拒绝。
        ///
        /// @param x      左上角横坐标
        /// @param y      左上角纵坐标
        /// @param width  宽度，静态为正
        /// @param height 高度，静态为正
        record Normalized(AnimatedNumber x, AnimatedNumber y, AnimatedNumber width, AnimatedNumber height)
                implements Rect {
            public Normalized {
                requireStaticFinite(x, "x");
                requireStaticFinite(y, "y");
                requireStaticPositive(width, "width");
                requireStaticPositive(height, "height");
            }
        }

        /// 锚定角落 + 像素偏移。
        ///
        /// @param corner 基准角落
        /// @param x      相对角落的横向像素偏移，可为负
        /// @param y      相对角落的纵向像素偏移，可为负
        /// @param width  宽度，像素，静态为正
        /// @param height 高度，像素，静态为正
        record Anchored(Corner corner, AnimatedNumber x, AnimatedNumber y,
                        AnimatedNumber width, AnimatedNumber height) implements Rect {
            public Anchored {
                requireStaticFinite(x, "x");
                requireStaticFinite(y, "y");
                requireStaticPositive(width, "width");
                requireStaticPositive(height, "height");
            }
        }

        /// 供界面提示“此模式是否随窗口缩放”。
        default boolean scalesWithWindow() {
            return this instanceof Normalized;
        }

        /// 四个分量的曲线末键时刻的最大值；常量分量与空曲线贡献 0。
        ///
        /// 布局曲线计入播放时长（主设计 §3.1）；静态矩形不延长动画。
        default float endTime() {
            return switch (this) {
                case Normalized n -> maxEndTime(n.x(), n.y(), n.width(), n.height());
                case Anchored a -> maxEndTime(a.x(), a.y(), a.width(), a.height());
            };
        }

        /// 是否为全静态矩形：四个分量都可在无时间与求值上下文时确定取值。
        ///
        /// 供静态校验与界面标记“静态 / 动画”使用；运行期仍需逐帧检查。
        default boolean isStatic() {
            return switch (this) {
                case Normalized n -> n.x().isStatic() && n.y().isStatic()
                        && n.width().isStatic() && n.height().isStatic();
                case Anchored a -> a.x().isStatic() && a.y().isStatic()
                        && a.width().isStatic() && a.height().isStatic();
            };
        }

        // region 预设

        /// 占满输出画面。
        static Normalized full() {
            return normalized(0f, 0f, 1f, 1f);
        }

        /// 左右分屏的左半。
        static Normalized leftHalf() {
            return normalized(0f, 0f, 0.5f, 1f);
        }

        /// 左右分屏的右半。
        static Normalized rightHalf() {
            return normalized(0.5f, 0f, 0.5f, 1f);
        }

        /// 画中画：右下角一块。
        static Normalized pictureInPicture() {
            return normalized(0.68f, 0.62f, 0.3f, 0.34f);
        }

        /// 四宫格中的一格，`index` 取 0~3，顺序为左上、右上、左下、右下。
        static Normalized quadrant(int index) {
            if (index < 0 || index > 3) {
                throw new IllegalArgumentException("Quadrant index must be 0..3: " + index);
            }

            return normalized((index % 2) * 0.5f, (index / 2) * 0.5f, 0.5f, 0.5f);
        }

        // endregion

        // region 校验

        private static void requireStaticFinite(AnimatedNumber number, String name) {
            number.staticValue().ifPresent(value -> {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "RigView.Rect " + name + " must be finite: " + value);
                }
            });
        }

        private static void requireStaticPositive(AnimatedNumber number, String name) {
            number.staticValue().ifPresent(value -> {
                if (!Float.isFinite(value) || value <= 0f) {
                    throw new IllegalArgumentException(
                            "RigView.Rect " + name + " must be finite and positive: " + value);
                }
            });
        }

        private static Normalized normalized(float x, float y, float width, float height) {
            return new Normalized(
                    AnimatedNumber.constant(x), AnimatedNumber.constant(y),
                    AnimatedNumber.constant(width), AnimatedNumber.constant(height));
        }

        private static float maxEndTime(AnimatedNumber... components) {
            float end = 0f;

            for (AnimatedNumber component : components) {
                end = Math.max(end, component.endTime());
            }

            return end;
        }

        // endregion
    }

    /// 结构编码：`enabled / active / rect / z_order / resolution_scale / update_every_frames`。
    ///
    /// 层级与质量是静态配置；矩形分量可动画（主设计 §3.1.2）。
    public static final MapCodec<RigView> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(RigView::enabled),
            ActiveTrack.CODEC.fieldOf("active").forGetter(RigView::active),
            Rect.CODEC.fieldOf("rect").forGetter(RigView::rect),
            Codecs.strictInt().fieldOf("z_order").forGetter(RigView::zOrder),
            Codecs.finiteDouble().fieldOf("resolution_scale").forGetter(RigView::resolutionScale),
            Codecs.strictInt().fieldOf("update_every_frames").forGetter(RigView::updateEveryFrames)
    ).apply(instance, RigView::new));

    /// 独立解析画面设置时使用的 Codec。
    public static final Codec<RigView> CODEC = MAP_CODEC.codec();
}
