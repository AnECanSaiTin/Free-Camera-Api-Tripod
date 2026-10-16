package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// 按稳定 ID 查曲线轨道的最小查询面（主设计 §6.1、§3.4）。
///
/// ## 为什么单独给一个接口
///
/// 求值环境需要“按 ID 取曲线”，但只需要这一件事——它不认识符号表、机位或文档。
/// 把它单独声明，是为了让求值上下文与缓存**只**依赖这份最小面：
///
/// - 采样器与变量求值只需要 `TrackLookup`；
/// - 需要解析名字（变量、函数）的地方才要完整解析器。
///
/// 旧实现把这两件事混在一个接口里（`Resolver extends TrackLookup`），
/// 于是“画一条曲线”也得先拿到整个名字解析器。
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
/// ## 绑定与失效
///
/// 本接口的实例绑定到**某一份快照**。文档版本变化后必须重新绑定，
/// 不能清空数值缓存后继续引用旧文档（主设计 §3.4）。
@NullMarked
@FunctionalInterface
public interface TrackLookup {

    /// 按稳定 ID 取曲线轨道；该 ID 不存在或不是曲线轨道时返回空。
    Optional<CurveTrack> findCurve(TrackId trackId);
}
