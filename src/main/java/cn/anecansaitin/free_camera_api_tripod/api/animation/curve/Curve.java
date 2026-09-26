package cn.anecansaitin.free_camera_api_tripod.api.animation.curve;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ExpressionContext;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NullMarked
public class Curve implements Curvec {
    /// 升序
    private final ArrayList<Keyframe> keys = new ArrayList<>();
    public WrapMode preMode = WrapMode.CLAMP;
    public WrapMode postMode = WrapMode.CLAMP;
    /// 索引缓存
    private int lastIndex = 0;
    private boolean positive = true;

    public Curve() {
    }

    public Curve(Keyframe... keys) {
        if (keys.length == 0) {
            return;
        }

        for (Keyframe key : keys) {
            this.keys.add(new Keyframe(key));
        }

        this.keys.sort(Keyframe.TIME_COMPARATOR);
    }

    public Curve(List<Keyframe> keys) {
        if (keys.isEmpty()) {
            return;
        }

        for (Keyframe key : keys) {
            this.keys.add(new Keyframe(key));
        }

        this.keys.sort(Keyframe.TIME_COMPARATOR);
    }

    @Override
    public float evaluate(float time) {
        return evaluate(time, null);
    }

    /// 带上下文的求值：挂了公式的字段按公式算（见 {@link Keyframe#value(ExpressionContext)}），
    /// 上下文为 null 或字段没挂公式时就是普通的固定数值求值
    public float evaluate(float time, @Nullable ExpressionContext context) {
        int size = keys.size();

        if (size == 0) {
            return 0;
        }

        if (size == 1) {
            return value(keys.getFirst(), context);
        }

        time = mapTime(time);
        int index = findFloorIndex(time);
        Keyframe left = keys.get(index);

        if (index == size - 1) {
            return value(left, context);
        }

        Keyframe right = keys.get(index + 1);
        float duration = right.time() - left.time();

        // 相邻关键帧时间相同（或数据异常）时，归一化时间与切线缩放都会变成 0/0，
        // 插值结果随即变成 NaN 并污染整条通道，这里直接退化成取左值
        if (!(duration > 0)) {
            return value(left, context);
        }

        if (Float.isInfinite(outTangent(left, context)) || Float.isInfinite(inTangent(right, context))) {
            // 切线为无限，视为Step插值，取左值
            return value(left, context);
        }

        // 归一化时间
        time = Math.clamp((time - left.time()) / duration, 0, 1);

        return switch (left.evaluateMode()) {
            case LINEAR -> evaluateLinear(left, right, time, context);
            case STEP -> value(left, context);
            case HERMITE -> evaluateHermite(left, right, time, duration, context);
        };
    }

    private float evaluateLinear(Keyframe left, Keyframe right, float time, @Nullable ExpressionContext context) {
        float leftValue = value(left, context);
        return (value(right, context) - leftValue) * time + leftValue;
    }

    private float evaluateHermite(Keyframe left, Keyframe right, float time, float duration, @Nullable ExpressionContext context) {
        // 切线计算
        float leftTangent = outTangent(left, context);
        float rightTangent = inTangent(right, context);

        leftTangent = switch (left.weightedMode()) {
            case NONE, IN -> leftTangent;
            case OUT, BOTH -> leftTangent * computeWeightScale(outWeight(left, context));
        };

        rightTangent = switch (right.weightedMode()) {
            case NONE, OUT -> rightTangent;
            case IN, BOTH -> rightTangent * computeWeightScale(inWeight(right, context));
        };

        return hermite(value(left, context), leftTangent, value(right, context), rightTangent, time, duration);
    }

    /// 取值 / 切线 / 权重的统一出口：没有上下文时读固定数值，省得求值链上到处写判断
    private static float value(Keyframe key, @Nullable ExpressionContext context) {
        return context == null ? key.value() : key.value(context);
    }

    private static float inTangent(Keyframe key, @Nullable ExpressionContext context) {
        return context == null ? key.inTangent() : key.inTangent(context);
    }

    private static float outTangent(Keyframe key, @Nullable ExpressionContext context) {
        return context == null ? key.outTangent() : key.outTangent(context);
    }

    private static float inWeight(Keyframe key, @Nullable ExpressionContext context) {
        return context == null ? key.inWeight() : key.inWeight(context);
    }

    private static float outWeight(Keyframe key, @Nullable ExpressionContext context) {
        return context == null ? key.outWeight() : key.outWeight(context);
    }

