package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// 按稳定 ID 查曲线轨道的最小查询面（主设计 §6.1、§3.4）。
///
/// ## 为什么在求值层
///
/// 本接口只回答“这个 `TrackId` 对应的数值曲线是什么”，属于**采样与求值的输入端口**，
/// 所以按 Q6 裁决放在 `api.animation.eval`，而不是值源 / 编译层：
///
/// - 依赖方向保持 `eval → 动画模型`（主设计 §2.1：eval 允许依赖动画与表达式模型）；
///   文档包不再反过来依赖求值层，也不会出现 `block ↔ expression` 的包循环；
/// - 求值上下文与缓存**只**依赖这份最小面：采样器与变量求值只需要 `TrackLookup`；
///   需要解析变量 / 函数名字的地方才要完整符号解析；
/// - 编译器批次不再需要为了“按 ID 找曲线”而依赖文档模型。
///
/// 旧实现把“名字解析”和“轨道查找”混在一个接口里（`Resolver extends TrackLookup`），
/// 于是“画一条曲线”也得先拿到整个名字解析器。
///
/// ## 绑定
///
/// 本接口的实例绑定到**某一份快照**。文档根不再提供 `trackLookup()` 一类便捷方法
/// （那会让文档包反向依赖求值层）；绑定由求值层在建立 `EvaluationContext` /
/// 帧求值环境时完成，文档版本变化后必须重新绑定，不能清空数值缓存后继续引用旧文档
/// （主设计 §3.4）。
///
/// ## 文档内唯一性使一维键成立
///
/// `TrackId` 在整份文档内唯一（跨机位也唯一），因此这里用**一个**轨道 ID 查询，
/// 不引入“机位 + 轨道”二元键（主设计 §3.1）。
///
/// ## 返回值与机位上下文
///
/// 返回的是不可变 [CurveTrack]，不含求值结果。**机位上下文不影响这里的查找**
/// （ID 已全局唯一），但影响后续求值：
///
/// - 被引用轨道上的公式若读取空间事实，求值与缓存必须带 `RigId`（主设计 §6.1）；
/// - 无机位上下文的入口（例如全局符号表试算）必须**显式指定机位**或如实报告缺失，
///   不能悄悄挑一台机位求值。
///
/// 注视 / 活动 / 相机来源是**非数值状态轨道**，不实现 `CurveTrack`，因此不会被本接口
/// 返回；`TrackRef` 指向它们必须被静态校验拒绝（主设计 §3.1.1）。
@NullMarked
@FunctionalInterface
public interface TrackLookup {

    /// 按稳定 ID 取曲线轨道；该 ID 不存在或不是曲线轨道时返回空。
    Optional<CurveTrack> findCurve(TrackId trackId);
}
