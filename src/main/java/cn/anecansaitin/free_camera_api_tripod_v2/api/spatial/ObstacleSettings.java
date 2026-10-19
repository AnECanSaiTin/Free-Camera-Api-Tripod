package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/// 机位的避障默认设置（主设计 §6.4、D3）。
///
/// ## 语义
///
/// 避障在注视之后**只修正位置**，不再次注视；“精确朝向目标”指避障前的姿态。
/// 结果**不反写文档、曲线或试算**——它是每帧的姿态修正，不是创作数据。
///
/// 碰撞起点按空间分两种（主设计 §6.4）：
///
/// - 局部机位：以**解析锚点**为起点；
/// - 世界机位：以**本帧基础相机位置**为起点。
///
/// 它限制的是“起点到期望位置”这条线段上的墙体遮挡，**不是**带历史的运动碰撞模拟：
/// 没有速度、没有反弹、不记忆上一帧。这一点必须写进帮助文案，否则作者会期待物理行为。
///
/// ## 与性能降级的分界
///
/// 避障改变实际姿态，因此**预览与正式播放必须走同一条宿主路径**（主设计 §5.5 对同一问题的口径），
/// 否则“预览看到的”与“播出来的”会不一致。
/// 宿主不支持时属于**能力缺失**：要么在静态校验/能力检查阶段拒绝，要么如实报告，
/// 不能悄悄跳过避障继续播。
///
/// @param enabled    是否启用。关闭时宿主不得做任何位置修正
/// @param margin     相机与障碍之间保留的距离，单位格；必须有限且非负
@NullMarked
public record ObstacleSettings(boolean enabled, double margin) {

    /// 默认：不启用避障，保留距离按宿主建议值。
    ///
    /// 默认关闭是因为避障需要宿主逐路适配与验证（主设计 §6.4 指出旧 `enableObstacle()`
    /// 围绕玩家眼睛做第三人称避障，不能直接代替任意机位算法）；作者显式打开才生效。
    public static final ObstacleSettings DISABLED = new ObstacleSettings(false, 0.1);

    /// 保留距离字段：有限且非负。
    ///
    /// 有限性由 [Codecs#finiteDouble] 保证；负值在这里转成解码错误，
    /// 避免构造器的异常穿透解码边界。
    private static final Codec<Double> MARGIN_CODEC = Codecs.finiteDouble().comapFlatMap(
            margin -> margin < 0
                    ? DataResult.error(() -> "Obstacle margin must be finite and non-negative: " + margin)
                    : DataResult.success(margin),
            margin -> margin);

    /// 结构编码：`{enabled, margin}`。
    ///
    /// `margin` 单位格，必须有限且非负；字段必填，不写默认值。
    public static final MapCodec<ObstacleSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(ObstacleSettings::enabled),
            MARGIN_CODEC.fieldOf("margin").forGetter(ObstacleSettings::margin)
    ).apply(instance, ObstacleSettings::new));

    /// 独立解析避障设置时使用的 Codec。
    public static final Codec<ObstacleSettings> CODEC = MAP_CODEC.codec();

    public ObstacleSettings {
        if (!Double.isFinite(margin) || margin < 0) {
            throw new IllegalArgumentException("Obstacle margin must be finite and non-negative: " + margin);
        }
    }

    public ObstacleSettings withEnabled(boolean value) {
        return new ObstacleSettings(value, margin);
    }

    public ObstacleSettings withMargin(double value) {
        return new ObstacleSettings(enabled, value);
    }
}
