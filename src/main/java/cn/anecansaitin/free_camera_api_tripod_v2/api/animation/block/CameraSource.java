package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;

/// 某一时刻驱动玩家相机的来源：`Player` 或 `Rig(RigId)`（主设计 §3.1.1、D22）。
///
/// ## 取消固定主机位
///
/// 旧实现用“文档里一个可空 `primaryRigId`”表示玩家相机由哪台机位驱动，等于把来源
/// 钉死成整个文档一个值。主设计 D22 取消该字段，改为文档级 `cameraSource` 阶跃轨道：
/// 每个时刻显式选择 Player 或某台活动机位，来源可随时间切换。
///
/// - `Player`：不接管玩家相机，玩家保持正常控制与画面；活动机位仍按布局叠加合成。
/// - `Rig`：引用必须存在，且在其被选择的每个区间都处于活动状态
///   （`view.enabled && view.active.at(t)`）。来源失活属于无效文档或准备错误，
///   运行期不得自动交还 Player，也不得自动改选其他机位（主设计 §3.1.1、§7.2）。
///
/// ## 为什么不用可空 RigId
///
/// 可空只能表达“有 / 没有”，不能表达“作者显式选择 Player”这一创作意图；
/// 而 `Player` 与“未选择”在轨道里必须是同一个可校验的取值。显式 `Player` 也保证
/// 编辑器与校验器不必猜测空值的含义。
///
/// ## 来源与活动状态是两件事
///
/// 来源轨道与活动轨道在验证器中**合并全部阶跃边界**逐区间检查；机位排序、UI 选择、
/// 性能降级都不能自行改变来源（主设计 §3.1.1、§7.6）。
///
/// ## 调用方
///
/// 运行期由 `CameraAnimation.cameraSource.at(t)` 取得本帧来源；它不依赖控制事件或
/// 播放历史，因此直接定位与顺序播放结果一致（主设计 §3.1.1、独立音频设计 §6.4）。
@NullMarked
public sealed interface CameraSource permits CameraSource.Player, CameraSource.Rig {

    /// 玩家正常相机：播放不接管玩家视角，活动机位只参与合成。
    record Player() implements CameraSource {
    }

    /// 由某台机位驱动玩家相机。
    ///
    /// @param rigId 来源机位；必须存在，且在被选择的每个区间活动
    record Rig(RigId rigId) implements CameraSource {

        public Rig {
            Objects.requireNonNull(rigId, "rigId");
        }
    }

    /// 玩家相机的共享取值。
    CameraSource PLAYER = new Player();

    /// 玩家正常相机。
    static CameraSource player() {
        return PLAYER;
    }

    /// 由给定机位驱动玩家相机。
    static CameraSource rig(RigId rigId) {
        return new Rig(rigId);
    }

    /// 来源机位；`Player` 时为空。
    default Optional<RigId> rigOptional() {
        return this instanceof Rig rig ? Optional.of(rig.rigId()) : Optional.empty();
    }

    /// 是否由机位驱动玩家相机。
    default boolean fromRig() {
        return this instanceof Rig;
    }
}
