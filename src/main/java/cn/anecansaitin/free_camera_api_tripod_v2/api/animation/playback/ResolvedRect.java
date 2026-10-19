package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// 已求值的合成矩形：一帧内 `x / y / width / height` 的**具体数值**（主设计 §3.1.2、§7.2）。
///
/// ## 与作者数据的区别
///
/// 作者数据 [RigView.Rect] 的分量是 `AnimatedNumber`：可能含公式或数值曲线，
/// **不是一个数**。准备阶段必须把它们求值到本帧的一个具体矩形，准备好之后合成器
/// 才不需要再读当前文档或再跑一次求值（主设计 §7.2：执行与合成不重新读取当前 store）。
///
/// 两者分开还有一个好处：**非法值只能出现在求值结果上**，不能藏进作者数据里。
/// 主设计 §3.1.2 规定“静态存在合法键不等于连续区间合法”——曲线过冲与公式产生的
/// 非法中间帧属于运行期错误，必须终止整场。本类型的构造器因此拒绝非有限值与
/// 非正宽高，让非法求值结果无法伪装成合法准备帧。
///
/// ## 坐标口径保持不变
///
/// 分支与作者矩形一一对应：归一化仍是相对输出画布的 `0~1` 分数，锚定仍是角落 + 像素。
/// 求值只把“可能变的分量”变成数，**不改变坐标口径**，也不做画布映射：
/// 分数 / 像素到最终画布矩形的映射由合成器按 [CompositionRequest] 的输出尺寸完成。
/// 这样准备帧与宿主分辨率解耦，窗口变化时不必让准备帧失效（主设计 §7.6）。
///
/// ## 越界
///
/// 坐标超出画布属于正常情形（作者可以把画面部分移出画布），由合成层裁剪；
/// 本类型只保证有限与正尺寸，不保证落在画布内。
@NullMarked
public sealed interface ResolvedRect permits ResolvedRect.Normalized, ResolvedRect.Anchored {

    /// 归一化矩形：相对输出画布的 `0~1` 坐标。
    ///
    /// @param x      左上角横坐标
    /// @param y      左上角纵坐标
    /// @param width  宽度，必须有限且为正
    /// @param height 高度，必须有限且为正
    record Normalized(double x, double y, double width, double height) implements ResolvedRect {
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
    /// @param width  宽度，像素，必须有限且为正
    /// @param height 高度，像素，必须有限且为正
    record Anchored(RigView.Rect.Corner corner, double x, double y, double width, double height)
            implements ResolvedRect {
        public Anchored {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requirePositive(width, "width");
            requirePositive(height, "height");
        }
    }

    /// 供宿主提示：该矩形是否随窗口缩放。
    default boolean scalesWithWindow() {
        return this instanceof Normalized;
    }

    /// 是否为归一化口径。合成器据此决定是否需要乘输出尺寸。
    default boolean normalized() {
        return this instanceof Normalized;
    }

    /// 归一化矩形；锚定口径返回空。
    default Optional<Normalized> asNormalized() {
        return this instanceof Normalized normalized ? Optional.of(normalized) : Optional.empty();
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("ResolvedRect " + name + " must be finite: " + value);
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("ResolvedRect " + name + " must be finite and positive: " + value);
        }
    }
}
