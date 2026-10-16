package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id;

import org.jspecify.annotations.NullMarked;

/// 文档对象的稳定 ID（主设计 D20 / §3.1）。
///
/// 需要稳定身份的对象：机位、轨道、关键帧、路径节点、标记、世界采样、音频引用与
/// 成片叠加元素。它们的 ID 在**各自 ID 类型的文档作用域内唯一并持久化**；
/// 移动、排序、重命名与撤销都保留 ID，复制对象生成新 ID，整份换档建立新的文档作用域。
///
/// 这里刻意不用裸 `String` 表达身份：`TrackId` 与 `RigId` 是两种不同的东西，
/// 混用会让“把机位 ID 传成轨道 ID”在编译期看不出来。
///
/// 与**类型标识**的分工见主设计 §3.1：类型标识用 `net.minecraft.resources.Identifier`
/// （开放注册表分派），文档对象身份用本接口的实现（稳定 ID），用户可见名称用 `String`。
/// 三者不互相替代。
///
/// ## 不可变
///
/// 全部实现都是 `record`，构造时校验取值非空白，因此不存在“半合法”的 ID 对象在文档里流动。
/// 第三方扩展**不**实现本接口：稳定 ID 的取值集合由框架固定，开放的是类型注册表。
@NullMarked
public sealed interface DocumentObjectId
        permits RigId, TrackId, KeyId, PathNodeId, MarkerId, SampleId, AudioRefId, OverlayId {

    /// ID 的持久化取值。
    ///
    /// 该值参与 Codec 往返与网络传输，且是唯一判定相等的依据；不做本地化、不做展示格式化。
    String value();
}
