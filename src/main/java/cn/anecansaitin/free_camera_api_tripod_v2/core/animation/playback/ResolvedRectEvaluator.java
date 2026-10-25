package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;

/// 把作者矩形 [RigView.Rect] 的四个 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber]
/// 求值成本帧的 [ResolvedRect]。
///
/// ## 只做求值，不做画布映射
///
/// 两种分支的坐标口径保持不变：
///
/// - [RigView.Rect.Normalized] 仍是相对输出画布的 `0~1` 分数；
/// - [RigView.Rect.Anchored] 仍是角落 + 像素偏移。
///
/// 分数 / 像素到最终像素矩形的映射属于合成阶段，按合成输出尺寸完成；本类不乘分辨率、
/// 不裁剪、也不判断矩形是否落在画布内。超出画布是正常情形，由合成层裁剪。
///
/// ## 宽高必须为正
///
/// 作者数据的构造器只能检查**静态可判定**的值；曲线过冲或公式可以产生非正宽高。
/// 本类在每一帧对四个分量求值后再次检查：宽度、高度必须是有限正数。检查失败返回
/// [Result.Failure] 与 `layout_rect_invalid` 诊断，**不抛出领域异常**——运行期错误要由
/// 准备阶段收集成整帧失败，而不是让调用方用 `try` / `catch` 猜测。`x` / `y` 允许为负
/// 或超出 `0~1`。
///
/// 失败即停止，不返回“部分求值”的矩形：半合法矩形不能伪装成准备帧。
@NullMarked
public final class ResolvedRectEvaluator {

    private final AnimatedNumberEvaluator numbers;

    /// @param numbers 布局数值求值器，非空
    public ResolvedRectEvaluator(AnimatedNumberEvaluator numbers) {
        this.numbers = Objects.requireNonNull(numbers, "numbers");
    }

    /// 求值一个作者矩形。
    ///
    /// @param rect  作者矩形，非空
    /// @param input 本帧显式输入，非空；四个分量只切换 [LayoutEvalInput#field]
    /// @return 已求值矩形或结构化失败
    public Result evaluate(RigView.Rect rect, LayoutEvalInput input) {
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(input, "input");

        return switch (rect) {
            case RigView.Rect.Normalized normalized -> evaluateNormalized(normalized, input);
            case RigView.Rect.Anchored anchored -> evaluateAnchored(anchored, input);
        };
    }

    private Result evaluateNormalized(RigView.Rect.Normalized rect, LayoutEvalInput input) {
        LayoutValue x = numbers.evaluate(rect.x(), input.withField(ExpressionSource.LayoutField.X));

        if (x instanceof LayoutValue.Failure failure) {
            return new Result.Failure(failure.diagnostic());
        }

        LayoutValue y = numbers.evaluate(rect.y(), input.withField(ExpressionSource.LayoutField.Y));

        if (y instanceof LayoutValue.Failure failure) {
            return new Result.Failure(failure.diagnostic());
        }

        LayoutValue width = numbers.evaluate(rect.width(),
                input.withField(ExpressionSource.LayoutField.WIDTH));

        if (width instanceof LayoutValue.Failure failure) {
            return new Result.Failure(failure.diagnostic());
        }

        double widthValue = ((LayoutValue.Success) width).value();

        if (!(widthValue > 0.0)) {
            return new Result.Failure(LayoutDiagnostics.rectInvalid(
                    input.withField(ExpressionSource.LayoutField.WIDTH),
                    "layout rect width must be finite and positive: " + widthValue));
        }

        LayoutValue height = numbers.evaluate(rect.height(),
                input.withField(ExpressionSource.LayoutField.HEIGHT));

        if (height instanceof LayoutValue.Failure failure) {
            return new Result.Failure(failure.diagnostic());
        }

        double heightValue = ((LayoutValue.Success) height).value();

        if (!(heightValue > 0.0)) {
            return new Result.Failure(LayoutDiagnostics.rectInvalid(
                    input.withField(ExpressionSource.LayoutField.HEIGHT),
                    "layout rect height must be finite and positive: " + heightValue));
        }

        return new Result.Success(new ResolvedRect.Normalized(
                ((LayoutValue.Success) x).value(),
                ((LayoutValue.Success) y).value(),
                widthValue,
                heightValue));
    }

    private Result evaluateAnchored(RigView.Rect.Anchored rect, LayoutEvalInput input) {
        LayoutValue x = numbers.evaluate(rect.x(), input.withField(ExpressionSource.LayoutField.X));

        if (x instanceof LayoutValue.Failure failure) {
            return new Result.Failure(failure.diagnostic());
        }

        LayoutValue y = numbers.evaluate(rect.y(), input.withField(ExpressionSource.LayoutField.Y));

        if (y instanceof LayoutValue.Failure failure) {
            return new Result.Failure(failure.diagnostic());
        }

        LayoutValue width = numbers.evaluate(rect.width(),
                input.withField(ExpressionSource.LayoutField.WIDTH));

        if (width instanceof LayoutValue.Failure failure) {
            return new Result.Failure(failure.diagnostic());
        }

        double widthValue = ((LayoutValue.Success) width).value();

        if (!(widthValue > 0.0)) {
            return new Result.Failure(LayoutDiagnostics.rectInvalid(
                    input.withField(ExpressionSource.LayoutField.WIDTH),
                    "layout rect width must be finite and positive: " + widthValue));
        }

        LayoutValue height = numbers.evaluate(rect.height(),
                input.withField(ExpressionSource.LayoutField.HEIGHT));

        if (height instanceof LayoutValue.Failure failure) {
            return new Result.Failure(failure.diagnostic());
        }

        double heightValue = ((LayoutValue.Success) height).value();

        if (!(heightValue > 0.0)) {
            return new Result.Failure(LayoutDiagnostics.rectInvalid(
                    input.withField(ExpressionSource.LayoutField.HEIGHT),
                    "layout rect height must be finite and positive: " + heightValue));
        }

        return new Result.Success(new ResolvedRect.Anchored(
                rect.corner(),
                ((LayoutValue.Success) x).value(),
                ((LayoutValue.Success) y).value(),
                widthValue,
                heightValue));
    }

    /// 矩形求值结果。
    ///
    /// 与 [LayoutValue] 一样只有成功 / 失败两个分支；失败必须带结构化诊断。
    @NullMarked
    public sealed interface Result permits Result.Success, Result.Failure {

        /// 已求值的合法矩形。
        ///
        /// @param rect 宽高有限为正的矩形，非空
        record Success(ResolvedRect rect) implements Result {
            public Success {
                Objects.requireNonNull(rect, "rect");
            }
        }

        /// 求值失败。
        ///
        /// @param diagnostic 结构化诊断，非空
        record Failure(Diagnostic diagnostic) implements Result {
            public Failure {
                Objects.requireNonNull(diagnostic, "diagnostic");
            }
        }

        /// 是否成功。
        default boolean succeeded() {
            return this instanceof Success;
        }

        /// 是否失败。
        default boolean failed() {
            return this instanceof Failure;
        }

        /// 成功时的矩形；失败时为空。
        default Optional<ResolvedRect> optionalRect() {
            return this instanceof Success success
                    ? Optional.of(success.rect())
                    : Optional.empty();
        }

        /// 失败时的诊断；成功时为空。
        default Optional<Diagnostic> optionalDiagnostic() {
            return this instanceof Failure failure
                    ? Optional.of(failure.diagnostic())
                    : Optional.empty();
        }
    }
}
