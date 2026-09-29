package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Evaluator;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curvec;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.StaticKeys;
import org.jspecify.annotations.NullMarked;

/// 曲线采样：**求值的唯一入口**。把「按 [Scope] 解析关键帧」与「曲线本身的纯数值插值」接起来。
///
/// 播放、曲线图、插入关键帧、界面预览一律走它，于是"画面上看到的"与"播放出来的"永远是同一条曲线——
/// 以前两者分别走带 / 不带求解器的两套求值，图上画的其实是公式的回退值，与播放结果对不上。
///
/// 采样器内部复用一份解析缓存，同一条曲线连续采样时每个键只解析一次；换曲线、换作用域
/// 都会自动失效（作用域里带着时间，见 [ResolvedKeys]），所以每帧新建作用域时不必手动清。
/// 作用域被长期复用（例如自己实现了一个可变的 [Scope]）时才需要 [clear]。
@NullMarked
public final class CurveSampler {
    private final ResolvedKeys keys = new ResolvedKeys();

    /// 按 [Scope] 采样：挂了公式的键按该时刻的公式值参与插值
    public float sample(Curvec curve, float time, Scope scope) {
        return curve.evaluate(time, keys.reset(curve, scope));
    }

    /// 一次性采样：不复用缓存，适合插入关键帧、读档这类偶发取值。
    /// 连续采样（画整条曲线、一帧内多个通道）请用实例方法 [#sample(Curvec, float, Scope)sample]
    public static float sampleOnce(Curvec curve, float time, Scope scope) {
        return curve.evaluate(time, new ResolvedKeys().reset(curve, scope));
    }

    /// 静态采样：只读键上的固定数值（挂了公式的键取它的回退值），不解析公式。
    ///
    /// 变量读轨道走的就是这一条路径（见 [ExpressionScope#track]），
    /// 所以"变量指向的轨道又引用该变量"不会无限递归
    public static float sampleStatic(Curvec curve, float time) {
        return curve.evaluate(time, new StaticKeys(curve));
    }

    /// 一次取多条通道：各组数值交给 [Evaluator] 组装（例如把三个旋转轴装成一个向量）。
    /// 缺了某条通道时该组取 NaN——通道缺失不该让整帧求值崩掉
    public <T> T sample(CameraAnimationc animation, float time, Evaluator<T> evaluator, Scope scope) {
        String[] properties = evaluator.properties();
        float[] values = new float[properties.length];

        for (int i = 0; i < properties.length; i++) {
            Curve curve = animation.curve(properties[i]);
            values[i] = curve == null ? Float.NaN : sample(curve, time, scope);
        }

        return evaluator.build(values);
    }

    /// 清空解析缓存；作用域被就地复用、时间变了却还是同一个对象时要调一次
    public void clear() {
        keys.clear();
    }
}
