package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 一个类型在**当前运行环境**中的可用状态（主设计 §2.2、§6.3、§7.7）。
///
/// ## 三个状态不能合并
///
/// | 状态 | 含义 | 处理 |
/// | --- | --- | --- |
/// | [TypeCapabilityStatus#AVAILABLE] | 类型已注册且客户端实现到位 | 可解码、可播放/预览 |
/// | [TypeCapabilityStatus#TYPE_NOT_REGISTERED] | common 侧没有该类型 | 文档解码会失败；是结构错误 |
/// | [TypeCapabilityStatus#RUNTIME_IMPLEMENTATION_MISSING] | 类型存在但客户端解析器/采集器缺失 | 文档可读可编辑，但**不能完整播放/预览**；是运行能力缺失 |
///
/// 设计 §6.3 / §7.7 明确要求两类诊断分开报告：把运行能力缺失伪装成“类型不存在”会让
/// 文档看起来损坏，把类型不存在伪装成能力缺失会掩盖结构错误（不变量 I9）。
///
/// @param typeId 类型标识
/// @param status 当前状态
@NullMarked
public record TypeCapability(Identifier typeId, TypeCapabilityStatus status) {

    public TypeCapability {
        java.util.Objects.requireNonNull(typeId, "typeId");
        java.util.Objects.requireNonNull(status, "status");
    }

    /// 类型与实现都可用。
    public static TypeCapability available(Identifier typeId) {
        return new TypeCapability(typeId, TypeCapabilityStatus.AVAILABLE);
    }

    /// common 侧未注册该类型。
    public static TypeCapability typeNotRegistered(Identifier typeId) {
        return new TypeCapability(typeId, TypeCapabilityStatus.TYPE_NOT_REGISTERED);
    }

    /// 类型已注册，但客户端解析器/采集器缺失。
    public static TypeCapability runtimeImplementationMissing(Identifier typeId) {
        return new TypeCapability(typeId, TypeCapabilityStatus.RUNTIME_IMPLEMENTATION_MISSING);
    }

    public boolean available() {
        return status == TypeCapabilityStatus.AVAILABLE;
    }
}
