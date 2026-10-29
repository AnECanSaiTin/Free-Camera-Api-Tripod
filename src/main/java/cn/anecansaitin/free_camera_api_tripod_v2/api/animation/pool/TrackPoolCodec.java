package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 全局轨道池的轨道分派 Codec。
///
/// 内建类型按 sealed 分支识别；扩展类型落到冻结的 `TrackTypeRegistry`。池中不再有
/// “每机位通道/注视”这种所有者语义，曲线、四元数、布尔/状态与扩展轨道共用同一列表。
@NullMarked
public final class TrackPoolCodec {

    private TrackPoolCodec() {
    }

    /// 按轨道 `type` 分派。
    public static Codec<AnimationTrack> codec(DocumentRegistries registries) {
        Objects.requireNonNull(registries, "registries");

        return Identifier.CODEC.<AnimationTrack>partialDispatch(
                "type",
                track -> DataResult.success(track.type()),
                id -> {
                    if (id.equals(CurveTrack.TYPE)) {
                        return DataResult.success(CurveTrack.MAP_CODEC);
                    }

                    if (id.equals(QuaternionTrack.TYPE)) {
                        return DataResult.success(QuaternionTrack.MAP_CODEC);
                    }

                    if (id.equals(ActiveTrack.TYPE)) {
                        return DataResult.success(ActiveTrack.MAP_CODEC);
                    }

                    if (id.equals(CameraSourceTrack.TYPE)) {
                        return DataResult.success(CameraSourceTrack.MAP_CODEC);
                    }

                    return registries.tracks().find(id)
                            .<DataResult<MapCodec<? extends AnimationTrack>>>map(type ->
                                    DataResult.success((MapCodec<? extends AnimationTrack>) type.codec()))
                            .orElseGet(() -> DataResult.error(() ->
                                    "Unknown or unsupported track type in global pool: " + id));
                });
    }
}
