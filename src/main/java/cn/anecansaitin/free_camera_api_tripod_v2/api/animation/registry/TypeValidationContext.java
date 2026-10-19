package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.DocumentObjectId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 类型级校验钩子的上下文（主设计 §2.2、§4.2）。
///
/// 校验器只校验一个已解码类型实例；文档级引用、区间与模式约束仍由统一
/// `DocumentValidator` 负责。上下文提供版本与稳定定位，类型校验器不得读取当前 store。
@NullMarked
public record TypeValidationContext(
        Revision revision,
        DocumentObjectId objectId,
        @Nullable RigId ownerRigId,
        String fieldPath) {

    public TypeValidationContext {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(objectId, "objectId");

        if (fieldPath.isBlank()) {
            throw new IllegalArgumentException("TypeValidationContext fieldPath must not be blank");
        }
    }
}
