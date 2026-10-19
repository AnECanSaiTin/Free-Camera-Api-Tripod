package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/// 锚点解析器可读的本帧客户端世界事实与诊断作用域（主设计 §6.3、§7.7；复核 R49-H2）。
///
/// ## 只读、不加载
///
/// 上下文只暴露**已加载且客户端可见**的数据：
///
/// - 实体 / 玩家不存在或未同步时返回空，解析器据此返回失败诊断；
/// - 不得通过本接口触发区块加载或强制同步；
/// - 维度与世界实例身份是缓存失效依据，解析结果不得跨世界实例复用。
///
/// ## 插值
///
/// [EntityReading] 同时给出当前与上一帧位置 / 姿态，解析器按
/// [partialTick] 自行插值并标记 `partialTickApplied`（主设计 §6.3：实体/玩家锚点按
/// partial tick 插值，固定锚点不插值）。瞬移、维度切换或无有效历史时使用当前值，
/// 不跨维度平滑。
///
/// ## 诊断作用域
///
/// [diagnosticScope] 绑定**正在解析的快照**的发布版本。同一个启动期注册的解析器会依次服务
/// 不同快照（正式播放、预览候选、不同版本），上下文是唯一能确定“失败属于哪一版”的来源：
/// 读取当前 store 会破坏冻结快照绑定，伪造文件作用域则把运行期错误伪装成解码错误。
@NullMarked
public interface AnchorResolutionContext {

    /// 当前客户端维度身份。
    Identifier dimension();

    /// 当前世界实例身份，非负；同维度重进世界也会变化。
    long worldInstanceId();

    /// 本帧 partial tick，`[0, 1)`。
    float partialTick();

    /// 本帧诊断作用域：与请求快照同一发布版本，恒为文档作用域。
    ///
    /// 解析失败必须用它构造 `Diagnostic`（可用 `SpatialResolution.failed(...)`），
    /// 不得读取当前 store 猜测版本，也不得使用文件作用域。
    DiagnosticScope.Document diagnosticScope();

    /// 按 UUID 读取已加载实体。
    Optional<EntityReading> entity(UUID entityId);

    /// 按 UUID 读取已同步玩家。
    Optional<EntityReading> player(UUID playerId);

    /// 一个可解析实体的本帧读数：当前值与上一帧值。
    ///
    /// @param position         当前（未插值）位置
    /// @param rotation         当前（未插值）姿态
    /// @param previousPosition 上一帧位置；无有效历史时等于当前值
    /// @param previousRotation 上一帧姿态；无有效历史时等于当前值
    /// @param hasHistory       是否存在可插值的历史；false 时解析器不得做时间插值
    record EntityReading(
            Vec3 position,
            Rotation rotation,
            Vec3 previousPosition,
            Rotation previousRotation,
            boolean hasHistory) {

        public EntityReading {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(rotation, "rotation");
            Objects.requireNonNull(previousPosition, "previousPosition");
            Objects.requireNonNull(previousRotation, "previousRotation");
        }
    }
}
