package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 机位的注视状态轨道：`Off` / `Target(AimTarget)`，仅允许 STEP（主设计 §3.1.1、§6.3–§6.4）。
///
/// ## 用两个键表达目标切换
///
/// “前 10 秒注视 A，10 秒起注视 B”就是 0 秒 `Target(A)`、10 秒 `Target(B)`：
/// `[0,10)` 注视 A，10 秒起立即注视 B。目标整体切换，不引入双目标混合、平滑过渡或
/// 交叉淡化；20 秒结束应由实际动画长度或后续键明确，不从“前后各十秒”的标签推断。
///
/// 不需要 Tick 事件、公式或“按时间返回 A/B 的自定义 Anchor”：控制状态由时间纯求值。
///
/// ## 交还旋转
///
/// “前 1 秒注视，后 1 秒按旋转关键帧”用同一时间轴：本轨道 0 秒 `Target(A)`、1 秒 `Off`；
/// 旋转通道 1 秒 R₁、2 秒 R₂。`[0,1)` 覆盖掩码启用的 Yaw/Pitch，Roll 来自旋转通道；
/// 从恰好 1 秒起（含该时刻）停止覆盖，采用当前旋转轨道结果。`Off` 不保存也不恢复
/// 进入注视前的旧旋转，不自动插入旋转键；没有旋转通道时仍按 ABSENT 补基础姿态，
/// 不冻结上一帧。交还可能有角度跳变，需要连续衔接时使用显式姿态捕获工具
/// （主设计 §4.7，编辑器命令随 edit / editor 批次补齐）。
///
/// ## 实际依赖
///
/// 只有**当前选择结果**指向的目标进入本帧解析：切换 A/B 之前不解析未来目标，
/// 未被选中的目标不要求当前存在；但全部键的类型、配置与文档引用必须静态合法
/// （主设计 §3.1.1、§7.3）。
///
/// ## 覆盖
///
/// 播放请求与预览可按 `RigId` 覆盖注视（继承 / 显式禁用 / 替换固定 Target），
/// 覆盖不回写本轨道；三态覆盖类型属于 `api.animation.playback` 契约
/// （P1 计划 §7.3 Q5）。文档内不存在“可空注视目标”这种形态。
///
/// ## 身份与不可变
///
/// `id` 是稳定身份，与数值轨道共用文档内唯一的 `TrackId` 空间；键与数值键共用 `KeyId`。
/// 键集合在构造时防御性复制并校验严格递增、时间唯一；`with*` 返回派生对象。
@NullMarked
public record AimTrack(
        TrackId id,
        String name,
        AimState defaultValue,
        List<ValueKeyframe<AimState>> keys) implements KeyframeTrack<AimState> {

    /// 注视状态轨道的类型标识。
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath("free_camera_api_tripod", "aim");

    /// 新建轨道时的默认显示名；作者可改，改名不改 `TrackId`。
    public static final String DEFAULT_NAME = "aim";

    public AimTrack {
        if (name.isBlank()) {
            throw new IllegalArgumentException("AimTrack name must not be blank");
        }

        Objects.requireNonNull(defaultValue, "defaultValue");

        // 防御性复制 + 严格递增 + 时间唯一（主设计 §4.4）
        keys = KeyframeSequences.canonical(keys);
    }

    /// 默认注视轨道：默认 `Off`、无键。这是“机位不注视任何目标”的标准形态。
    public static AimTrack off(TrackId id) {
        return new AimTrack(id, DEFAULT_NAME, AimState.off(), List.of());
    }

    /// 显式构造：自定义默认值与键。
    public static AimTrack of(TrackId id,
                              String name,
                              AimState defaultValue,
                              List<ValueKeyframe<AimState>> keys) {
        return new AimTrack(id, name, defaultValue, keys);
    }

    @Override
    public Identifier type() {
        return TYPE;
    }

    @Override
    public AimTrack withName(String newName) {
        return new AimTrack(id, newName, defaultValue, keys);
    }

    @Override
    public AimTrack withDefaultValue(AimState newDefaultValue) {
        return new AimTrack(id, name, newDefaultValue, keys);
    }

    @Override
    public AimTrack withKeys(List<ValueKeyframe<AimState>> newKeys) {
        return new AimTrack(id, name, defaultValue, newKeys);
    }

    /// 换身份，其余不变。只在**复制轨道**时由 `IdGenerator` 配套使用。
    @Override
    public AimTrack withId(TrackId newId) {
        return new AimTrack(newId, name, defaultValue, keys);
    }
}
