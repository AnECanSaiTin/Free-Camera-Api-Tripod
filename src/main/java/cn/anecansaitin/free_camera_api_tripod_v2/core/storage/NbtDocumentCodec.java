package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.DocumentCodec;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.DocumentDecodeResult;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.NullMarked;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/// 单份文档的 NBT 载荷编解码（主设计 §8.1、§8.2）。
///
/// 本类只做 `DocumentCodec ↔ CompoundTag ↔ byte[]` 的适配与规模检查：
///
/// - 编码先经 [DocumentCodec]（含 unknown type / data_version / 叠加限制与统一结构约束），
///   再检查 NBT 深度 / 条目 / 字节上限；超限返回 `DataResult` 错误，不写部分文件。
/// - 解码先检查字节上限，再用 [NbtAccounter] 读取，随后检查深度 / 条目，最后交给
///   [DocumentCodec] 执行结构解码与统一文档校验；失败不返回部分文档。
/// - 载荷是**未命名**的 CompoundTag 字节；命名根 / 压缩由上层资源服务决定。
///
/// 本类不读写文件、不选择资源根；文件 IO 与原子替换仍由资源服务负责。
@NullMarked
public final class NbtDocumentCodec {

    private final DocumentCodec documents;
    private final NbtResourceLimits limits;

    /// @param documents 文档两阶段 Codec；不得读取当前 store
    /// @param limits    单载荷规模上限；不得为 null
    public NbtDocumentCodec(DocumentCodec documents, NbtResourceLimits limits) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /// 编码为 NBT 字节。
    ///
    /// @param document 待编码文档
    /// @return 成功时的未命名 CompoundTag 字节；失败不返回部分结果
    public DataResult<byte[]> encode(CameraAnimation document) {
        Objects.requireNonNull(document, "document");
        DataResult<Tag> encoded = documents.encode(document, NbtOps.INSTANCE);

        if (encoded.isError()) {
            return DataResult.error(() -> errorMessage(encoded));
        }

        Tag tag = encoded.result().orElseThrow();

        if (!(tag instanceof CompoundTag compound)) {
            return DataResult.error(() -> "NBT document root must be a compound tag");
        }

        String violation = NbtTagGuard.check(compound, limits);

        if (violation != null) {
            return DataResult.error(() -> violation);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(compound, output);
        } catch (IOException | RuntimeException exception) {
            return DataResult.error(() -> "Cannot write NBT document: " + messageOf(exception));
        }

        if (bytes.size() > limits.maxBytes()) {
            return DataResult.error(() -> "NBT document exceeds byte limit " + limits.maxBytes()
                    + ": " + bytes.size());
        }

        return DataResult.success(bytes.toByteArray());
    }

    /// 编码为 GZIP 压缩的 NBT 字节。
    ///
    /// 原始 NBT 与压缩结果都受 [NbtResourceLimits#maxBytes()] 约束；压缩失败或超限返回
    /// `DataResult` 错误。载荷仍是未命名 CompoundTag 的 GZIP 字节，不添加命名根。
    ///
    /// @param document 待编码文档
    /// @return 成功时的 GZIP 字节；失败不返回部分结果
    public DataResult<byte[]> encodeCompressed(CameraAnimation document) {
        DataResult<byte[]> encoded = encode(document);

        if (encoded.isError()) {
            return DataResult.error(() -> errorMessage(encoded));
        }

        byte[] raw = encoded.result().orElseThrow();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(Math.max(64, raw.length / 2));

        try (GZIPOutputStream output = new GZIPOutputStream(compressed)) {
            output.write(raw);
        } catch (IOException | RuntimeException exception) {
            return DataResult.error(() -> "Cannot compress NBT document: " + messageOf(exception));
        }

        byte[] bytes = compressed.toByteArray();

        if (bytes.length > limits.maxBytes()) {
            return DataResult.error(() -> "Compressed NBT document exceeds byte limit "
                    + limits.maxBytes() + ": " + bytes.length);
        }

        return DataResult.success(bytes);
    }

    /// 解码 GZIP 压缩的 NBT 文档。
    ///
    /// 压缩输入与解压后的原始 NBT 都必须不超过 [NbtResourceLimits#maxBytes()]；解压过程
    /// 采用计数读取，超限立即停止，不先完整展开再拒绝。
    ///
    /// @param bytes GZIP 压缩的未命名 CompoundTag 字节
    /// @return 两阶段解码结果；失败不返回部分文档
    public DocumentDecodeResult decodeCompressed(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");

        if (bytes.length > limits.maxBytes()) {
            return new DocumentDecodeResult.StructureFailed(
                    "Compressed NBT document exceeds byte limit " + limits.maxBytes()
                            + ": " + bytes.length, null);
        }

        byte[] raw;

        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;

            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > limits.maxBytes()) {
                    return new DocumentDecodeResult.StructureFailed(
                            "Decompressed NBT document exceeds byte limit " + limits.maxBytes(), null);
                }

                output.write(buffer, 0, read);
            }

            raw = output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            return new DocumentDecodeResult.StructureFailed(
                    "Cannot decompress NBT document: " + messageOf(exception), null);
        }

        return decode(raw);
    }

    /// 从 NBT 字节解码并执行统一文档校验。
    ///
    /// @param bytes 未命名 CompoundTag 字节
    /// @return 两阶段解码结果；失败不返回部分文档
    public DocumentDecodeResult decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");

        if (bytes.length > limits.maxBytes()) {
            return new DocumentDecodeResult.StructureFailed(
                    "NBT document exceeds byte limit " + limits.maxBytes()
                            + ": " + bytes.length, null);
        }

        CompoundTag tag;

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            tag = NbtIo.read(input, NbtAccounter.create(limits.maxBytes()));
        } catch (IOException | RuntimeException exception) {
            return new DocumentDecodeResult.StructureFailed(
                    "Cannot read NBT document: " + messageOf(exception), null);
        }

        String violation = NbtTagGuard.check(tag, limits);

        if (violation != null) {
            return new DocumentDecodeResult.StructureFailed(violation, null);
        }

        return withFieldPath(documents.decode(NbtOps.INSTANCE, tag), tag);
    }

    /// 结构失败时用 NBT 输入补一次有证据的字段定位；无法证明时保留空路径。
    private static DocumentDecodeResult withFieldPath(DocumentDecodeResult result,
                                                       CompoundTag tag) {
        if (result instanceof DocumentDecodeResult.StructureFailed failed
                && failed.fieldPath() == null) {
            String path = NbtStructureFieldPath.locate(failed.message(), tag);

            if (path != null) {
                return new DocumentDecodeResult.StructureFailed(failed.message(), path);
            }
        }

        return result;
    }

    private static String errorMessage(DataResult<?> result) {
        return result.error()
                .map(DataResult.Error::message)
                .orElse("NBT document encode failed");
    }

    private static String messageOf(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