    public int key(float time, float value) {
        return key(new Keyframe(time, value));
    }

    /// 添加关键帧，并返回索引
    /// 根据关键帧时间，自动选择插入位置
    /// 如果对应时间已有关键帧，则修改该关键帧并返回索引
    /// 如果时间小于0，则不添加并返回-1
    public int key(Keyframe key) {
        if (key.time() < 0) {
            return -1;
        }

        int index = Collections.binarySearch(keys, key, Keyframe.TIME_COMPARATOR);

        if (index >= 0) {
            // 已存在，修改关键帧
            Keyframe keyframe = keys.get(index);
            keyframe.set(key);
            return index;
        }

        int insertIndex = -(index + 1);
        keys.add(insertIndex, new Keyframe(key));
        return insertIndex;
    }

    @Override
    public Keyframe key(int index) {
        if (validKey(index)) {
            throw new IndexOutOfBoundsException("Invalid keyframe index: " + index);
        }

        return keys.get(index);
    }

    /// 移动关键帧
    /// 如果index不在范围内，则不移动并返回-1
    /// 如果newTime小于0，则不移动并返回-1
    /// 如果目标时间已有关键帧（且都不是被移动的这一个），则不移动并返回-1
    public int moveKey(int index, float newTime) {
        if (validKey(index) || newTime < 0) {
            return -1;
        }

        Keyframe keyframe = keys.get(index);

        if (keyframe.time() == newTime) {
            return -1;
        }

        keys.remove(index);
        return key(keyframe.time(newTime));
    }

    /// 删除关键帧
    /// 如果index不在范围内，则不删除
    public boolean removeKey(int index) {
        if (validKey(index)) {
            return false;
        }

        keys.remove(index);
        return true;
    }

    public void smoothTangents(float weight) {
        for (int i = 0; i < size(); i++) {
            smoothTangents(i, weight);
        }
    }

    public void smoothTangents(int index, float weight) {
        if (keys.isEmpty() || index < 0 || index >= keys.size()) {
            return;
        }

        int count = keys.size();
        Keyframe current = keys.get(index);
        weight = Math.clamp(weight, 0, 1);

        float inTangent, outTangent;

        if (count == 1) {
            // 单关键帧：切线为 0
            inTangent = outTangent = 0f;
        } else if (index == 0) {
            // 起点只有 outTangent
            Keyframe next = keys.get(1);
            float dt = next.time() - current.time();
            float dv = next.value() - current.value();
            outTangent = (dt != 0) ? (dv / dt) : current.inTangent();
            inTangent = outTangent;
        } else if (index == count - 1) {
            // 终点只有 inTangent
            Keyframe prev = keys.get(count - 2);
            float dt = current.time() - prev.time();
            float dv = current.value() - prev.value();
            inTangent = (dt != 0) ? (dv / dt) : current.outTangent();
            outTangent = inTangent;
        } else {
            // 中间点：使用 Catmull-Rom
            Keyframe prev = keys.get(index - 1);
            Keyframe next = keys.get(index + 1);

            float dtPrev = current.time() - prev.time();
            float dtNext = next.time() - current.time();
            float dvPrev = current.value() - prev.value();
            float dvNext = next.value() - current.value();

            // 斜率
            float slopePrev = dvPrev / dtPrev;
            float slopeNext = dvNext / dtNext;

            float tangent = (slopePrev + slopeNext) * 0.5f;
            inTangent = Mth.lerp(weight, slopePrev, tangent);
            outTangent = Mth.lerp(weight, slopeNext, tangent);
        }

        current.inTangent(inTangent);
        current.outTangent(outTangent);
    }

    @Override
    public int size() {
        return keys.size();
    }

    /// 根据时间wrap模式，映射到有效时间范围内
    private float mapTime(float time) {
        if (size() == 0) {
            return 0;
        }

        float timeStart = keys.getFirst().time();
        float timeEnd = keys.getLast().time();
        float duration = timeEnd - timeStart;

        if (duration <= 0) {
            return timeStart;
        }

        if (time < timeStart) {
            return mapPreTime(time, timeStart, timeEnd, duration);
        } else if (time > timeEnd) {
            return mapPostTime(time, timeStart, timeEnd, duration);
        } else {
            return Math.clamp(time, timeStart, timeEnd);
        }
    }

