package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;

/// 编辑工具：缓动、批量变换、烘焙与注视姿态捕获（主设计 §4.6、§4.7）。
///
/// 工具依赖冻结的求值上下文与事实，只生成一个完整候选；不读取当前 store、不修改文档、不发布。
/// 缓动 / 批量请求显式携带输入快照，同 ID 不同文档不能被当成同一输入。任一目标冲突、缺输入、
/// 未显式兜底的 UNKNOWN、ERROR 或超预算时整体拒绝，只产生一个撤销项。
@NullMarked
public interface EditTools {

    /// 对选中键应用显式缓动预设。
    ToolResult applyEasing(EasingRequest request);

    /// 对选中键执行时间域 / 值域批量变换。
    ToolResult applyBatchTransform(BatchTransformRequest request);

    /// 按冻结环境烘焙一条轨道。
    ToolResult bake(BakeRequest request);

    /// 捕获注视姿态并生成“旋转常量键 + 注视 Off”原子候选。
    ToolResult captureAim(AimCaptureRequest request);
}
