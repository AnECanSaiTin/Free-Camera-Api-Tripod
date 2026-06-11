package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.api.Keyframe;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NullMarked
public class Curve {
    /// 升序
    private final ArrayList<MutableKeyframe> keys = new ArrayList<>();
    public WrapMode preMode = WrapMode.CLAMP;
    public WrapMode postMode = WrapMode.CLAMP;
    /// 索引缓存
    private int lastIndex = 0;

    public Curve() {
    }

    public Curve(Keyframe... keys) {
        if (keys.length == 0) {
            return;
        }

        for (Keyframe key : keys) {
            this.keys.add(new MutableKeyframe(key));
        }

        this.keys.sort(Keyframe.TIME_COMPARATOR);
    }

    public Curve(List<Keyframe> keys) {
        if (keys.isEmpty()) {
            return;
        }

        for (Keyframe key : keys) {
            this.keys.add(new MutableKeyframe(key));
        }

        this.keys.sort(Keyframe.TIME_COMPARATOR);
    }

    public float evaluate(float time) {
        int size = keys.size();

        if (size == 0) {
            return 0;
        }

        if (size == 1) {
            return keys.getFirst().value();
        }

        time = mapTime(time);
        int index = findFloorIndex(time);
        MutableKeyframe left = keys.get(index);
        MutableKeyframe right = keys.get(index + 1);
        float duration = right.time() - left.time();

        if (Float.isInfinite(left.outTangent()) || Float.isInfinite(right.inTangent())) {
            // 切线为无限，视为Step插值，取左值
            return left.value();
        }

        // 归一化时间
        time = Math.clamp((time - left.time()) / duration, 0, 1);

        return switch (left.evaluateMode()) {
            case LINEAR -> evaluateLinear(left, right, time);
            case STEP -> left.value();
            case HERMITE -> evaluateHermite(left, right, time, duration);
        };
    }

    private float evaluateLinear(MutableKeyframe left, MutableKeyframe right, float time) {
        return (left.value() + right.value()) * time;
    }

    private float evaluateHermite(MutableKeyframe left, MutableKeyframe right, float time, float duration) {
        // 切线计算
        float leftTangent = left.outTangent();
        float rightTangent = right.inTangent();

        leftTangent = switch (left.weightedMode()) {
            case NONE, IN -> leftTangent;
            case OUT, BOTH -> leftTangent * computeWeightScale(left.outWeight());
        };

        rightTangent = switch (right.weightedMode()) {
            case NONE, OUT -> rightTangent;
            case IN, BOTH -> rightTangent * computeWeightScale(right.inWeight());
        };

        return hermite(left.value(), leftTangent, right.value(), rightTangent, time, duration);
    }

    public int key(float time, float value) {
        return key(new MutableKeyframe(time, value));
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
            MutableKeyframe keyframe = keys.get(index);
            keyframe.set(key);
            return index;
        }

        int insertIndex = -(index + 1);
        keys.add(insertIndex, new MutableKeyframe(key));
        return insertIndex;
    }

    public Keyframe key(int index) {
        return keys.get(index);
    }

    /// 移动关键帧
    /// 如果index不在范围内，则不移动并返回-1
    /// 如果newTime小于0，则不移动并返回-1
    /// 如果目标时间已有关键帧（且都不是被移动的这一个），则不移动并返回-1
    public int moveKey(int index, float newTime) {
        if (index < 0 || index >= size() || newTime < 0) {
            return -1;
        }

        MutableKeyframe keyframe = keys.get(index);

        if (keyframe.time() == newTime) {
            return -1;
        }

        keys.remove(index);
        return key(keyframe.time(newTime));
    }

    public int moveKey(float oldTime, float newTime) {
        if (oldTime == newTime) {
            return -1;
        }

        int index = binarySearch(oldTime);
        return moveKey(index, newTime);
    }

    /// 删除关键帧
    /// 如果index不在范围内，则不删除
    public boolean removeKey(int index) {
        if (index < 0 || index >= size()) {
            return false;
        }

        keys.remove(index);
        return true;
    }

    public boolean removeKye(float time) {
        int index = binarySearch(time);
        return removeKey(index);
    }


    public void smoothTangents(int index, float weight) {
        if (keys.isEmpty() || index < 0 || index >= keys.size()) {
            return;
        }

        int count = keys.size();
        MutableKeyframe current = keys.get(index);
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

    public int size() {
        return keys.size();
    }

    public static Curve constant(float timeStart, float timeEnd, float value) {
        if (timeStart == timeEnd) {
            return new Curve(new MutableKeyframe(timeStart, value, 0, 0));
        }

        return new Curve(new MutableKeyframe(timeStart, value), new MutableKeyframe(timeEnd, value));
    }

    public static Curve easeInOut(float timeStart, float valueStart, float timeEnd, float valueEnd) {
        if (timeStart == timeEnd) {
            return new Curve(new MutableKeyframe(timeStart, valueStart, 0, 0));
        }

        return new Curve(new MutableKeyframe(timeStart, valueStart), new MutableKeyframe(timeEnd, valueEnd));
    }

    public static Curve linear(float timeStart, float valueStart, float timeEnd, float valueEnd) {
        if (timeStart == timeEnd) {
            return new Curve(new MutableKeyframe(timeStart, valueStart, 0, 0));
        }

        float slope = (valueEnd - valueStart) / (timeEnd - timeStart);
        return new Curve(new MutableKeyframe(timeStart, valueStart, 0, slope), new MutableKeyframe(timeEnd, valueEnd, slope, 0));
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

    private int findFloorIndex(float time) {
        // region 缓存快速检查
        if (lastIndex < 0 || lastIndex >= keys.size() - 1) {
            lastIndex = 0;
        }

        Keyframe left = keys.get(lastIndex);
        Keyframe right = keys.get(lastIndex + 1);

        if (time >= left.time() && time <= right.time()) {
            return lastIndex;
        }
        // endregion
        lastIndex = Math.max(binarySearch(time), 0);
        return lastIndex;
    }

    private int binarySearch(float time) {
        int left = 0;
        int right = keys.size() - 1;

        while (left <= right) {
            int mid = (left + right) >>> 1;
            float midTime = keys.get(mid).time();

            if (midTime < time) {
                left = mid + 1;
            } else if (midTime > time) {
                right = mid - 1;
            } else {
                return mid;
            }
        }

        return -(left + 1);
    }

    private float computeWeightScale(float weight) {
        // 来自Unity的经验算法，减少计算量
        return weight / (weight + 3.0f);
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
}