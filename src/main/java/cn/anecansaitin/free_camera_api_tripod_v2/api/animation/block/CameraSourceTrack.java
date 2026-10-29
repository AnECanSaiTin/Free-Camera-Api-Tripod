package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 文档级相机来源轨道：`Player` / `Rig(RigId)`，仅允许 STEP（主设计 §3.1.1、D22）。
///
/// ## 取代固定主机位
///
/// 旧实现用文档里一个可空 `primaryRigId` 指定玩家相机来源；最新设计改为本轨道，
/// 每个时刻显式选择来源，因此可以在播放中途从 Player 交给某台机位、或从一台机位
/// 交给另一台。来源切换是**立即交接**，不做交叉淡化（主设计 §3.1.1、§7.5）。
///
/// ## 校验口径
///
/// - 引用 `Rig(RigId)` 时，机位必须存在。
/// - 验证器**合并来源轨道与活动轨道的全部阶跃边界**，逐个区间检查：被选择的来源机位
///   必须在整个区间活动；最终时刻同样检查（主设计 §3.1.1）。
/// - 删除机位或关闭其静态总开关，必须在同一候选里修复来源轨道，否则整份提交被拒绝。
/// - 不得自动选择第一台活动机位，也不得在运行期自动交还 Player（主设计 §7.2）。
///
/// 运行期来源失活属于无效文档或准备错误：正式播放在准备阶段失败并释放相机控制，
/// 不静默改选来源（主设计 §7.3）。
///
/// ## 与合成、监听、音频的关系
///
/// - 来源是 Player 时保留正常玩家画面作为底图，活动机位按布局叠加；
///   来源是 Rig 时该机位复用正常世界画面（主设计 §7.6）。
/// - 声音监听器跟随本帧来源的最终姿态，副机位不得更新监听器（主设计 §7.6、音频设计 §6.4）。
/// - 来源采样不依赖控制事件或播放历史，直接定位与顺序播放一致。
///
/// ## 身份与不可变
///
/// 本轨道属于文档根，`TrackId` 与全部机位通道、注视 / 活动轨道共用文档内唯一空间；
/// 键共用 `KeyId`。键集合防御性复制并校验严格递增、时间唯一；`with*` 返回派生对象。
///
/// @param defaultValue 首键前的显式默认值；文档默认 `Player`
@NullMarked
public record CameraSourceTrack(
        TrackId id,
        String name,
        CameraSource defaultValue,
        List<ValueKeyframe<CameraSource>> keys) implements KeyframeTrack<CameraSource>, RenamableTrack {

    /// 相机来源轨道的类型标识。
    public static final Identifier TYPE =
            Identifier.fromNamespaceAndPath("free_camera_api_tripod", "camera_source");

    /// 新建轨道时的默认显示名；作者可改，改名不改 `TrackId`。
    public static final String DEFAULT_NAME = "camera_source";

    public CameraSourceTrack {
        if (name.isBlank()) {
            throw new IllegalArgumentException("CameraSourceTrack name must not be blank");
        }

        Objects.requireNonNull(defaultValue, "defaultValue");

        // 防御性复制 + 严格递增 + 时间唯一（主设计 §4.4）
        keys = KeyframeSequences.canonical(keys);
    }

    /// 默认来源轨道：默认 `Player`、无键（不接管玩家相机）。
    public static CameraSourceTrack player(TrackId id) {
        return new CameraSourceTrack(id, DEFAULT_NAME, CameraSource.player(), List.of());
    }

    /// 显式构造：指定默认来源与键。
    public static CameraSourceTrack of(TrackId id,
                                       String name,
                                       CameraSource defaultValue,
                                       List<ValueKeyframe<CameraSource>> keys) {
        return new CameraSourceTrack(id, name, defaultValue, keys);
    }

    @Override
    public Identifier type() {
        return TYPE;
    }

    @Override
    public CameraSourceTrack withName(String newName) {
        return new CameraSourceTrack(id, newName, defaultValue, keys);
    }

    @Override
    public CameraSourceTrack withDefaultValue(CameraSource newDefaultValue) {
        return new CameraSourceTrack(id, name, newDefaultValue, keys);
    }

    @Override
    public CameraSourceTrack withKeys(List<ValueKeyframe<CameraSource>> newKeys) {
        return new CameraSourceTrack(id, name, defaultValue, newKeys);
    }

    /// 换身份，其余不变。只在**复制轨道**时由 `IdGenerator` 配套使用。
    @Override
    public CameraSourceTrack withId(TrackId newId) {
        return new CameraSourceTrack(newId, name, defaultValue, keys);
    }

    /// 结构 MapCodec：`id / name / default / keys`；来源按 `Player` / `Rig` 分支写入。
    public static final MapCodec<CameraSourceTrack> MAP_CODEC =
            RecordCodecBuilder.<CameraSourceTrack>mapCodec(instance -> instance.group(
                    TrackId.CODEC.fieldOf("id").forGetter(CameraSourceTrack::id),
                    Codecs.nonBlankString().fieldOf("name").forGetter(CameraSourceTrack::name),
                    CameraSource.CODEC.fieldOf("default").forGetter(CameraSourceTrack::defaultValue),
                    ValueKeyframe.codec(CameraSource.CODEC).listOf().fieldOf("keys")
                            .forGetter(CameraSourceTrack::keys)
            ).apply(instance, CameraSourceTrack::new));

    /// 独立 Codec。
    public static final Codec<CameraSourceTrack> CODEC = MAP_CODEC.codec();
}
