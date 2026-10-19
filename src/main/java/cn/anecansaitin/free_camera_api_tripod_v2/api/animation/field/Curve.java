package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// 一条曲线：严格递增的关键帧序列 + 首尾之外的 wrap 模式。
///
/// ## 不变量
///
/// 键时间**严格递增且唯一**（主设计 §4.4 / D17）：同一轨道内不允许两个键落在同一时刻，
/// 也不自动量化、不静默覆盖冲突键。构造时按键时间升序整理并校验唯一性；
/// 键本身是不可变 `Keyframe`，因此只复制容器，不复制元素。
///
/// 时间冲突的处理属于**编辑层**：移动或粘贴撞到已有时刻时，整个候选被拒绝，
/// 并提供覆盖、合并或跳过的显式操作（主设计 §4.4）。构造器抛异常只用于兜底，
/// 不应作为常规冲突处理路径。
///
/// ## 求值
///
/// **本类型不含插值算法**。曲线只描述数据；求值由 `api.animation.eval` 的采样器按
/// 显式求值上下文完成（不变量 I6：曲线、数值列与姿态共用同一求值规则）。
/// 这样 `Curve` 保持为纯数据，序列化与相等性都不受算法版本影响。
///
/// 键为空时：位置通道视为未编写（`ABSENT`），不是“值 0”。相机因此保持宿主基础姿态分量，
/// 不会被搬到世界原点。这条口径由求值层给出，不在本类型上表达。
///
/// @param keys     严格按时间递增、时间互不相同的键；已防御性复制
/// @param preMode  首键之前的时间处理
/// @param postMode 末键之后的时间处理
@NullMarked
public record Curve(List<Keyframe> keys, Wrap preMode, Wrap postMode) {

    /// 插值模式（主设计 §3.2 的“阶梯 / 线性 / 贝塞尔”）。
    ///
    /// `HERMITE` 表示本项目自己的三次曲线（两端各一条曲柄），名称沿用主体实现，
    /// 不泛指所有 Hermite 插值，也不声称与其它软件的同名模式逐位等价。
    public enum Interpolation {
        /// 阶梯：段内取左值。
        STEP,
        /// 线性：段内按归一化时间线性插值。
        LINEAR,
        /// 三次曲线：使用两侧斜率与曲柄长度。
        HERMITE
    }

    /// 首键之前 / 末键之后的时间处理（曲线外插）。
    public enum Wrap {
        /// 夹取到首键 / 末键。
        CLAMP,
        /// 按整段时长循环回绕。
        LOOP,
        /// 按两倍时长来回往复。
        PING_PONG
    }

    /// 空曲线：没有任何键，wrap 均为夹取。
    public static final Curve EMPTY = new Curve(List.of(), Wrap.CLAMP, Wrap.CLAMP);

    public Curve {
        List<Keyframe> sorted = new ArrayList<>(keys);

        if (sorted.size() > 1) {
            sorted.sort(Comparator.comparingDouble(Keyframe::time));

            for (int i = 1; i < sorted.size(); i++) {
                float previous = sorted.get(i - 1).time();
                float current = sorted.get(i).time();

                if (current == previous) {
                    throw new IllegalArgumentException(
                            "Keyframe times must be unique within a curve; duplicate at " + current + "s");
                }
            }
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        keys = List.copyOf(sorted);
    }

    /// 单键曲线。
    public static Curve of(Keyframe key) {
        return new Curve(List.of(key), Wrap.CLAMP, Wrap.CLAMP);
    }

    /// 结构编码：`keys / pre_mode / post_mode`。
    ///
    /// 键按时间升序写出（构造器已整理）；重复时间的键在解码时会被构造器拒绝，
    /// 由解码边界转成结构错误（主设计 §4.4：同轨时间唯一）。
    public static final MapCodec<Curve> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Keyframe.CODEC.listOf().fieldOf("keys").forGetter(Curve::keys),
            Codecs.enumByName(Wrap.class).fieldOf("pre_mode").forGetter(Curve::preMode),
            Codecs.enumByName(Wrap.class).fieldOf("post_mode").forGetter(Curve::postMode)
    ).apply(instance, Curve::new));

    /// 独立解析一条曲线时使用的 Codec。
    public static final Codec<Curve> CODEC = MAP_CODEC.codec();

    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    /// 第 index 个键；越界返回空，不抛异常——越界下标在编辑层由稳定 ID 定位替代。
    public @Nullable Keyframe keyAt(int index) {
        return index >= 0 && index < keys.size() ? keys.get(index) : null;
    }

    /// 首键；空曲线返回空。
    public @Nullable Keyframe first() {
        return keys.isEmpty() ? null : keys.getFirst();
    }

    /// 末键；空曲线返回空。
    public @Nullable Keyframe last() {
        return keys.isEmpty() ? null : keys.getLast();
    }

    /// 首键到末键的时间跨度，单位秒；键少于两个时为 0。
    ///
    /// 这是**段落跨度**，只服务曲线自身的循环 / 往复段落长度，**不是**轨道或动画时长：
    /// 轨道结束时间取末键时刻（`CurveTrack.duration()`），动画时长还取文档级轨道、
    /// 相机来源、状态轨道、标记与叠加范围的有限最大结束时间（主设计 §3.1）。
    public float span() {
        return keys.size() < 2 ? 0f : keys.getLast().time() - keys.getFirst().time();
    }

    /// 按稳定 ID 找键；不存在返回空。
    ///
    /// 这**不是**求值路径（求值按时间二分查找），只是编辑层按 ID 定位用。
    public @Nullable Keyframe byId(KeyId keyId) {
        for (Keyframe key : keys) {
            if (key.keyId().equals(keyId)) {
                return key;
            }
        }

        return null;
    }

    /// 换一份键集合，wrap 模式不变。构造器会重新校验递增与唯一性。
    public Curve withKeys(List<Keyframe> newKeys) {
        return new Curve(newKeys, preMode, postMode);
    }

    /// 换 wrap 模式，键不变。
    public Curve withWrap(Wrap newPreMode, Wrap newPostMode) {
        return new Curve(keys, newPreMode, newPostMode);
    }
}
