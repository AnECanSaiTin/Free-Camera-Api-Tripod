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
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.QuaternionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 机位深复制：为源机位引用的内建轨道生成新 TrackId/KeyId，并重写属性绑定。
///
/// 默认共享复制见 [TrackPoolRigCopier]。扩展轨道没有通用深复制能力，遇到时明确拒绝，不
/// 伪造复制结果。新轨道追加到全局池末尾，源轨道保持不变。
@NullMarked
public final class TrackPoolRigDeepCopier {

    public TrackPoolAnimation copyDeep(TrackPoolAnimation document,
                                       RigId sourceId,
                                       RigId newId,
                                       String newName,
                                       IdGenerator ids) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(newId, "newId");
        Objects.requireNonNull(newName, "newName");
        Objects.requireNonNull(ids, "ids");

        if (newName.isBlank()) {
            throw new IllegalArgumentException("Copied rig name must not be blank");
        }

        PooledRig source = document.rig(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Rig does not exist: " + sourceId));

        if (document.rig(newId).isPresent()) {
            throw new IllegalArgumentException("Rig id already exists: " + newId);
        }

        Map<TrackId, TrackId> copiedIds = new LinkedHashMap<>();
        List<AnimationTrack> newTracks = new ArrayList<>(document.tracks());

        for (TrackId referenced : collectReferencedTrackIds(source.properties())) {
            if (copiedIds.containsKey(referenced)) {
                continue;
            }

            AnimationTrack original = document.track(referenced).orElseThrow(() ->
                    new IllegalArgumentException("Referenced track does not exist: " + referenced));
            TrackId newTrackId = new TrackId(ids.nextId(TrackId.class));
            copiedIds.put(referenced, newTrackId);
            newTracks.add(copyTrack(original, newTrackId, ids));
        }

        RigProperties properties = rewrite(source.properties(), copiedIds);
        PooledRig copy = new PooledRig(newId, newName, source.path(), source.motionMode(),
                source.distanceMode(), source.space(), source.output(), properties);
        TrackPoolAnimation candidate = document.withTracks(newTracks)
                .withRigs(append(document.rigs(), copy));

        if (!new TrackPoolValidator().validate(candidate,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision.INITIAL)
                .isEmpty()) {
            throw new IllegalArgumentException("Deep-copied rig failed track-pool validation");
        }

        return candidate;
    }

    private static List<PooledRig> append(List<PooledRig> rigs, PooledRig rig) {
        List<PooledRig> copy = new ArrayList<>(rigs);
        copy.add(rig);
        return copy;
    }

    private static AnimationTrack copyTrack(AnimationTrack track, TrackId newId, IdGenerator ids) {
        return switch (track) {
            case CurveTrack curve -> new CurveTrack(newId, curve.name(), null,
                    copyCurve(curve.curve(), ids));
            case QuaternionTrack quaternion -> new QuaternionTrack(newId, quaternion.name(),
                    quaternion.defaultValue(), copyKeys(quaternion.keys(), ids));
            case ActiveTrack active -> new ActiveTrack(newId, active.name(),
                    active.defaultValue(), copyBooleanKeys(active.keys(), ids));
            case CameraSourceTrack source -> new CameraSourceTrack(newId, source.name(),
                    source.defaultValue(), copySourceKeys(source.keys(), ids));
            case cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack ignored ->
                    throw new IllegalArgumentException("AimTrack is not part of the global track pool");
            case AnimationTrack.ExtensionTrack extension -> throw new IllegalArgumentException(
                    "Extension track cannot be deep-copied generically: " + extension.type());
        };
    }

    private static Curve copyCurve(Curve curve, IdGenerator ids) {
        List<Keyframe> keys = new ArrayList<>();

        for (Keyframe key : curve.keys()) {
            keys.add(key.withId(new KeyId(ids.nextId(KeyId.class))));
        }

        return new Curve(keys, curve.preMode(), curve.postMode());
    }

