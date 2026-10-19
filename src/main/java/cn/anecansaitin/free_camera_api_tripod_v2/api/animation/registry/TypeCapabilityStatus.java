package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import org.jspecify.annotations.NullMarked;

/// 类型能力状态（主设计 §2.2、§6.3、§7.7）。
///
/// 详细语义见 [TypeCapability]；本枚举只提供稳定状态值，避免调用方按字符串猜测。
@NullMarked
public enum TypeCapabilityStatus {

    /// 类型已注册，客户端执行部件也已注册。
    AVAILABLE,

    /// common 侧没有注册该类型；文档解码会失败。
    TYPE_NOT_REGISTERED,

    /// 类型已注册，但本环境缺少客户端解析器 / 采集器。
    RUNTIME_IMPLEMENTATION_MISSING
}
