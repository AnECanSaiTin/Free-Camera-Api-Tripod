package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 有类型轨道的键序列整理：排序、时间与 `KeyId` 唯一性校验、防御性复制。
///
/// 三个状态轨道分支共用同一套不变量（严格递增、同轨时间唯一、同轨 `KeyId` 唯一、
/// 不保留调用方容器），因此把整理逻辑收在一处，避免各 record 各写一遍后出现口径差异。
/// `KeyId` 重复会让“按 ID 改值”命中多个键，编辑目标出现歧义（主设计 §3.1）。
///
/// 本类不是公开契约：键的时间冲突由编辑层给出可操作的显式选择（覆盖 / 合并 / 跳过，
/// 主设计 §4.4），构造器抛异常只用于兜底，不作为常规冲突处理路径。
final class KeyframeSequences {

    private KeyframeSequences() {
    }

    static <T> List<ValueKeyframe<T>> canonical(List<ValueKeyframe<T>> keys) {
        List<ValueKeyframe<T>> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingDouble(ValueKeyframe::time));

        for (int i = 1; i < sorted.size(); i++) {
            float previous = sorted.get(i - 1).time();
            float current = sorted.get(i).time();

            if (current == previous) {
                throw new IllegalArgumentException(
                        "ValueKeyframe times must be unique within a track; duplicate at " + current + "s");
            }
        }

        Set<KeyId> keyIds = new HashSet<>(sorted.size());

        for (ValueKeyframe<T> key : sorted) {
            if (!keyIds.add(key.keyId())) {
                throw new IllegalArgumentException(
                        "ValueKeyframe key ids must be unique within a track; duplicate key id "
                                + key.keyId());
            }
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        return List.copyOf(sorted);
    }
}
