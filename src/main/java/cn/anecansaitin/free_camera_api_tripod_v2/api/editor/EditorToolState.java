package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import org.jspecify.annotations.NullMarked;

/// 编辑器当前工具状态（主设计 §9.1）。
///
/// 工具状态属于会话视图，不进入文档或撤销历史；拖动类编辑仍通过预览事务提交。
@NullMarked
public enum EditorToolState {

    /// 选择 / 指针。
    SELECT,

    /// 移动时间或对象。
    MOVE,

    /// 旋转。
    ROTATE,

    /// 缩放。
    SCALE,

    /// 布局矩形编辑。
    LAYOUT,

    /// 路径节点编辑。
    PATH
}
