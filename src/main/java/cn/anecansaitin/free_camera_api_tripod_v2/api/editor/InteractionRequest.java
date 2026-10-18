package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// 带选项的交互请求（主设计 §9.2）。
///
/// 消息使用不可变 [LocalizedMessage]；选项至少一个且标识在请求内唯一。请求过期、会话关闭
/// 或选项无效时必须拒绝，不能执行旧回调。
///
/// @param id      请求身份
/// @param message 不可变消息
/// @param options 选项；顺序稳定，不保留调用方容器
@NullMarked
public record InteractionRequest(InteractionId id, LocalizedMessage message, List<InteractionOption> options) {

    public InteractionRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(message, "message");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        options = List.copyOf(options);

        if (options.isEmpty()) {
            throw new IllegalArgumentException("InteractionRequest requires at least one option");
        }

        Set<String> ids = new HashSet<>();
        for (InteractionOption option : options) {
            if (!ids.add(option.id())) {
                throw new IllegalArgumentException("InteractionRequest option ids must be unique: " + option.id());
            }
        }
    }
}
