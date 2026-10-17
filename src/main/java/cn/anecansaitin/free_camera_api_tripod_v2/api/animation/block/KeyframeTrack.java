package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 有类型关键帧轨道：与数值曲线共用时间轴基础设施，值用原生类型（主设计 §3.1.1、D28）。
///
/// ## 为什么不是数值曲线
///
/// 注视（`Off` / `Target(AimTarget)`）、机位活动状态（`boolean`）与玩家相机来源
/// （`Player` / `Rig(RigId)`）都是**离散状态**：它们在键时间点整体切换，不能插值，
/// 也不能编码成浮点数。把它们塞进 [CurveTrack] 需要三件错事：
///
/// - 用 `NumberSource` / 数值 `KeyField` 承载对象，靠魔法数字或哈希区分目标；
/// - 让四个曲柄参数与 LINEAR / HERMITE 模式出现在只允许 STEP 的轨道上；
/// - 让 `TrackRef` 有机会读到“目标”这种非数值状态。
///
/// 本节契约按主设计 §3.1.1 采用**有类型的**关键帧轨道，而不是另建 `StepProperty`
/// 或第二套状态键编辑系统：TrackId、KeyId、秒基准时间、排序、增删 / 移动 / 框选 /
/// 吸附 / 复制粘贴、事务、撤销与 Codec / 校验流程全部与数值轨道共用。
///
/// ## 三个内建分支
///
/// | 分支 | 值类型 | 默认值 | 所有者 |
/// | --- | --- | --- | --- |
/// | [AimTrack] | [AimState]（`Off` / `Target`） | `Off` | `CameraRig.aim` |
/// | [ActiveTrack] | `boolean` | `true` | `RigView.active` |
/// | [CameraSourceTrack] | [CameraSource]（`Player` / `Rig`） | `Player` | `CameraAnimation.cameraSource` |
///
/// 分支是 **sealed** 的：这些值类型由框架固定，不通过轨道类型注册表开放；
/// 第三方数据仍走 `AnimationTrack.ExtensionTrack`（主设计 §8.1 不新增第四类开放注册表）。
/// sealed 也让“布尔轨道配注视值”这种错配在编译期就失败。
///
/// ## 仅 STEP 的求值语义
///
/// 首键之前用 `defaultValue`，键时间起（含该时刻）用该键值直到下个键，末键之后保持。
/// 这正是 [at] 的口径，也是求值层与时间轴预览共用的唯一规则：
///
/// - 时间不加历史：`[0,1)` 注视 A、1 秒 `Off`，则 1 秒起立即使用当前旋转通道结果，
///   不恢复进入注视前的角度（主设计 §3.1.1、§6.4）。
/// - 直接定位与顺序播放结果一致，因为没有“上一帧状态”可依赖。
///
/// ## 单一所有权与派生目录
///
/// 命中这三个入口的轨道**只持久化一次**：它属于机位或文档，`tracks` 通用表里不再存
/// 一份可独立修改的副本。统一时间轴从文档级轨道、机位通道及这些入口**派生**只读目录，
/// 全部 `TrackId` 在文档内唯一（主设计 §3.1.1）。
///
/// `TrackLookup` 只返回 [CurveTrack]；引用非数值状态轨道的 `TrackRef` 必须在静态校验
/// 中拒绝（主设计 §3.1.1）。
///
/// ## 不可变
///
/// 键集合在构造时防御性复制并校验时间唯一；`with*` 返回派生对象，不修改自身。
@NullMarked
public sealed interface KeyframeTrack<T> extends AnimationTrack
        permits AimTrack, ActiveTrack, CameraSourceTrack {

    /// 首键之前使用、空轨唯一使用的显式默认值。
    ///
    /// 默认值是**作者写下的**创作数据，不是运行期兜底：求值失败仍按错误处理，
    /// 不得用默认值替代失败结果（主设计 §6.1、D18）。
    T defaultValue();

    /// 严格按时间递增的关键帧；已防御性复制。
    List<ValueKeyframe<T>> keys();

    /// 轨道类型标识：由各分支固定，不走开放注册表。
    @Override
    Identifier type();

    /// 最后一个不晚于 `time` 的键；没有这样的键返回空。
    ///
    /// 这是纯时间查询：不读取世界、不做插值、不产生副作用。
    ///
    /// @param time 秒；必须有限（时间域检查属于求值层，这里只拒绝明显非法输入）
    default @Nullable ValueKeyframe<T> keyAt(float time) {
        requireFiniteTime(time);

        List<ValueKeyframe<T>> keys = keys();
        int low = 0;
        int high = keys.size() - 1;
        int found = -1;

        while (low <= high) {
            int middle = (low + high) >>> 1;

            if (keys.get(middle).time() <= time) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        return found < 0 ? null : keys.get(found);
    }

    /// STEP 求值：该时刻生效的状态值。
    ///
    /// 首键前返回 [defaultValue]，键时间起返回该键值（含该时刻），末键后保持。
    ///
    /// @param time 秒；必须有限
    default T at(float time) {
        ValueKeyframe<T> key = keyAt(time);

        return key == null ? defaultValue() : key.value();
    }

    /// 该轨道最后一个键的时间，单位秒；没有键时为 0。
    ///
    /// 这是**末键时刻**（相对文档时间轴原点），不是首键到末键的跨度。
    /// 它不是动画时长：文档时长还取启用机位通道、相机来源、文档轨道、标记与叠加范围的
    /// 有限最大结束时间（主设计 §3.1）。
    @Override
    default float duration() {
        List<ValueKeyframe<T>> keyframes = keys();

        return keyframes.isEmpty() ? 0f : keyframes.getLast().time();
    }

    /// 键数量；界面用它判断轨道是否为空，序列化用它做规模校验。
    @Override
    default int keyCount() {
        return keys().size();
    }

    /// 换显示名，**TrackId 不变**——改名不会让时间轴选择或外部引用失效。
    KeyframeTrack<T> withName(String name);

    /// 换显式默认值；键集合不变。构造器会重新校验值与集合。
    KeyframeTrack<T> withDefaultValue(T value);

    /// 换键集合；默认值不变。构造器会重新校验严格递增与时间唯一。
    KeyframeTrack<T> withKeys(List<ValueKeyframe<T>> keys);

    /// 换身份，其余不变。只在**复制轨道**时由 `IdGenerator` 配套使用。
    KeyframeTrack<T> withId(TrackId newId);

    private static void requireFiniteTime(float time) {
        if (!Float.isFinite(time)) {
            throw new IllegalArgumentException("KeyframeTrack query time must be finite: " + time);
        }
    }
}
