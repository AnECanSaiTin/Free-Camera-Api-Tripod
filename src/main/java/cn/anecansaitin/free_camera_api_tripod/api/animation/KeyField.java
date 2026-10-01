package cn.anecansaitin.free_camera_api_tripod.api.animation;

import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/// 关键帧上可以挂公式的五个数值槽位。
///
/// **枚举顺序 = 序列化顺序 = 面板行顺序**；界面标签、小数位、所属插值模式都挂在这里，
/// 于是"再加一个可动态字段"真的只需要在这里加一行——这是原来那套枚举想做到、
/// 却因为字段分散在五组方法里而没做到的事。
///
/// [Keyframe] 的字段因此是「槽位 → 数值来源」这一对（见 [Keyframe#source(KeyField)]），
/// 五个数值不再各有五个方法。
@NullMarked
public enum KeyField {
    /// 唯一的取值槽位，三种插值模式下都参与求值
    VALUE("inspector.key.value", 3, EnumSet.of(EvaluateMode.LINEAR, EvaluateMode.STEP, EvaluateMode.HERMITE)),
    /// 入曲柄的斜率；只有左键为贝塞尔的那一段才读它
    IN_SLOPE("inspector.key.in_slope", 3, EnumSet.of(EvaluateMode.HERMITE)),
    /// 出曲柄的斜率
    OUT_SLOPE("inspector.key.out_slope", 3, EnumSet.of(EvaluateMode.HERMITE)),
    /// 入曲柄的长度：相邻帧间隔 1/3 的倍数
    IN_LENGTH("inspector.key.in_length", 3, EnumSet.of(EvaluateMode.HERMITE)),
    /// 出曲柄的长度
    OUT_LENGTH("inspector.key.out_length", 3, EnumSet.of(EvaluateMode.HERMITE));

    private final String labelKey;
    private final int decimals;
    private final Set<EvaluateMode> modes;

    KeyField(String labelKey, int decimals, Set<EvaluateMode> modes) {
        this.labelKey = labelKey;
        this.decimals = decimals;
        this.modes = Collections.unmodifiableSet(modes);
    }

    /// 界面标签的语言键
    public String labelKey() {
        return labelKey;
    }

    /// 界面显示的小数位
    public int decimals() {
        return decimals;
    }

    /// 该模式下这个槽位是否参与求值——**也正是"面板该不该显示这一行"的判据**
    public boolean activeIn(EvaluateMode mode) {
        return modes.contains(mode);
    }
}
