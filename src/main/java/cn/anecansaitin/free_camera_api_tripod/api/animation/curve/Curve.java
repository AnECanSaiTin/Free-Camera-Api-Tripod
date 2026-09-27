package cn.anecansaitin.free_camera_api_tripod.api.animation.curve;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Solver;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;
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

    /// 带求解器的求值：挂了公式的数值按公式算（见 [ValueSource#evaluateOrFallback]），
    /// 求解器为 null 或公式算不出来时就是普通的固定数值求值
    public float evaluate(float time, @Nullable Solver solver) {
        int size = keys.size();

        if (size == 0) {
            return 0;
        }

        if (size == 1) {
            return value(keys.getFirst(), solver);
        }

        time = mapTime(time);
        int index = findFloorIndex(time);
        Keyframe left = keys.get(index);

        if (index == size - 1) {
            return value(left, solver);
        }

        Keyframe right = keys.get(index + 1);
        float duration = right.time() - left.time();

        // 相邻关键帧时间相同（或数据异常）时，归一化时间与切线缩放都会变成 0/0，
        // 插值结果随即变成 NaN 并污染整条通道，这里直接退化成取左值
        if (!(duration > 0)) {
            return value(left, solver);
        }

        if (Float.isInfinite(outSlope(left, solver)) || Float.isInfinite(inSlope(right, solver))) {
            // 切线为无限，视为Step插值，取左值
            return value(left, solver);
        }

        // 归一化时间
        time = Math.clamp((time - left.time()) / duration, 0, 1);

        return switch (left.evaluateMode()) {
            case LINEAR -> evaluateLinear(left, right, time, solver);
            case STEP -> value(left, solver);
            case HERMITE -> evaluateBezier(left, right, time, duration, solver);
        };
    }

    private float evaluateLinear(Keyframe left, Keyframe right, float time, @Nullable Solver solver) {
        float leftValue = value(left, solver);
        return (value(right, solver) - leftValue) * time + leftValue;
    }

    /// 三次贝塞尔求值。
    ///
    /// 两端各有一条曲柄，落在 `端点 + 曲柄长度 × (1, 斜率)` 处：横向是曲柄长度（基准是这段时长的 1/3，
    /// 按关键帧上的长度倍数缩放），纵向是斜率乘这个长度。横向也是自由度，
    /// 所以曲线图上把曲柄拖长拖短同样会改变曲线，与常见软件的贝塞尔曲柄一致。
    ///
    /// 求值要先按时间反解曲线参数（见 [solveParameter]）。两侧曲柄长度之和不超过整段时长时
    /// 横坐标随参数单调，解唯一
    private float evaluateBezier(Keyframe left, Keyframe right, float time, float duration, @Nullable Solver solver) {
        float p0 = value(left, solver);
        float p1 = value(right, solver);
        float span = duration / 3f;
        float h0 = span * handleLength(outLength(left, solver));
        float h1 = span * handleLength(inLength(right, solver));
        float m0 = outSlope(left, solver);
        float m1 = inSlope(right, solver);
        float u = solveParameter(h0 / duration, 1 - h1 / duration, time);
        return bezier(p0, p0 + m0 * h0, p1 - m1 * h1, p1, u);
    }

    /// 取值 / 斜率 / 曲柄长度的统一出口：算不出来就退回该字段自己的固定数值
    private static float value(Keyframe key, @Nullable Solver solver) {
        return ValueSource.evaluateOrFallback(key.valueSource(), solver);
    }

    private static float inSlope(Keyframe key, @Nullable Solver solver) {
        return ValueSource.evaluateOrFallback(key.inSlopeSource(), solver);
    }

    private static float outSlope(Keyframe key, @Nullable Solver solver) {
        return ValueSource.evaluateOrFallback(key.outSlopeSource(), solver);
    }

    private static float inLength(Keyframe key, @Nullable Solver solver) {
        return ValueSource.evaluateOrFallback(key.inLengthSource(), solver);
    }

    private static float outLength(Keyframe key, @Nullable Solver solver) {
        return ValueSource.evaluateOrFallback(key.outLengthSource(), solver);
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
        if (invalidKey(index)) {
            throw new IndexOutOfBoundsException("Invalid keyframe index: " + index);
        }

        return keys.get(index);
    }

    /// 移动关键帧
    /// 如果index不在范围内，则不移动并返回-1
    /// 如果newTime小于0，则不移动并返回-1
    /// 如果目标时间已有关键帧（且都不是被移动的这一个），则不移动并返回-1
    public int moveKey(int index, float newTime) {
        if (invalidKey(index) || newTime < 0) {
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
        if (invalidKey(index)) {
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

        float inSlope, outSlope;

        if (count == 1) {
            // 单关键帧：切线为 0
            inSlope = outSlope = 0f;
        } else if (index == 0) {
            // 起点只有出侧斜率
            Keyframe next = keys.get(1);
            float dt = next.time() - current.time();
            float dv = next.value() - current.value();
            outSlope = (dt != 0) ? (dv / dt) : current.inSlope();
            inSlope = outSlope;
        } else if (index == count - 1) {
            // 终点只有入侧斜率
            Keyframe prev = keys.get(count - 2);
            float dt = current.time() - prev.time();
            float dv = current.value() - prev.value();
            inSlope = (dt != 0) ? (dv / dt) : current.outSlope();
            outSlope = inSlope;
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
            inSlope = Mth.lerp(weight, slopePrev, tangent);
            outSlope = Mth.lerp(weight, slopeNext, tangent);
        }

        current.inSlope(inSlope);
        current.outSlope(outSlope);
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

    /// 指定时间之前最近的关键帧；该时间之前没有键（含整条曲线还是空的）时返回 null
    @Nullable
    public Keyframe preKey(float time) {
        int index = findFloorIndex(time);
        return invalidKey(index) ? null : key(index);
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

    /// 曲柄长度倍数的上限：基准长度的 1.5 倍。
    /// 一段里两侧曲柄加起来不超过整段时长，横坐标才随曲线参数单调、反解才有唯一解
    public static final float MAX_HANDLE_LENGTH = 1.5f;

    /// 曲柄长度倍数：负值按 0、超上限按上限。
    /// 数据可能来自手写文件或公式，越界会让反解失去唯一解，所以取用前统一夹一次
    public static float handleLength(float length) {
        return Math.clamp(length, 0f, MAX_HANDLE_LENGTH);
    }

    /// 下标是否越界（或为负）。方法名就是判断结果，调用处不必再取反
    private boolean invalidKey(int index) {
        return index < 0 || index >= size();
    }

    /// 反解曲线参数：求 u 使横坐标等于归一化后的时间。
    ///
    /// 两侧曲柄长度之和不超过整段时长（见 [MAX_HANDLE_SCALE]）时横坐标单调递增，
    /// 牛顿迭代从线性解 u = x 出发很快收敛；命中不了就退回当前值，不会发散
    private static float solveParameter(float a, float b, float x) {
        float u = x;

        for (int i = 0; i < 8; i++) {
            float error = parameterX(a, b, u) - x;

            if (Math.abs(error) < 1.0E-4f) {
                break;
            }

            float slope = parameterSlope(a, b, u);

            if (slope < 1.0E-5f) {
                break;
            }

            u = Math.clamp(u - error / slope, 0f, 1f);
        }

        return u;
    }

    /// 三次贝塞尔的横坐标（归一化）。a 是左曲柄的横坐标，b 是右曲柄的
    private static float parameterX(float a, float b, float u) {
        float v = 1 - u;
        return 3 * v * v * u * a + 3 * v * u * u * b + u * u * u;
    }

    /// 横坐标对曲线参数的导数
    private static float parameterSlope(float a, float b, float u) {
        float v = 1 - u;
        return 3 * (v * v * a + 2 * v * u * (b - a) + u * u * (1 - b));
    }

    /// 三次贝塞尔的值
    private static float bezier(float p0, float c0, float c1, float p1, float u) {
        float v = 1 - u;
        return v * v * v * p0 + 3 * v * v * u * c0 + 3 * v * u * u * c1 + u * u * u * p1;
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
