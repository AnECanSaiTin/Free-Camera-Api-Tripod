package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 交互请求的一个选项（主设计 §9.2）。
///
/// 选项标识在请求内唯一；二元确认也只是两个普通选项。
///
/// @param id         选项标识，非空白
/// @param label      不可变显示文本
/// @param destructive 是否为破坏性选项（界面可据此强调，不改变会话行为）
@NullMarked
public record InteractionOption(String id, LocalizedMessage label, boolean destructive) {

    public InteractionOption {
        if (id.isBlank()) {
            throw new IllegalArgumentException("InteractionOption id must not be blank");
        }

        Objects.requireNonNull(label, "label");
    }
}
