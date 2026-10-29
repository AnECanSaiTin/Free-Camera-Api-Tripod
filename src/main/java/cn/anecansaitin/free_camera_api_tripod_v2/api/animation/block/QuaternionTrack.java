package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 四元数轨道：整体相机旋转的关键帧来源（主设计 §1.3.5、D34）。
///
/// 键值是完整四元数，不保存 Yaw / Pitch / Roll 三轴；键之间使用最短路径 [Quaternion#slerp]。
/// 首键之前使用显式默认值，末键之后保持末键。四元数不记录“绕了几圈”：需要多圈运动时
/// 由工具生成相邻转角小于 180° 的分段键。
///
/// 本轨道当前可作为扩展轨道族中的独立类型使用；全局轨道池与属性绑定的文档根迁移完成后，
/// 它会进入文档级轨道池。
@NullMarked
public record QuaternionTrack(
        TrackId id,
        String name,
        Quaternion defaultValue,
        List<ValueKeyframe<Quaternion>> keys) implements AnimationTrack, RenamableTrack {

    /// 四元数轨道类型标识。
    public static final Identifier TYPE =
            Identifier.fromNamespaceAndPath("free_camera_api_tripod", "quaternion");

    /// 结构编码：`{id, name, default, keys}`。
    public static final MapCodec<QuaternionTrack> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TrackId.CODEC.fieldOf("id").forGetter(QuaternionTrack::id),
            Codecs.nonBlankString().fieldOf("name").forGetter(QuaternionTrack::name),
            Quaternion.CODEC.fieldOf("default").forGetter(QuaternionTrack::defaultValue),
            ValueKeyframe.codec(Quaternion.CODEC).listOf().fieldOf("keys").forGetter(QuaternionTrack::keys)
    ).apply(instance, QuaternionTrack::new));

    /// 独立 Codec。
    public static final Codec<QuaternionTrack> CODEC = MAP_CODEC.codec();

    public QuaternionTrack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(defaultValue, "defaultValue");

        if (name.isBlank()) {
            throw new IllegalArgumentException("QuaternionTrack name must not be blank");
        }

        keys = canonical(keys);
    }

    @Override
    public Identifier type() {
        return TYPE;
    }

    @Override
    public float duration() {
        return keys.isEmpty() ? 0f : keys.getLast().time();
    }

    @Override
    public int keyCount() {
        return keys.size();
    }

    /// 最小弧插值求值。
    ///
    /// @param time 秒；有限
    /// @return 该时刻的四元数
    public Quaternion at(float time) {
        if (!Float.isFinite(time)) {
            throw new IllegalArgumentException("QuaternionTrack time must be finite: " + time);
        }

        if (keys.isEmpty()) {
            return defaultValue;
        }

        if (time < keys.getFirst().time()) {
            return defaultValue;
        }

        int index = 0;

        while (index + 1 < keys.size() && keys.get(index + 1).time() <= time) {
            index++;
        }

        if (index + 1 >= keys.size()) {
            return keys.getLast().value();
        }

        ValueKeyframe<Quaternion> from = keys.get(index);
        ValueKeyframe<Quaternion> to = keys.get(index + 1);
        float span = to.time() - from.time();

        if (span <= 0f) {
            return to.value();
        }

        return from.value().slerp(to.value(), (time - from.time()) / span);
    }

    @Override
    public QuaternionTrack withName(String newName) {
        return new QuaternionTrack(id, newName, defaultValue, keys);
    }

    public QuaternionTrack withDefaultValue(Quaternion value) {
        return new QuaternionTrack(id, name, value, keys);
    }

    public QuaternionTrack withKeys(List<ValueKeyframe<Quaternion>> newKeys) {
        return new QuaternionTrack(id, name, defaultValue, newKeys);
    }

    private static List<ValueKeyframe<Quaternion>> canonical(List<ValueKeyframe<Quaternion>> keys) {
        Objects.requireNonNull(keys, "keys");
        List<ValueKeyframe<Quaternion>> copy = List.copyOf(keys);
        float previous = Float.NaN;

        for (ValueKeyframe<Quaternion> key : copy) {
            if (Float.isFinite(previous) && key.time() <= previous) {
                throw new IllegalArgumentException(
                        "QuaternionTrack key times must be strictly increasing and unique: " + key.time());
            }

            previous = key.time();
        }

        return copy;
    }
}
