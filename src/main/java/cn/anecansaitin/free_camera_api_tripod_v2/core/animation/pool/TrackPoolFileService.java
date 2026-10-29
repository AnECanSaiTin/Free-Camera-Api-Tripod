package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import com.mojang.serialization.DataResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;

/// 轨道池文档文件服务：有界读取、JSON/NBT 识别与原子替换。
///
/// 只处理本地路径与结构编解码；旧 `data_version` 由 Codec 拒绝。目录索引、缓存与网络同步
/// 不属于本类。
public final class TrackPoolFileService {

    /// 单文件读取上限。
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

    /// 读取一个文档。
    public DataResult<TrackPoolAnimation> read(Path path, DocumentRegistries registries) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(registries, "registries");

        try {
            long size = Files.size(path);

            if (size > MAX_FILE_BYTES) {
                return DataResult.error(() -> "Track-pool file exceeds " + MAX_FILE_BYTES + " bytes");
            }

            byte[] bytes = Files.readAllBytes(path);
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

            if (name.endsWith(".nbt.gz")) {
                bytes = decompress(bytes);
            }

            if (name.endsWith(".nbt") || name.endsWith(".nbt.gz")) {
                return TrackPoolDocumentCodec.decodeNbt(bytes, registries);
            }

            return TrackPoolDocumentCodec.decodeJson(
                    new String(bytes, StandardCharsets.UTF_8), registries);
        } catch (IOException exception) {
            return DataResult.error(() -> "Could not read track-pool file: " + exception.getMessage());
        }
    }

    private static byte[] decompress(byte[] compressed) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;

            while ((read = input.read(buffer)) >= 0) {
                total += read;

                if (total > MAX_FILE_BYTES) {
                    throw new IOException("Compressed track-pool file expands beyond " + MAX_FILE_BYTES);
                }

                output.write(buffer, 0, read);
            }

            return output.toByteArray();
        }
    }

    private static byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }

        return output.toByteArray();
    }

    /// 原子写入一个文档；按扩展名选择 JSON/NBT。
    public DataResult<Path> write(Path path,
                                  TrackPoolAnimation document,
                                  DocumentRegistries registries) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registries, "registries");

        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        byte[] bytes;

        if (name.endsWith(".nbt") || name.endsWith(".nbt.gz")) {
            DataResult<byte[]> encoded = TrackPoolDocumentCodec.encodeNbt(document, registries);

            if (encoded.error().isPresent()) {
                return DataResult.error(() -> encoded.error().orElseThrow().message());
            }

            bytes = encoded.result().orElseThrow();

            if (name.endsWith(".nbt.gz")) {
                try {
                    bytes = compress(bytes);
                } catch (IOException exception) {
                    return DataResult.error(() -> "Could not compress track-pool NBT: "
                            + exception.getMessage());
                }
            }
        } else {
            DataResult<com.google.gson.JsonElement> encoded =
                    TrackPoolDocumentCodec.encodeJson(document, registries);

            if (encoded.error().isPresent()) {
                return DataResult.error(() -> encoded.error().orElseThrow().message());
            }

            bytes = encoded.result().orElseThrow().toString().getBytes(StandardCharsets.UTF_8);
        }

        if (bytes.length > MAX_FILE_BYTES) {
            return DataResult.error(() -> "Encoded track-pool document exceeds " + MAX_FILE_BYTES + " bytes");
        }

        try {
            Path parent = path.toAbsolutePath().getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temporary = Files.createTempFile(parent, ".track-pool-", ".tmp");

            try {
                Files.write(temporary, bytes);

                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }

            return DataResult.success(path);
        } catch (IOException exception) {
            return DataResult.error(() -> "Could not write track-pool file: " + exception.getMessage());
        }
    }
}
