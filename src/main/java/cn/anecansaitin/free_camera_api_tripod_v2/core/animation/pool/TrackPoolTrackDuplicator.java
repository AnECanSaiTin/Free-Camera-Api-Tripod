package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.IdGenerator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 单条内建轨道的复制：新 TrackId 与全部新 KeyId，源轨道不变。
///
/// 扩展轨道没有通用复制能力，明确拒绝。复制只返回派生轨道，不自动加入文档或建立绑定。
@NullMarked
public final class TrackPoolTrackDuplicator {

    public AnimationTrack duplicate(AnimationTrack track, TrackId newId, IdGenerator ids) {
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(newId, "newId");
        Objects.requireNonNull(ids, "ids");

        return switch (track) {
            case CurveTrack curve -> new CurveTrack(newId, curve.name(), null,
                    copyCurve(curve.curve(), ids));
            case QuaternionTrack quaternion -> new QuaternionTrack(newId, quaternion.name(),
                    quaternion.defaultValue(), copyQuaternionKeys(quaternion.keys(), ids));
            case ActiveTrack active -> new ActiveTrack(newId, active.name(),
                    active.defaultValue(), copyBooleanKeys(active.keys(), ids));
            case CameraSourceTrack source -> new CameraSourceTrack(newId, source.name(),
                    source.defaultValue(), copySourceKeys(source.keys(), ids));
            case AnimationTrack.ExtensionTrack extension -> throw new IllegalArgumentException(
                    "Extension track cannot be duplicated generically: " + extension.type());
            case cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack ignored ->
                    throw new IllegalArgumentException("AimTrack is not part of the global track pool");
        };
    }

    private static Curve copyCurve(Curve curve, IdGenerator ids) {
        List<Keyframe> keys = new ArrayList<>();

        for (Keyframe key : curve.keys()) {
            keys.add(key.withId(new KeyId(ids.nextId(KeyId.class))));
        }

        return new Curve(keys, curve.preMode(), curve.postMode());
    }

    private static List<ValueKeyframe<Quaternion>> copyQuaternionKeys(
            List<ValueKeyframe<Quaternion>> keys, IdGenerator ids) {
        List<ValueKeyframe<Quaternion>> copy = new ArrayList<>();

        for (ValueKeyframe<Quaternion> key : keys) {
            copy.add(key.withId(new KeyId(ids.nextId(KeyId.class))));
        }

        return copy;
    }

    private static List<ValueKeyframe<Boolean>> copyBooleanKeys(
            List<ValueKeyframe<Boolean>> keys, IdGenerator ids) {
        List<ValueKeyframe<Boolean>> copy = new ArrayList<>();

        for (ValueKeyframe<Boolean> key : keys) {
            copy.add(key.withId(new KeyId(ids.nextId(KeyId.class))));
        }

        return copy;
    }

    private static List<ValueKeyframe<CameraSource>> copySourceKeys(
            List<ValueKeyframe<CameraSource>> keys, IdGenerator ids) {
        List<ValueKeyframe<CameraSource>> copy = new ArrayList<>();

        for (ValueKeyframe<CameraSource> key : keys) {
            copy.add(key.withId(new KeyId(ids.nextId(KeyId.class))));
        }

        return copy;
    }
}
