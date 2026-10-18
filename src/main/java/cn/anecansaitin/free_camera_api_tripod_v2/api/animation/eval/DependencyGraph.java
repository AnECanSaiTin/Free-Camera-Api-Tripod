package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Set;

/// 从完整文档与编译后语法树派生的依赖图（主设计 §4.2、§3.4、D9）。
///
/// 依赖图由文档整体派生，不在文档对象里维护独立的增量状态。它负责报告未定义引用与环，
/// 但无环不代表调用深度或求值工作量有界：静态可判定的最长调用链仍须校验，运行期继续执行
/// 资源上限。
///
/// @see EvaluationCache
@NullMarked
public interface DependencyGraph {

    /// 某条轨道直接依赖的其它轨道；无依赖返回空集合。
    Set<TrackId> dependenciesOf(TrackId trackId);

    /// 建立依赖图时发现的诊断（未定义引用、环、函数参数个数等）。
    List<Diagnostic> diagnostics();
}
