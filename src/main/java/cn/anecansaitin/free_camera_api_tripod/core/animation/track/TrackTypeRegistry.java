package cn.anecansaitin.free_camera_api_tripod.core.animation.track;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.TrackType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/// 轨道类型注册表。
///
/// 本期只注册曲线轨道；未来的事件触发器轨道、后处理特效轨道通过
/// {@link #register(TrackType)} 接入，编辑器会自动在“新建轨道”中列出。
public final class TrackTypeRegistry {
    /// 曲线轨道：由 float 关键帧构成，是本期的唯一实现
    public static final Identifier CURVE = Identifier.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "curve");

    private static final Map<Identifier, TrackType> TYPES = new LinkedHashMap<>();

    static {
        register(new TrackType(
                CURVE,
                Component.translatable("free_camera_api_tripod.track_type.curve"),
                0xFF6BCBFF,
                null
        ));
    }

    private TrackTypeRegistry() {
    }

    public static void register(TrackType type) {
        TYPES.put(type.id(), type);
    }

    public static @Nullable TrackType get(Identifier id) {
        return TYPES.get(id);
    }

    public static Collection<TrackType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public static MutableComponent label(Identifier id) {
        TrackType type = TYPES.get(id);
        return type != null ? type.label().copy() : Component.literal(id.toString());
    }

    public static int color(Identifier id) {
        TrackType type = TYPES.get(id);
        return type != null ? type.color() : 0xFFFFFFFF;
    }
}