    private static List<ValueKeyframe<Quaternion>> copyKeys(
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

    private static List<TrackId> collectReferencedTrackIds(RigProperties properties) {
        List<TrackId> ids = new ArrayList<>();
        collectNumeric(properties.position().x(), ids);
        collectNumeric(properties.position().y(), ids);
        collectNumeric(properties.position().z(), ids);
        collectQuaternion(properties.rotation().source(), ids);
        collectNumeric(properties.fov().source(), ids);
        collectBoolean(properties.active().source(), ids);
        collect(properties.rect(), ids);
        collectBoolean(properties.obstacle().enabled(), ids);
        collectNumeric(properties.obstacle().margin(), ids);
        return ids;
    }

    private static void collectNumeric(PropertyBinding<NumericSource> binding, List<TrackId> ids) {
        if (binding instanceof PropertyBinding.Enabled<NumericSource> enabled
                && enabled.source() instanceof NumericSource.Track track) {
            ids.add(track.trackId());
        }
    }

    private static void collectBoolean(PropertyBinding<BooleanSource> binding, List<TrackId> ids) {
        if (binding instanceof PropertyBinding.Enabled<BooleanSource> enabled
                && enabled.source() instanceof BooleanSource.Track track) {
            ids.add(track.trackId());
        }
    }

    private static void collectQuaternion(PropertyBinding<QuaternionSource> binding, List<TrackId> ids) {
        if (binding instanceof PropertyBinding.Enabled<QuaternionSource> enabled) {
            switch (enabled.source()) {
                case QuaternionSource.Track track -> ids.add(track.trackId());
                case QuaternionSource.EulerComposite euler -> {
                    collectNumericSource(euler.yaw(), ids);
                    collectNumericSource(euler.pitch(), ids);
                    collectNumericSource(euler.roll(), ids);
                }
                case QuaternionSource.Constant ignored -> {
                }
            }
        }
    }

    private static void collectNumericSource(NumericSource source, List<TrackId> ids) {
        if (source instanceof NumericSource.Track track) {
            ids.add(track.trackId());
        }
    }

    private static void collect(RectProperty rect, List<TrackId> ids) {
        switch (rect) {
            case RectProperty.Normalized normalized -> {
                collectNumeric(normalized.x(), ids);
                collectNumeric(normalized.y(), ids);
                collectNumeric(normalized.width(), ids);
                collectNumeric(normalized.height(), ids);
            }
            case RectProperty.Anchored anchored -> {
                collectNumeric(anchored.x(), ids);
                collectNumeric(anchored.y(), ids);
                collectNumeric(anchored.width(), ids);
                collectNumeric(anchored.height(), ids);
            }
        }
    }

    private static RigProperties rewrite(RigProperties properties, Map<TrackId, TrackId> ids) {
        return new RigProperties(
                new PositionProperty(
                        rewriteNumeric(properties.position().x(), ids),
                        rewriteNumeric(properties.position().y(), ids),
                        rewriteNumeric(properties.position().z(), ids)),
                new RotationProperty(rewriteQuaternion(properties.rotation().source(), ids)),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty(
                        rewriteNumeric(properties.fov().source(), ids)),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty(
                        rewriteBoolean(properties.active().source(), ids)),
                rewrite(properties.rect(), ids),
                new ObstacleProperty(
                        rewriteBoolean(properties.obstacle().enabled(), ids),
                        rewriteNumeric(properties.obstacle().margin(), ids)));
    }

    private static PropertyBinding<NumericSource> rewriteNumeric(PropertyBinding<NumericSource> binding,
                                                          Map<TrackId, TrackId> ids) {
        if (binding instanceof PropertyBinding.Enabled<NumericSource> enabled
                && enabled.source() instanceof NumericSource.Track track) {
            TrackId newId = ids.get(track.trackId());
            return newId == null ? binding : PropertyBinding.enabled(new NumericSource.Track(newId));
        }

        return binding;
    }

    private static PropertyBinding<BooleanSource> rewriteBoolean(PropertyBinding<BooleanSource> binding,
                                                          Map<TrackId, TrackId> ids) {
        if (binding instanceof PropertyBinding.Enabled<BooleanSource> enabled
                && enabled.source() instanceof BooleanSource.Track track) {
            TrackId newId = ids.get(track.trackId());
            return newId == null ? binding : PropertyBinding.enabled(new BooleanSource.Track(newId));
        }

        return binding;
    }

    private static PropertyBinding<QuaternionSource> rewriteQuaternion(PropertyBinding<QuaternionSource> binding,
                                                             Map<TrackId, TrackId> ids) {
        if (!(binding instanceof PropertyBinding.Enabled<QuaternionSource> enabled)) {
            return binding;
        }

        return switch (enabled.source()) {
            case QuaternionSource.Constant ignored -> binding;
            case QuaternionSource.Track track -> {
                TrackId newId = ids.get(track.trackId());
                yield newId == null ? binding : PropertyBinding.enabled(new QuaternionSource.Track(newId));
            }
            case QuaternionSource.EulerComposite euler -> PropertyBinding.enabled(
                    new QuaternionSource.EulerComposite(
                            rewriteNumericSource(euler.yaw(), ids), rewriteNumericSource(euler.pitch(), ids),
                            rewriteNumericSource(euler.roll(), ids), euler.order(), euler.unit()));
        };
    }

    private static RectProperty rewrite(RectProperty rect, Map<TrackId, TrackId> ids) {
        return switch (rect) {
            case RectProperty.Normalized normalized -> new RectProperty.Normalized(
                    rewriteNumeric(normalized.x(), ids), rewriteNumeric(normalized.y(), ids),
                    rewriteNumeric(normalized.width(), ids), rewriteNumeric(normalized.height(), ids));
            case RectProperty.Anchored anchored -> new RectProperty.Anchored(anchored.corner(),
                    rewriteNumeric(anchored.x(), ids), rewriteNumeric(anchored.y(), ids),
                    rewriteNumeric(anchored.width(), ids), rewriteNumeric(anchored.height(), ids));
        };
    }

    private static NumericSource rewriteNumericSource(NumericSource source, Map<TrackId, TrackId> ids) {
        if (source instanceof NumericSource.Track track) {
            TrackId newId = ids.get(track.trackId());
            return newId == null ? source : new NumericSource.Track(newId);
        }

        return source;
    }
}
