package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.QuaternionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 轨道池绑定依赖索引：由属性/来源绑定反向查询每条轨道被谁使用。
///
/// 用于删除影响面、轨道面板使用次数与属性面板“其他使用方”。未绑定轨道没有条目，但仍可能
/// 通过内容参与播放时长；本图只描述**显式绑定**关系，不做求值。
@NullMarked
public final class TrackPoolDependencyGraph {

    private final Map<TrackId, Set<Usage>> usages;

    private TrackPoolDependencyGraph(Map<TrackId, Set<Usage>> usages) {
        Map<TrackId, Set<Usage>> copy = new LinkedHashMap<>();

        for (Map.Entry<TrackId, Set<Usage>> entry : usages.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }

        this.usages = Collections.unmodifiableMap(copy);
    }

    public static TrackPoolDependencyGraph build(TrackPoolAnimation document) {
        Objects.requireNonNull(document, "document");
        Map<TrackId, Set<Usage>> usages = new LinkedHashMap<>();

        for (PooledRig rig : document.rigs()) {
            String base = "rigs[" + rig.id().value() + "].";
            var properties = rig.properties();
            numeric(usages, properties.position().x(), rig.id(), base + "position.x");
            numeric(usages, properties.position().y(), rig.id(), base + "position.y");
            numeric(usages, properties.position().z(), rig.id(), base + "position.z");
            rotation(usages, properties.rotation().source(), rig.id(), base + "rotation");
            numeric(usages, properties.fov().source(), rig.id(), base + "fov");
            bool(usages, properties.active().source(), rig.id(), base + "active");
            rect(usages, properties.rect(), rig.id(), base + "rect");
            bool(usages, properties.obstacle().enabled(), rig.id(), base + "obstacle.enabled");
            numeric(usages, properties.obstacle().margin(), rig.id(), base + "obstacle.margin");
        }

        if (document.cameraSourceBinding() instanceof PropertyBinding.Enabled<CameraSourceProperty> enabled
                && enabled.source() instanceof CameraSourceProperty.Track track) {
            add(usages, track.trackId(), new Usage(null, "camera_source"));
        }

        return new TrackPoolDependencyGraph(usages);
    }

    /// 引用某轨道的全部绑定位置；未绑定时为空。
    public Set<Usage> usages(TrackId trackId) {
        return usages.getOrDefault(trackId, Set.of());
    }

    /// 使用次数。
    public int useCount(TrackId trackId) {
        return usages(trackId).size();
    }

    /// 是否被任何属性或文档来源绑定。
    public boolean isReferenced(TrackId trackId) {
        return usages.containsKey(trackId);
    }

    /// 被显式引用的轨道集合。
    public Set<TrackId> referencedTracks() {
        return usages.keySet();
    }

    /// 一个绑定位置。
    ///
    /// @param rigId 机位；文档级来源为空
    /// @param path  字段路径
    public record Usage(@Nullable RigId rigId, String path) {
        public Usage {
            Objects.requireNonNull(path, "path");
        }
    }

    private static void numeric(Map<TrackId, Set<Usage>> usages,
                                PropertyBinding<NumericSource> binding,
                                RigId rigId,
                                String path) {
        if (binding instanceof PropertyBinding.Enabled<NumericSource> enabled
                && enabled.source() instanceof NumericSource.Track track) {
            add(usages, track.trackId(), new Usage(rigId, path));
        }
    }

    private static void bool(Map<TrackId, Set<Usage>> usages,
                             PropertyBinding<BooleanSource> binding,
                             RigId rigId,
                             String path) {
        if (binding instanceof PropertyBinding.Enabled<BooleanSource> enabled
                && enabled.source() instanceof BooleanSource.Track track) {
            add(usages, track.trackId(), new Usage(rigId, path));
        }
    }

    private static void rotation(Map<TrackId, Set<Usage>> usages,
                                 PropertyBinding<QuaternionSource> binding,
                                 RigId rigId,
                                 String path) {
        if (!(binding instanceof PropertyBinding.Enabled<QuaternionSource> enabled)) {
            return;
        }

        switch (enabled.source()) {
            case QuaternionSource.Track track -> add(usages, track.trackId(), new Usage(rigId, path));
            case QuaternionSource.EulerComposite euler -> {
                numericSource(usages, euler.yaw(), rigId, path + ".yaw");
                numericSource(usages, euler.pitch(), rigId, path + ".pitch");
                numericSource(usages, euler.roll(), rigId, path + ".roll");
            }
            case QuaternionSource.Constant ignored -> {
            }
        }
    }

    private static void rect(Map<TrackId, Set<Usage>> usages,
                             RectProperty property,
                             RigId rigId,
                             String path) {
        switch (property) {
            case RectProperty.Normalized normalized -> {
                numeric(usages, normalized.x(), rigId, path + ".x");
                numeric(usages, normalized.y(), rigId, path + ".y");
                numeric(usages, normalized.width(), rigId, path + ".width");
                numeric(usages, normalized.height(), rigId, path + ".height");
            }
            case RectProperty.Anchored anchored -> {
                numeric(usages, anchored.x(), rigId, path + ".x");
                numeric(usages, anchored.y(), rigId, path + ".y");
                numeric(usages, anchored.width(), rigId, path + ".width");
                numeric(usages, anchored.height(), rigId, path + ".height");
            }
        }
    }

    private static void numericSource(Map<TrackId, Set<Usage>> usages,
                                      NumericSource source,
                                      RigId rigId,
                                      String path) {
        if (source instanceof NumericSource.Track track) {
            add(usages, track.trackId(), new Usage(rigId, path));
        }
    }

    private static void add(Map<TrackId, Set<Usage>> usages, TrackId trackId, Usage usage) {
        usages.computeIfAbsent(trackId, ignored -> new LinkedHashSet<>()).add(usage);
    }
}
