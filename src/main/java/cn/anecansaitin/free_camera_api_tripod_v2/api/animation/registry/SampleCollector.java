package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleArgs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 世界采样的**客户端采集器**：把静态参数读成本帧读数（主设计 §7.7、D24）。
///
/// ## 契约
///
/// - 只读已加载且客户端可见的数据；不触发区块加载、不请求服务端同步；
/// - 取不到数据（未加载 / 未同步 / 目标不存在）返回 [SampleReading.Unknown]，
///   由 `sample(name, fallback)` 决定是否兜底；
/// - 采集器缺失、抛异常、输出非法或超预算返回 [SampleReading.CollectionError]，
///   **不可**被 fallback 兜底；
/// - 实现不得把上一周期成功值伪装成新读数；重采周期由帧准备方控制；
/// - 实现是无状态的（读数缓存由帧准备方管理），同一采集器可被多个声明复用。
///
/// 类型缺失在解码期报错；采集器缺失只是运行能力缺失，由 [TypeCapabilityService] 报告。
@NullMarked
public interface SampleCollector {

    /// 本采集器负责的采样类型 ID，必须与注册的类型一致。
    Identifier typeId();

    /// 采集一个样本声明。
    ///
    /// @param args    该样本的静态参数；`args.type()` 必须等于 [typeId]
    /// @param context 本帧客户端世界读数与共享执行会话
    /// @return 读数；失败必须落在 UNKNOWN 或 COLLECTION_ERROR，不得返回 null
    SampleReading collect(SampleArgs args, SampleCollectionContext context);
}
