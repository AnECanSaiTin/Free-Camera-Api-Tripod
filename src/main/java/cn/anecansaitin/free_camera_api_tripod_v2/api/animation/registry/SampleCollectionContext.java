package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/// 采样采集器可读的本帧客户端世界事实（主设计 §7.7、D24）。
///
/// ## 只允许受控查询
///
/// 设计 §7.7 要求采样只读已加载数据、属性有类型白名单、不任意反射字段。因此上下文是
/// **受控查询面**，不是 `Level`：
///
/// - [blockState]：未加载 / 不可见时返回空；
/// - [lightLevel]：未加载时返回空；
/// - [entityAttribute]：按白名单属性 ID 读已同步实体属性，未知属性返回空；
/// - 不提供任意反射、方块实体 NBT、未同步记分板或触发加载的入口。
///
/// 后续类型（群系、天气、已同步记分板）需要新增查询方法时扩展本接口，不能把
/// `Level` / `Entity` 直接暴露给第三方采集器。
///
/// ## 预算
///
/// [session] 是本帧共享执行会话；采集器在做昂贵查询前应调用
/// [EvaluationSession#tryConsumeWork]，不足时返回
/// [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading.CollectionError]
/// 的 `BUDGET_EXCEEDED`，不能静默降级为 UNKNOWN。
@NullMarked
public interface SampleCollectionContext {

    /// 当前客户端维度身份。
    Identifier dimension();

    /// 当前世界实例身份，非负。
    long worldInstanceId();

    /// 采集发生时的游戏刻，非负。
    long gameTick();

    /// 区块是否已加载且客户端可见；实现不得触发加载。
    boolean chunkLoaded(int chunkX, int chunkZ);

    /// 读取已加载方块的方块状态；未加载返回空。
    Optional<BlockState> blockState(int x, int y, int z);

    /// 读取已加载位置的光照等级；未加载返回空。
    OptionalInt lightLevel(int x, int y, int z);

    /// 读取已同步实体的白名单属性；实体不存在、属性未知或数据未同步时返回空。
    Optional<Float> entityAttribute(UUID entityId, Identifier attributeId);

    /// 本帧共享执行会话（工作量预算与深度）。
    EvaluationSession session();
}
