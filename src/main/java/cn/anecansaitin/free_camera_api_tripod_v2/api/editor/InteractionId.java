package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import org.jspecify.annotations.NullMarked;

/// 交互请求身份（主设计 §9.2）。
///
/// 请求身份只用于关联当前会话中的待处理请求；过期、已关闭或无效选项必须拒绝，不能执行
/// 旧回调。
///
/// @param value 非负请求序号
@NullMarked
public record InteractionId(long value) {

    public InteractionId {
        if (value < 0) {
            throw new IllegalArgumentException("InteractionId must be non-negative: " + value);
        }
    }
}
