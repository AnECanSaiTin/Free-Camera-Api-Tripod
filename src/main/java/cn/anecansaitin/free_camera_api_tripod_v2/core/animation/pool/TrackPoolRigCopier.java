package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 机位复制：默认共享引用轨道（主设计 §1.3.7、D36）。
///
/// 复制出的机位获得新 RigId/名称与独立静态配置，但属性绑定原样保留，因此与源机位共享同一批
/// 轨道；不会为复制自动创建深拷贝。需要深拷贝引用轨道的交互由后续工具显式发起。
@NullMarked
public final class TrackPoolRigCopier {

    /// 共享轨道复制一台机位。
    public TrackPoolAnimation copyShared(TrackPoolAnimation document,
                                         RigId sourceId,
                                         RigId newId,
                                         String newName) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(newId, "newId");
        Objects.requireNonNull(newName, "newName");

        if (newName.isBlank()) {
            throw new IllegalArgumentException("Copied rig name must not be blank");
        }

        PooledRig source = document.rig(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Rig does not exist: " + sourceId));

        if (document.rig(newId).isPresent()) {
            throw new IllegalArgumentException("Rig id already exists: " + newId);
        }

        PooledRig copy = new PooledRig(newId, newName, source.path(), source.motionMode(),
                source.distanceMode(), source.space(), source.output(), source.properties());
        List<PooledRig> rigs = new ArrayList<>(document.rigs());
        rigs.add(copy);
        TrackPoolAnimation candidate = document.withRigs(rigs);

        if (!new TrackPoolValidator().validate(candidate,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision.INITIAL)
                .isEmpty()) {
            throw new IllegalArgumentException("Copied rig failed track-pool validation");
        }

        return candidate;
    }
}
