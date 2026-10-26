package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 有界资源枚举结果。
///
/// [entries] 已按资源身份稳定排序，最多包含调用方给出的条数；[truncated] 表示磁盘上还有
/// 被省略的合法资源。宿主应把 `truncated` 显示为“列表已截断”，不能把截断后的列表当成
/// 完整资源集合；显式 [ResourceId] 的载入不受该上限影响。
///
/// @param entries   资源条目；只读副本
/// @param truncated 是否还有未返回的合法资源
@NullMarked
public record ResourceListing(List<AnimationResourceEntry> entries, boolean truncated) {

    public ResourceListing {
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
    }
}
