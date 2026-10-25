package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 曲线采样用的纯数值工具：时间 wrap、段内归一化与 STEP / LINEAR / HERMITE 求值。
///
/// 本类只接收已经解析好的数值，不读取 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe]
/// 的取值来源：常量 / 公式的判断与诊断由 [ConstantCurveSampler] 完成。计算统一使用 `double`，
/// 避免 `float` 在取模与贝塞尔反解时过早损失精度；输出转回 `float` 后的有限性检查仍在采样器里。
///
/// 不访问世界、store、渲染或任何客户端类型，可独立单元测试。
@NullMarked
public final class CurveInterpolation {

    /// 曲柄长度倍数上限：基准长度的 1.5 倍。
    ///
    /// 一段内两侧曲柄的横向长度都不超过段长的 1/2，横坐标才随曲线参数严格单调，
    /// 反解参数才有唯一解；与领域校验使用的上限一致。
    public static final float MAX_HANDLE_LENGTH = 1.5f;

    /// 牛顿迭代次数；收敛后提前退出。
    private static final int NEWTON_ITERATIONS = 8;

    /// 横坐标允许误差。
    private static final double PARAMETER_TOLERANCE = 1.0e-6d;

    /// 小于该导数时不再做牛顿迭代，改走二分兜底。
    private static final double MIN_PARAMETER_SLOPE = 1.0e-9d;

    /// 二分兜底次数：足以把 `[0, 1]` 收敛到浮点精度。
    private static final int BISECTION_ITERATIONS = 48;

    private CurveInterpolation() {
    }

    /// 把任意采样时间映射到 `[firstTime, lastTime]`（闭区间）。
    ///
    /// 时间在首键之前用 `preMode`，在末键之后用 `postMode`：
    ///
    /// - `CLAMP`：取该侧的端点时间；
    /// - `LOOP`：以整段时长 `lastTime - firstTime` 取正模；
    /// - `PING_PONG`：以两倍段长为周期做反射。
    ///
    /// 段长小于等于 0（单键曲线）时按 `CLAMP` 处理，返回 `firstTime`。
    /// `NaN` 不会被静默替换：比较全部不成立时原样返回，让采样器按非有限结果报错。
    ///
    /// @param time      采样时间
    /// @param firstTime 首键时间
    /// @param lastTime  末键时间
    /// @param preMode   首键之前的处理
    /// @param postMode  末键之后的处理
    /// @return 落在有效区间内的时间
    public static double wrapTime(double time,
                                  double firstTime,
                                  double lastTime,
                                  Curve.Wrap preMode,
                                  Curve.Wrap postMode) {
        Objects.requireNonNull(preMode, "preMode");
        Objects.requireNonNull(postMode, "postMode");

        double span = lastTime - firstTime;

        if (!(span > 0d)) {
            return firstTime;
        }

        if (time < firstTime) {
            return wrapSide(time, firstTime, lastTime, span, preMode, true);
        }

        if (time > lastTime) {
            return wrapSide(time, firstTime, lastTime, span, postMode, false);
        }

        return time;
    }

    /// 段内归一化时间：`(time - leftTime) / (rightTime - leftTime)`，夹到 `[0, 1]`。
    ///
    /// 段长小于等于 0 时返回 0（退化为左端点）；`NaN` 时间原样传播，由采样器报错。
    ///
    /// @param time      采样时间
    /// @param leftTime  左键时间
    /// @param rightTime 右键时间
    /// @return 归一化时间
    public static double normalizedTime(double time, double leftTime, double rightTime) {
        double span = rightTime - leftTime;

        if (!(span > 0d)) {
            return 0d;
        }

        return Math.clamp((time - leftTime) / span, 0d, 1d);
    }

    /// 线性插值。
    ///
    /// @param normalizedTime 段内归一化时间
    /// @param leftValue      左键取值
    /// @param rightValue     右键取值
    /// @return 插值结果
    public static double linear(double normalizedTime, double leftValue, double rightValue) {
        return leftValue + (rightValue - leftValue) * normalizedTime;
    }

