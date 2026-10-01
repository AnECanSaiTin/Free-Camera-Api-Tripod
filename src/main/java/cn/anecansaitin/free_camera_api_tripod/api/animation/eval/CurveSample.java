package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.KeyField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframec;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curvec;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.KeyValues;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Constant;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.NumberSource;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Scope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/// 按 [Scope] 把键解析成 [KeyValues]：挂了公式的槽位按该时刻算出，算不出来退回固定数值。
///
/// **这是全仓唯一需要知道"来源只存在于可写类上"的地方**：只读视图 [Keyframec] 不发放
/// [NumberSource]，于是这里对一个 [Keyframe] 问 [#resolve] 里的
/// `key instanceof Keyframe`，读只读视图时按它的固定数值造一个 [Constant]。
///
/// 结果按 (键, 槽位) **惰性缓存**：只有求值真正读到某个键时才解析它，所以一次求值只解析
/// 落在区间两端的那两个键；同一条曲线反复采样（画整条曲线、一帧内多个通道求值）时每个键只解析一次。
///
/// 缓存随 [reset] 换曲线、键数、作用域或**帧版本**而清空——公式的值依赖时间，
/// 换了一帧就必须连带清掉，否则会拿上一时刻的值接着用。
/// 作用域是跨帧复用的（见 [Scope#version]），所以除了比对象身份还要比一次版本号。
///
/// `scope` 为 null 就是**静态求值**：公式一律算不出来，[NumberSource#evaluateOrFallback] 给出回退值。
/// 静态取值因此不再是另一个类，而是同一个分支。
@NullMarked
final class CurveSample implements KeyValues {
    /// 每个键占用的缓存槽位数：等于可动态槽位的个数
    private static final int FIELDS = KeyField.values().length;

    private @Nullable Curvec curve;
    private @Nullable Scope scope;
    /// 建缓存时的帧版本；作用域可被跨帧复用，光比对象身份不够，见 [#reset]
    private long version;
    /// 解析结果；未解析的槽位放 NaN（解析出来的值不会是 NaN，见 [NumberSource#evaluateOrFallback]）
    private float[] cache = new float[0];
    private int size;

    /// 一次性构造：公式求值内部的轨道读数（[ExpressionScope#track]）与 [CurveSampler#sampleOnce] 用它
    static CurveSample at(Curvec curve, @Nullable Scope scope) {
        return new CurveSample().reset(curve, scope);
    }

    /// 静态读取器：作用域为 null，只读键上的固定数值。
    ///
    /// 只剩 [CurveSampler#sampleStatic] 在用——那是"本来就没有求值环境"的场景（命令插键、读档），
    /// 不是拿来断开变量与轨道之间回边的（那条回边现在由 `EvaluationGraph` 在写入期保证不存在）
    static CurveSample staticOf(Curvec curve) {
        return at(curve, null);
    }

    /// 切换曲线与作用域并返回自身；曲线、键数、作用域任一变化都清空缓存。
    ///
    /// 作用域进了比较是因为**公式的值依赖时间**：换了一个作用域（也就是换了一个时刻），
    /// 之前解析出来的数就过期了。
    /// 作用域**可复用**之后（见 [Scope#version]），同一个对象会被反复用来表示不同的帧，
    /// 身份相同但帧不同，因此还要比一次版本号。两个判断缺一不可：
    /// 只比身份会漏掉"同一对象换了一帧"，只比版本会漏掉"两个不同对象恰好版本相同"
    CurveSample reset(Curvec curve, @Nullable Scope scope) {
        long currentVersion = scope == null ? 0 : scope.version();
        int count = curve.size();

        if (this.curve != curve || count != size || this.scope != scope || this.version != currentVersion) {
            this.curve = curve;
            this.size = count;

            if (cache.length < count * FIELDS) {
                cache = new float[Math.max(16, count * FIELDS)];
            }

            Arrays.fill(cache, Float.NaN);
        }

        this.scope = scope;
        this.version = currentVersion;
        return this;
    }

    /// 清空缓存：下次采样重新解析。跨帧复用同一个读取器时要调一次
    void clear() {
        curve = null;
        scope = null;
        version = 0;
        size = 0;
        Arrays.fill(cache, Float.NaN);
    }

    @Override
    public float value(int index) {
        return resolve(index, KeyField.VALUE);
    }

    @Override
    public float inSlope(int index) {
        return resolve(index, KeyField.IN_SLOPE);
    }

    @Override
    public float inLength(int index) {
        return resolve(index, KeyField.IN_LENGTH);
    }

    @Override
    public float outSlope(int index) {
        return resolve(index, KeyField.OUT_SLOPE);
    }

    @Override
    public float outLength(int index) {
        return resolve(index, KeyField.OUT_LENGTH);
    }

    private float resolve(int index, KeyField field) {
        // 曲线上的键被删掉时索引会失效；这里给 0 而不是抛异常，采样最多是这一帧不对
        if (index < 0 || index >= size) {
            return 0f;
        }

        int slot = index * FIELDS + field.ordinal();
        float value = cache[slot];

        if (!Float.isNaN(value)) {
            return value;
        }

        Curvec current = curve;

        if (current == null) {
            return 0f;
        }

        return cache[slot] = sourceOf(current.key(index), field).evaluateOrFallback(scope);
    }

    /// 只读视图只有固定数值：它给不出 [NumberSource]，就按 [Keyframec#constant] 造一个
    private static NumberSource sourceOf(Keyframec key, KeyField field) {
        return key instanceof Keyframe writable ? writable.source(field) : new Constant(key.constant(field));
    }
}
