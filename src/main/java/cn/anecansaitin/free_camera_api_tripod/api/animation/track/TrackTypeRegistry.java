package cn.anecansaitin.free_camera_api_tripod.api.animation.track;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/// 轨道类型注册表。
public final class TrackTypeRegistry {
    /// 曲线轨道
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

    @Nullable
    public static TrackType get(Identifier id) {
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
