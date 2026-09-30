package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframec;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curvec;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.KeyValues;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/// 按 [Scope] 解析曲线上关键帧五个数值的读取器：挂了公式的字段按该时刻算出，算不出来退回固定数值。
///
/// 结果按 (键, 字段) **惰性缓存**：只有求值真正读到某个键时才解析它，所以一次求值只解析
/// 落在区间两端的那两个键；同一条曲线反复采样（画整条曲线、一帧内多个通道求值）时每个键只解析一次。
///
/// 缓存随 [reset] 换曲线、键数、作用域或**帧版本**而清空——公式的值依赖时间，
/// 换了一帧就必须连带清掉，否则会拿上一时刻的值接着用。
/// 作用域是跨帧复用的（见 [Scope#version]），所以除了比对象身份还要比一次版本号。
@NullMarked
public final class ResolvedKeys implements KeyValues {
    private static final int FIELDS = 5;
    private static final int VALUE = 0;
    private static final int IN_SLOPE = 1;
    private static final int IN_LENGTH = 2;
    private static final int OUT_SLOPE = 3;
    private static final int OUT_LENGTH = 4;

    private @Nullable Curvec curve;
    private @Nullable Scope scope;
    /// 建缓存时的帧版本；作用域可被跨帧复用，光比对象身份不够，见 [#reset]
    private long version;
    /// 解析结果；未解析的槽位放 NaN（解析出来的值不会是 NaN，见 [ValueSource#evaluateOrFallback]）
    private float[] cache = new float[0];
    private int size;

    /// 切换曲线与作用域并返回自身；曲线、键数、作用域任一变化都清空缓存。
    ///
    /// 作用域进了比较是因为**公式的值依赖时间**：换了一个作用域（也就是换了一个时刻），
    /// 之前解析出来的数就过期了。播放器与编辑器每帧新建作用域，缓存因此自然按帧失效，
    /// 不必由调用方记着清。
    ///
    /// 作用域改成**可复用**之后（见 [Scope#version]），同一个对象会被反复用来表示不同的帧，
    /// 身份相同但帧不同，因此还要比一次版本号。两个判断缺一不可：
    /// 只比身份会漏掉"同一对象换了一帧"，只比版本会漏掉"两个不同对象恰好版本相同"
    public KeyValues reset(Curvec curve, Scope scope) {
        int count = curve.size();

        if (this.curve != curve || count != size || this.scope != scope || this.version != scope.version()) {
            this.curve = curve;
            this.size = count;

            if (cache.length < count * FIELDS) {
                cache = new float[Math.max(16, count * FIELDS)];
            }

            Arrays.fill(cache, Float.NaN);
        }

        this.scope = scope;
        this.version = scope.version();
        return this;
    }

    /// 清空缓存：下次采样重新解析。跨帧复用同一个读取器时要调一次
    public void clear() {
        curve = null;
        scope = null;
        version = 0;
        size = 0;
        Arrays.fill(cache, Float.NaN);
    }

    @Override
    public float value(int index) {
        return resolve(index, VALUE);
    }

    @Override
    public float inSlope(int index) {
        return resolve(index, IN_SLOPE);
    }

    @Override
    public float inLength(int index) {
        return resolve(index, IN_LENGTH);
    }

    @Override
    public float outSlope(int index) {
        return resolve(index, OUT_SLOPE);
    }

    @Override
    public float outLength(int index) {
        return resolve(index, OUT_LENGTH);
    }

    private float resolve(int index, int field) {
        // 曲线上的键被删掉时索引会失效；这里给 0 而不是抛异常，采样最多是这一帧不对
        if (index < 0 || index >= size) {
            return 0f;
        }

        int slot = index * FIELDS + field;
        float value = cache[slot];

        if (!Float.isNaN(value)) {
            return value;
        }

        Keyframec key = curve.key(index);
        ValueSource source = switch (field) {
            case VALUE -> key.valueSource();
            case IN_SLOPE -> key.inSlopeSource();
            case IN_LENGTH -> key.inLengthSource();
            case OUT_SLOPE -> key.outSlopeSource();
            default -> key.outLengthSource();
        };
        value = ValueSource.evaluateOrFallback(source, scope);
        cache[slot] = value;
        return value;
    }
}
