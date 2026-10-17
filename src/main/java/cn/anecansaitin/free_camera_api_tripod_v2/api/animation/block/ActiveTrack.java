package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 机位活动状态轨道：`boolean`，仅允许 STEP（主设计 §3.1.1、§7.6）。
///
/// ## 三个“启用”不能混为一谈
///
/// | 概念 | 落点 | 谁改 |
/// | --- | --- | --- |
/// | 静态作者总开关 | `RigView.enabled` | 作者编辑，进文档 |
/// | 本时刻活动状态 | 本轨道 `at(t)` | 作者编辑关键帧，进文档 |
/// | 宿主性能停路 / 降级 | 宿主运行状态 | 宿主，**不改写文档**（D23、§7.6） |
///
/// 运行活动状态是 `view.enabled && active.at(t)`：
///
/// - 不活动机位不主动准备姿态、矩形或渲染；重新活动时必须准备有效新帧，
///   不能先显示停用前的缓存画面（主设计 §7.6）。
/// - 不活动机位仍可被 `TrackRef` 引用：相关数值与空间依赖按**实际依赖**求值，
///   错误照常处理（主设计 §7.2）。
///
/// ## 示例
///
/// 第二机位在 10 秒出现：默认值 `false`（或 0 秒键 `false`），10 秒键 `true`；
/// 矩形在同一时刻可以是小窗，20 秒插值到半屏。第二路出现与“相机来源改为该机位”
/// 是两个独立的创作动作（主设计 §3.1.2 示例）。
///
/// 关闭静态总开关或删除机位时，若来源轨道仍选择该机位，必须在**同一候选**里修复来源，
/// 否则整份提交被拒绝（主设计 §3.1.1、§7.2）。
///
/// ## 身份与不可变
///
/// `id` 与数值轨道共用文档内唯一的 `TrackId` 空间；键共用 `KeyId`。
/// 键集合防御性复制并校验严格递增、时间唯一；`with*` 返回派生对象。
///
/// @param defaultValue 首键前的显式默认值；`true` 表示默认活动
@NullMarked
public record ActiveTrack(
        TrackId id,
        String name,
        Boolean defaultValue,
        List<ValueKeyframe<Boolean>> keys) implements KeyframeTrack<Boolean> {

    /// 活动状态轨道的类型标识。
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath("free_camera_api_tripod", "active");

    /// 新建轨道时的默认显示名；作者可改，改名不改 `TrackId`。
    public static final String DEFAULT_NAME = "active";

    public ActiveTrack {
        if (name.isBlank()) {
            throw new IllegalArgumentException("ActiveTrack name must not be blank");
        }

        Objects.requireNonNull(defaultValue, "defaultValue");

        // 防御性复制 + 严格递增 + 时间唯一（主设计 §4.4）
        keys = KeyframeSequences.canonical(keys);
    }

    /// 默认活动轨道：默认 `true`、无键。新建机位“默认一直出画面”的标准形态。
    public static ActiveTrack enabled(TrackId id) {
        return new ActiveTrack(id, DEFAULT_NAME, Boolean.TRUE, List.of());
    }

    /// 默认不活动轨道：默认 `false`、无键；用于“到某时刻才出现的第二路”。
    public static ActiveTrack disabled(TrackId id) {
        return new ActiveTrack(id, DEFAULT_NAME, Boolean.FALSE, List.of());
    }

    /// 显式构造：自定义默认值与键。
    public static ActiveTrack of(TrackId id,
                                 String name,
                                 boolean defaultValue,
                                 List<ValueKeyframe<Boolean>> keys) {
        return new ActiveTrack(id, name, defaultValue, keys);
    }

    @Override
    public Identifier type() {
        return TYPE;
    }

    @Override
    public ActiveTrack withName(String newName) {
        return new ActiveTrack(id, newName, defaultValue, keys);
    }

    @Override
    public ActiveTrack withDefaultValue(Boolean newDefaultValue) {
        return new ActiveTrack(id, name, newDefaultValue, keys);
    }

    @Override
    public ActiveTrack withKeys(List<ValueKeyframe<Boolean>> newKeys) {
        return new ActiveTrack(id, name, defaultValue, newKeys);
    }

    /// 换身份，其余不变。只在**复制轨道**时由 `IdGenerator` 配套使用。
    @Override
    public ActiveTrack withId(TrackId newId) {
        return new ActiveTrack(newId, name, defaultValue, keys);
    }
}