    /// 三次 Hermite / 贝塞尔插值。
    ///
    /// 两端各有一条曲柄，横向长度是 `曲柄长度倍数 × 段长 / 3`，纵向增量是
    /// `斜率 × 横向长度`；曲柄长度倍数先夹到 `[0, 1.5]`。因为横向控制点分别落在
    /// `[0, 1/2]` 与 `[1/2, 1]`，横坐标随曲线参数单调，可以按归一化时间唯一反解参数。
    ///
    /// 任一输入非有限时结果自然成为非有限值，不在这里兜底成常量；采样器负责报 `ERROR`。
    ///
    /// @param normalizedTime 段内归一化时间
    /// @param span           段长（右键时间 - 左键时间），正数
    /// @param leftValue      左键取值
    /// @param rightValue     右键取值
    /// @param leftOutSlope   左键出侧斜率
    /// @param rightInSlope   右键入侧斜率
    /// @param leftOutLength  左键出侧曲柄长度倍数
    /// @param rightInLength  右键入侧曲柄长度倍数
    /// @return 插值结果
    public static double hermite(double normalizedTime,
                                 double span,
                                 double leftValue,
                                 double rightValue,
                                 double leftOutSlope,
                                 double rightInSlope,
                                 double leftOutLength,
                                 double rightInLength) {
        double leftHandle = clampHandleLength(leftOutLength) / 3d;
        double rightHandle = clampHandleLength(rightInLength) / 3d;

        double leftControl = leftValue + leftOutSlope * leftHandle * span;
        double rightControl = rightValue - rightInSlope * rightHandle * span;

        double parameter = solveParameter(leftHandle, 1d - rightHandle, normalizedTime);
        return bezier(leftValue, leftControl, rightControl, rightValue, parameter);
    }

    /// 曲柄长度倍数夹取：负值按 0，超过 [MAX_HANDLE_LENGTH] 按上限。
    ///
    /// `NaN` 原样返回，不做静默替换。
    ///
    /// @param length 原始曲柄长度倍数
    /// @return 夹取后的长度倍数
    public static double clampHandleLength(double length) {
        return Math.clamp(length, 0d, MAX_HANDLE_LENGTH);
    }

    private static double wrapSide(double time,
                                   double firstTime,
                                   double lastTime,
                                   double span,
                                   Curve.Wrap mode,
                                   boolean beforeFirst) {
        return switch (mode) {
            case CLAMP -> beforeFirst ? firstTime : lastTime;
            case LOOP -> firstTime + positiveModulo(time - firstTime, span);
            case PING_PONG -> {
                double period = span * 2d;
                double local = positiveModulo(time - firstTime, period);

                yield local <= span
                        ? firstTime + local
                        : lastTime - (local - span);
            }
        };
    }

    /// 正模：结果落在 `[0, modulus)`；浮点舍入把负余数加到 `modulus` 时归零，避免越界。
    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;

        if (result < 0d) {
            result += modulus;
        }

        if (result >= modulus) {
            return 0d;
        }

        return result;
    }

    /// 按归一化横坐标反解贝塞尔参数。
    ///
    /// 先用牛顿迭代；导数过小或未收敛时用二分兜底。非有限输入直接返回归一化时间，
    /// 让后续贝塞尔求值传播 `NaN`，不在数学工具里编造有限结果。
    private static double solveParameter(double leftControl, double rightControl, double normalizedTime) {
        if (!Double.isFinite(normalizedTime)
                || !Double.isFinite(leftControl)
                || !Double.isFinite(rightControl)) {
            return normalizedTime;
        }

        double parameter = Math.clamp(normalizedTime, 0d, 1d);

        for (int i = 0; i < NEWTON_ITERATIONS; i++) {
            double error = parameterX(leftControl, rightControl, parameter) - normalizedTime;

            if (Math.abs(error) <= PARAMETER_TOLERANCE) {
                return parameter;
            }

            double slope = parameterSlope(leftControl, rightControl, parameter);

            if (!(slope > MIN_PARAMETER_SLOPE)) {
                break;
            }

            parameter = Math.clamp(parameter - error / slope, 0d, 1d);
        }

        double low = 0d;
        double high = 1d;

        for (int i = 0; i < BISECTION_ITERATIONS; i++) {
            double middle = (low + high) * 0.5d;

            if (parameterX(leftControl, rightControl, middle) < normalizedTime) {
                low = middle;
            } else {
                high = middle;
            }
        }

        return (low + high) * 0.5d;
    }

    /// 三次贝塞尔的归一化横坐标；左端点横坐标为 0、右端点为 1。
    private static double parameterX(double leftControl, double rightControl, double parameter) {
        double inverse = 1d - parameter;

        return 3d * inverse * inverse * parameter * leftControl
                + 3d * inverse * parameter * parameter * rightControl
                + parameter * parameter * parameter;
    }

    /// 归一化横坐标对曲线参数的导数。
    private static double parameterSlope(double leftControl, double rightControl, double parameter) {
        double inverse = 1d - parameter;

        return 3d * (inverse * inverse * leftControl
                + 2d * inverse * parameter * (rightControl - leftControl)
                + parameter * parameter * (1d - rightControl));
    }

    /// 一维三次贝塞尔取值。
    private static double bezier(double start,
                                 double firstControl,
                                 double secondControl,
                                 double end,
                                 double parameter) {
        double inverse = 1d - parameter;

        return inverse * inverse * inverse * start
                + 3d * inverse * inverse * parameter * firstControl
                + 3d * inverse * parameter * parameter * secondControl
                + parameter * parameter * parameter * end;
    }
}
