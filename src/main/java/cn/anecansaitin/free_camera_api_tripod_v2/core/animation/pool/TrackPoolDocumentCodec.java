package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/// 轨道池文档的 JSON / NBT 结构编解码入口。
///
/// 只负责结构往返；旧 `data_version` 由 [TrackPoolAnimation.DATA_VERSION_CODEC] 在解码阶段拒绝。
/// 文件大小、路径、原子替换与目录索引属于服务层，不在这里重复。
public final class TrackPoolDocumentCodec {

    private TrackPoolDocumentCodec() {
    }

    public static DataResult<JsonElement> encodeJson(TrackPoolAnimation document,
                                                     DocumentRegistries registries) {
        Objects.requireNonNull(document, "document");
        return TrackPoolAnimation.codec(registries).encodeStart(JsonOps.INSTANCE, document);
    }

    public static DataResult<TrackPoolAnimation> decodeJson(JsonElement json,
                                                            DocumentRegistries registries) {
        Objects.requireNonNull(json, "json");
        return TrackPoolAnimation.codec(registries).parse(JsonOps.INSTANCE, json);
    }

    public static DataResult<TrackPoolAnimation> decodeJson(String json,
                                                            DocumentRegistries registries) {
        Objects.requireNonNull(json, "json");

        try {
            return decodeJson(JsonParser.parseString(json), registries);
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Malformed track-pool JSON: " + exception.getMessage());
        }
    }

    public static DataResult<byte[]> encodeNbt(TrackPoolAnimation document,
                                               DocumentRegistries registries) {
        Objects.requireNonNull(document, "document");

        DataResult<Tag> encoded = TrackPoolAnimation.codec(registries)
                .encodeStart(NbtOps.INSTANCE, document);

        if (encoded.error().isPresent()) {
            return DataResult.error(() -> encoded.error().orElseThrow().message());
        }

        try {
            if (!(encoded.result().orElseThrow() instanceof net.minecraft.nbt.CompoundTag compound)) {
                return DataResult.error(() -> "Track-pool NBT root must be a compound tag");
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(compound, output);
            }

            return DataResult.success(bytes.toByteArray());
        } catch (IOException exception) {
            return DataResult.error(() -> "Could not encode track-pool NBT: " + exception.getMessage());
        }
    }

    public static DataResult<TrackPoolAnimation> decodeNbt(byte[] bytes,
                                                           DocumentRegistries registries) {
        Objects.requireNonNull(bytes, "bytes");

        try (java.io.DataInputStream input = new java.io.DataInputStream(
                new ByteArrayInputStream(bytes))) {
            Tag tag = NbtIo.read(input, NbtAccounter.unlimitedHeap());
            return TrackPoolAnimation.codec(registries).parse(NbtOps.INSTANCE, tag);
        } catch (IOException | RuntimeException exception) {
            return DataResult.error(() -> "Malformed track-pool NBT: " + exception.getMessage());
        }
    }
}