    private float mapPreTime(float time, float timeStart, float timeEnd, float duration) {
        return switch (preMode) {
            case CLAMP -> timeStart;
            case LOOP -> {
                float localPre = (time - timeStart) % duration;

                if (localPre < 0) {
                    localPre += duration;
                }

                yield timeStart + localPre;
            }
            case PING_PONG -> {
                float period = duration * 2;
                float localPre = (time - timeStart) % period;

                if (localPre < 0) {
                    localPre += period;
                }

                yield localPre <= duration ? timeStart + localPre : timeEnd - localPre + duration;
            }
        };
    }

    private float mapPostTime(float time, float timeStart, float timeEnd, float duration) {
        return switch (postMode) {
            case CLAMP -> timeEnd;
            case LOOP -> {
                float localPost = (time - timeStart) % duration;

                if (localPost < 0) {
                    localPost += duration;
                }

                // 当时间及其接近timeEnd时，可能因为浮点数误差导致超出有效范围
                if (localPost >= duration) {
                    localPost = 0;
                }

                yield timeStart + localPost;
            }
            case PING_PONG -> {
                float period = duration * 2;
                float localPost = (time - timeStart) % period;

                if (localPost < 0) {
                    localPost += period;
                }

                yield localPost <= duration ? timeStart + localPost : timeEnd - localPost + duration;
            }
        };
    }

    @Nullable
    public Keyframe preKey(float time) {
        int index = findFloorIndex(time);

        if (validKey(index)) {
            return key(index);
        }

        return null;
    }

    private int findFloorIndex(float time) {
        int size = keys.size();
        int maxFloor = size - 1;

        if (lastIndex >= 0 && lastIndex < maxFloor) {
            Keyframe left = keys.get(lastIndex);
            Keyframe right = keys.get(lastIndex + 1);

            if (time >= left.time() && time < right.time()) {
                return lastIndex;
            }

            if (positive) {
                if (time >= right.time()) {
                    if (lastIndex + 1 >= maxFloor) {
                        return lastIndex = maxFloor;
                    }

                    left = right;
                    right = keys.get(lastIndex + 2);

                    if (time >= left.time() && time < right.time()) {
                        return ++lastIndex;
                    }
                }
            } else if (time < left.time()) {
                if (lastIndex <= 0) {
                    return lastIndex = 0;
                }

                right = left;
                left = keys.get(lastIndex - 1);

                if (time >= left.time() && time < right.time()) {
                    return --lastIndex;
                }
            }
        }

        int i = binarySearch(time);
        i = i < 0 ? -i - 2 : i;
        positive = i >= lastIndex;
        return lastIndex = i;
    }

    private final Keyframe searchingCache = new Keyframe(0, 0);

    private int binarySearch(float time) {
        return Collections.binarySearch(keys, searchingCache.time(time), Keyframe.TIME_COMPARATOR);
    }

    private float computeWeightScale(float weight) {
        // 来自Unity的经验算法，减少计算量
        return weight / (weight + 3.0f);
    }

    private boolean validKey(int index) {
        return index < 0 || index >= size();
    }

    private float hermite(float p0, float m0, float p1, float m1, float t, float dt) {
        float tangent0 = m0 * dt;
        float tangent1 = m1 * dt;

        float t2 = t * t;
        float t3 = t2 * t;

        float h00 = 2 * t3 - 3 * t2 + 1;
        float h10 = -2 * t3 + 3 * t2;
        float h01 = t3 - 2 * t2 + t;
        float h11 = t3 - t2;

        return h00 * p0 + h10 * p1 + h01 * tangent0 + h11 * tangent1;
    }

    public static Curve constant(float timeStart, float timeEnd, float value) {
        if (timeStart == timeEnd) {
            return new Curve(new Keyframe(timeStart, value, 0, 0));
        }

        return new Curve(new Keyframe(timeStart, value), new Keyframe(timeEnd, value));
    }

    public static Curve easeInOut(float timeStart, float valueStart, float timeEnd, float valueEnd) {
        if (timeStart == timeEnd) {
            return new Curve(new Keyframe(timeStart, valueStart, 0, 0));
        }

        return new Curve(new Keyframe(timeStart, valueStart), new Keyframe(timeEnd, valueEnd));
    }

    public static Curve linear(float timeStart, float valueStart, float timeEnd, float valueEnd) {
        if (timeStart == timeEnd) {
            return new Curve(new Keyframe(timeStart, valueStart, 0, 0));
        }

        float slope = (valueEnd - valueStart) / (timeEnd - timeStart);
        return new Curve(new Keyframe(timeStart, valueStart, 0, slope), new Keyframe(timeEnd, valueEnd, slope, 0));
    }
}
