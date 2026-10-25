package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackAction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackLimits;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackProtocol;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackProtocolCodec;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import org.jspecify.annotations.NullMarked;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// 第一版网络播放动作的明文编解码（主设计 §8.3、P1 §3.5 / §5“存储与网络”）。
///
/// 本类只实现**请求负载的字节形状**：不注册网络包、不做传输、不校验完整文档，
/// 也不读取当前 store、客户端世界或解析器。锚点按类型与完整参数传输，
/// 不传选择器字符串；具体锚点解析仍是接收端资源/能力服务的职责。
///
/// ## 字节布局（协议版本 1）
///
/// 整数默认大端；`VarInt` 为 Minecraft 网络 VarInt；`UTF` 为 VarInt 字节长度加 UTF-8；
/// `bool` 为 1 字节。没有压缩、没有隐藏的默认值。`UTF` 沿用 Minecraft 的 32767 字符上限，
/// 超过该上限的字符串在编解码边界报错。
///
/// | 顺序 | 字段 | 编码 |
/// | --- | --- | --- |
/// | 1 | 协议版本 | `int`，必须等于 [PlaybackProtocol#CURRENT_VERSION] |
/// | 2 | 动作 tag | `byte`：`Stop=0`、`Start=1` |
/// | 3 | 来源 tag（仅 Start） | `byte`：`Embedded=0`、`Local=1` |
/// | 4a | 随包文档（Embedded） | `VarInt` 字节数 + 该长度的 NBT 文档字节 |
/// | 4b | 资源标识（Local） | `UTF`（[ResourceId#value]） |
/// | 5 | 速度 | `float`，有限且 `> 0` |
/// | 6 | 循环 | `bool` |
/// | 7 | 开始时间 | `float`，有限且 `>= 0` |
/// | 8 | 覆盖条目数 | `VarInt`，不得超过 [PlaybackLimits#maxRigOverrides] |
/// | 9 | 每条覆盖 | 见下 |
/// | 10 | 总长度 | 整个动作不得超过 [PlaybackLimits#maxTotalBytes] |
///
/// 每条覆盖的固定头按下列顺序写出；条件载荷紧随其后：
///
/// | 顺序 | 字段 | 编码 |
/// | --- | --- | --- |
/// | 9.1 | 机位 ID | `UTF`（[RigId#value]） |
/// | 9.2 | 是否有锚点 | `bool` |
/// | 9.3 | 是否有跟随掩码 | `bool` |
/// | 9.4 | 注视三态 | `byte`：`Inherit=0`、`Disabled=1`、`Replace=2` |
/// | 9.5 | 是否有避障 | `bool` |
/// | 9.6 | 锚点（若有） | `Identifier UTF` + 该锚点类型的 [AnchorType#streamCodec] 完整参数 |
/// | 9.7 | 跟随掩码（若有） | 3 个 `bool`（x / y / z） |
/// | 9.8 | 注视目标（仅 Replace） | 锚点 + 3 个 `bool` 掩码 + 3 个 `double` 偏移 |
/// | 9.9 | 避障（若有） | `bool enabled` + `double margin` |
///
/// 覆盖 map 的迭代顺序不参与语义；解码保留线上顺序。重复机位 ID 被拒绝，
/// 不会用后出现的条目静默覆盖前一条。
///
/// ## 随包文档
///
/// Embedded 文档先用 [CameraAnimation#codec(DocumentRegistries)] 在 [NbtOps] 下编码成
/// 无压缩的 NBT 字节，再前置 `VarInt` 字节数。文档字节超过
/// [PlaybackLimits#maxDocumentBytes] 时失败；解码在分配字节数组与构造 NBT 读取器之前
/// 同时检查长度上限和 `readableBytes()`，并使用字节数比例受限的 [NbtAccounter]。
///
/// ## 失败
///
/// [encode] / [decode] 把版本不符、未知分支、未知锚点类型、缺失 Codec、超限、截断与
/// 第三方 Codec 异常全部转成 [DataResult] 错误，不向外抛异常，也不返回部分动作。
/// 编码失败时缓冲区内容不保证可回滚，调用方应丢弃；成功只返回 [Unit#INSTANCE]。
///
/// ## 线程
///
/// 构造后只持有冻结的 [DocumentRegistries] 与不可变文档 Codec，可在网络线程复用。
@NullMarked
public final class FlatPlaybackProtocolCodec implements PlaybackProtocolCodec {

    /// 停止动作 tag。
    private static final byte ACTION_STOP = 0;

    /// 开始动作 tag。
    private static final byte ACTION_START = 1;

    /// 随包文档来源 tag。
    private static final byte SOURCE_EMBEDDED = 0;

    /// 本地资源来源 tag。
    private static final byte SOURCE_LOCAL = 1;

    /// 注视继承 tag。
    private static final byte AIM_INHERIT = 0;

    /// 注视显式禁用 tag。
    private static final byte AIM_DISABLED = 1;

    /// 注视替换 tag。
    private static final byte AIM_REPLACE = 2;

    /// 与 `FriendlyByteBuf#writeUtf` 默认值一致的字符串字符上限。
    private static final int MAX_STRING_CHARS = 32767;

    /// NBT 读取器的字节放大保护倍数：原始文档字节已受限，这里只吸收对象开销。
    ///
    /// NBT 记账把每个 compound 条目按名称、对象开销等额外计数，布尔等小字段的
    /// 记账量可达原始字节的 10 倍以上；倍数取得过小会把合法文档误判为过大。
    private static final long NBT_ACCOUNTING_FACTOR = 16L;

    private final DocumentRegistries registries;
    private final Codec<CameraAnimation> documentCodec;

    /// 建立编解码器。
    ///
    /// @param registries 已冻结的文档注册表；用于随包文档编解码与锚点类型分派
    public FlatPlaybackProtocolCodec(DocumentRegistries registries) {
        this.registries = Objects.requireNonNull(registries, "registries");
        this.documentCodec = CameraAnimation.codec(registries);
    }

    @Override
    public DataResult<Unit> encode(RegistryFriendlyByteBuf buffer, PlaybackAction action, PlaybackLimits limits) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(limits, "limits");

        int startIndex = buffer.writerIndex();

        try {
            writeAction(buffer, action, limits, startIndex);
            checkTotalBytes(buffer, startIndex, limits);
            return DataResult.success(Unit.INSTANCE);
        } catch (ProtocolFailure failure) {
            return DataResult.error(failure::getMessage);
        } catch (IndexOutOfBoundsException truncation) {
            return DataResult.error(() -> "truncated playback protocol buffer while encoding");
        } catch (RuntimeException unexpected) {
            return DataResult.error(() -> "playback protocol encode failed: " + describe(unexpected));
        }
    }

    @Override
    public DataResult<PlaybackAction> decode(RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(limits, "limits");

        try {
            int readableBytes = buffer.readableBytes();

            if (readableBytes > limits.maxTotalBytes()) {
                throw violation("playback request is " + readableBytes
                        + " bytes, exceeds maxTotalBytes " + limits.maxTotalBytes());
            }

            int version = buffer.readInt();

            if (version != PlaybackProtocol.CURRENT_VERSION) {
                throw violation("unsupported playback protocol version " + version
                        + ", expected " + PlaybackProtocol.CURRENT_VERSION);
            }

            byte actionTag = buffer.readByte();

            return switch (actionTag) {
                case ACTION_STOP -> DataResult.success(new PlaybackAction.Stop());
                case ACTION_START -> DataResult.success(readStart(buffer, limits));
                default -> throw violation("unknown playback action tag: " + actionTag);
            };
        } catch (ProtocolFailure failure) {
            return DataResult.error(failure::getMessage);
        } catch (IndexOutOfBoundsException truncation) {
            return DataResult.error(() -> "truncated playback protocol buffer");
        } catch (RuntimeException unexpected) {
            return DataResult.error(() -> "playback protocol decode failed: " + describe(unexpected));
        }
    }

    // region 写

    private void writeAction(RegistryFriendlyByteBuf buffer,
                             PlaybackAction action,
                             PlaybackLimits limits,
                             int startIndex) {
        buffer.writeInt(PlaybackProtocol.CURRENT_VERSION);

        switch (action) {
            case PlaybackAction.Stop ignored -> {
                buffer.writeByte(ACTION_STOP);
                checkTotalBytes(buffer, startIndex, limits);
            }
            case PlaybackAction.Start start -> {
                buffer.writeByte(ACTION_START);
                writeStart(buffer, start, limits, startIndex);
            }
        }
    }

    private void writeStart(RegistryFriendlyByteBuf buffer,
                            PlaybackAction.Start start,
                            PlaybackLimits limits,
                            int startIndex) {
        writeSource(buffer, start.source(), limits, startIndex);

        buffer.writeFloat(start.speed());
        buffer.writeBoolean(start.loop());
        buffer.writeFloat(start.startTime());
        checkTotalBytes(buffer, startIndex, limits);

        int overrideCount = start.rigOverrides().size();

        if (overrideCount > limits.maxRigOverrides()) {
            throw violation("rig override count " + overrideCount
                    + " exceeds maxRigOverrides " + limits.maxRigOverrides());
        }

        buffer.writeVarInt(overrideCount);
        checkTotalBytes(buffer, startIndex, limits);

        for (Map.Entry<RigId, RigPlaybackOverrides> entry : start.rigOverrides().entrySet()) {
            writeRigOverride(buffer, entry.getKey(), entry.getValue(), limits, startIndex);
            checkTotalBytes(buffer, startIndex, limits);
        }
    }

    private void writeSource(RegistryFriendlyByteBuf buffer,
                             PlaybackSource source,
                             PlaybackLimits limits,
                             int startIndex) {
        if (source instanceof PlaybackSource.Embedded embedded) {
            buffer.writeByte(SOURCE_EMBEDDED);

            byte[] documentBytes = encodeDocument(embedded.animation(), limits);
            buffer.writeVarInt(documentBytes.length);
            buffer.writeBytes(documentBytes);
        } else if (source instanceof PlaybackSource.Local local) {
            buffer.writeByte(SOURCE_LOCAL);
            buffer.writeUtf(local.resourceId().value());
        } else {
            throw violation("unknown playback source: " + source.getClass().getName());
        }

        checkTotalBytes(buffer, startIndex, limits);
    }

    private byte[] encodeDocument(CameraAnimation animation, PlaybackLimits limits) {
        DataResult<Tag> encoded;

        try {
            encoded = documentCodec.encodeStart(NbtOps.INSTANCE, animation);
        } catch (RuntimeException unexpected) {
            throw violation("embedded document encode failed: " + describe(unexpected));
        }

        if (encoded.result().isEmpty()) {
            throw violation("embedded document encode failed: " + errorMessage(encoded));
        }

        Tag tag = encoded.result().orElseThrow();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try {
            NbtIo.writeAnyTag(tag, new DataOutputStream(bytes));
        } catch (IOException failure) {
            throw violation("embedded document NBT encode failed: " + describe(failure));
        }

        byte[] documentBytes = bytes.toByteArray();

        if (documentBytes.length > limits.maxDocumentBytes()) {
            throw violation("embedded document is " + documentBytes.length
                    + " bytes, exceeds maxDocumentBytes " + limits.maxDocumentBytes());
        }

        return documentBytes;
    }

    private void writeRigOverride(RegistryFriendlyByteBuf buffer,
                                  RigId rigId,
                                  RigPlaybackOverrides overrides,
                                  PlaybackLimits limits,
                                  int startIndex) {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(overrides, "overrides");

        Anchor anchor = overrides.anchor();
        RotationMask followMask = overrides.followMask();
        AimOverride aim = overrides.aim();
        ObstacleSettings obstacle = overrides.obstacle();

        byte aimTag = switch (aim) {
            case AimOverride.Inherit ignored -> AIM_INHERIT;
            case AimOverride.Disabled ignored -> AIM_DISABLED;
            case AimOverride.Replace ignored -> AIM_REPLACE;
        };

        buffer.writeUtf(rigId.value());
        buffer.writeBoolean(anchor != null);
        buffer.writeBoolean(followMask != null);
        buffer.writeByte(aimTag);
        buffer.writeBoolean(obstacle != null);

        if (anchor != null) {
            writeAnchor(buffer, anchor, limits, startIndex);
        }

        if (followMask != null) {
            writeMask(buffer, followMask);
        }

        if (aim instanceof AimOverride.Replace replace) {
            writeAimTarget(buffer, replace.target(), limits, startIndex);
        }

        if (obstacle != null) {
            if (!Double.isFinite(obstacle.margin()) || obstacle.margin() < 0) {
                throw violation("obstacle margin must be finite and non-negative: " + obstacle.margin());
            }

            buffer.writeBoolean(obstacle.enabled());
            buffer.writeDouble(obstacle.margin());
        }
    }

    private void writeAimTarget(RegistryFriendlyByteBuf buffer,
                                AimTarget target,
                                PlaybackLimits limits,
                                int startIndex) {
        if (target.target() == null || target.mask() == null || target.offset() == null) {
            throw violation("AimOverride.Replace target must be complete");
        }

        writeAnchor(buffer, target.target(), limits, startIndex);
        writeMask(buffer, target.mask());

        Vec3 offset = target.offset();

        if (!Double.isFinite(offset.x()) || !Double.isFinite(offset.y()) || !Double.isFinite(offset.z())) {
            throw violation("aim target offset must be finite: " + offset);
        }

        buffer.writeDouble(offset.x());
        buffer.writeDouble(offset.y());
        buffer.writeDouble(offset.z());
    }

    private void writeAnchor(RegistryFriendlyByteBuf buffer,
                             Anchor anchor,
                             PlaybackLimits limits,
                             int startIndex) {
        Identifier typeId = anchor.type();

        if (typeId == null) {
            throw violation("anchor type id must not be null");
        }

        AnchorType<?> anchorType = registries.anchors().find(typeId)
                .orElseThrow(() -> violation("unknown anchor type: " + typeId));

        StreamCodec<? super RegistryFriendlyByteBuf, Anchor> streamCodec;

        try {
            streamCodec = anchorStreamCodec(anchorType);
        } catch (RuntimeException unexpected) {
            throw violation("failed to obtain stream codec for anchor type " + typeId
                    + ": " + describe(unexpected));
        }

        if (streamCodec == null) {
            throw violation("anchor type " + typeId + " has no stream codec");
        }

        buffer.writeIdentifier(typeId);

        try {
            streamCodec.encode(buffer, anchor);
        } catch (RuntimeException unexpected) {
            throw violation("failed to encode anchor " + typeId + ": " + describe(unexpected));
        }

        checkTotalBytes(buffer, startIndex, limits);
    }

    private static void writeMask(RegistryFriendlyByteBuf buffer, RotationMask mask) {
        buffer.writeBoolean(mask.x());
        buffer.writeBoolean(mask.y());
        buffer.writeBoolean(mask.z());
    }

    // endregion

    // region 读

    private PlaybackAction.Start readStart(RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
        PlaybackSource source = readSource(buffer, limits);

        float speed = readSpeed(buffer);
        boolean loop = buffer.readBoolean();
        float startTime = readStartTime(buffer);

        int overrideCount = buffer.readVarInt();

        if (overrideCount < 0) {
            throw violation("rig override count must be non-negative: " + overrideCount);
        }

        if (overrideCount > limits.maxRigOverrides()) {
            throw violation("rig override count " + overrideCount
                    + " exceeds maxRigOverrides " + limits.maxRigOverrides());
        }

        if (overrideCount > buffer.readableBytes()) {
            throw violation("truncated playback protocol buffer: override count " + overrideCount
                    + " exceeds remaining bytes " + buffer.readableBytes());
        }

        Map<RigId, RigPlaybackOverrides> overrides = new LinkedHashMap<>();

        for (int index = 0; index < overrideCount; index++) {
            RigId rigId = readRigId(buffer, limits);
            RigPlaybackOverrides value = readRigOverride(buffer, limits);

            if (overrides.putIfAbsent(rigId, value) != null) {
                throw violation("duplicate rig override: " + rigId.value());
            }
        }

        return new PlaybackAction.Start(source, overrides, speed, loop, startTime);
    }

    private PlaybackSource readSource(RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
        byte sourceTag = buffer.readByte();

        return switch (sourceTag) {
            case SOURCE_EMBEDDED -> new PlaybackSource.Embedded(readDocument(buffer, limits));
            case SOURCE_LOCAL -> new PlaybackSource.Local(readResourceId(buffer, limits));
            default -> throw violation("unknown playback source tag: " + sourceTag);
        };
    }

    private CameraAnimation readDocument(RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
        int documentLength = buffer.readVarInt();

        if (documentLength <= 0) {
            throw violation("embedded document length must be positive: " + documentLength);
        }

        if (documentLength > limits.maxDocumentBytes()) {
            throw violation("embedded document is " + documentLength
                    + " bytes, exceeds maxDocumentBytes " + limits.maxDocumentBytes());
        }

        if (documentLength > buffer.readableBytes()) {
            throw violation("truncated playback protocol buffer: embedded document needs "
                    + documentLength + " bytes but only " + buffer.readableBytes() + " remain");
        }

        byte[] documentBytes = new byte[documentLength];
        buffer.readBytes(documentBytes);
        return decodeDocument(documentBytes);
    }

    private CameraAnimation decodeDocument(byte[] documentBytes) {
        Tag tag;

        try {
            // 原始文档字节已经受限；至少使用 MC 默认的 NBT 配额，避免小文档被对象开销误伤。
            long quota = Math.max(NbtAccounter.DEFAULT_NBT_QUOTA,
                    (long) documentBytes.length * NBT_ACCOUNTING_FACTOR);
            tag = NbtIo.readAnyTag(new DataInputStream(new ByteArrayInputStream(documentBytes)),
                    NbtAccounter.create(quota));
        } catch (IOException | RuntimeException failure) {
            throw violation("embedded document NBT decode failed: " + describe(failure));
        }

        DataResult<CameraAnimation> decoded;

        try {
            decoded = documentCodec.parse(NbtOps.INSTANCE, tag);
        } catch (RuntimeException failure) {
            throw violation("embedded document decode failed: " + describe(failure));
        }

        if (decoded.result().isEmpty()) {
            throw violation("embedded document decode failed: " + errorMessage(decoded));
        }

        return decoded.result().orElseThrow();
    }

    private RigPlaybackOverrides readRigOverride(RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
        boolean hasAnchor = buffer.readBoolean();
        boolean hasFollowMask = buffer.readBoolean();
        byte aimTag = buffer.readByte();
        boolean hasObstacle = buffer.readBoolean();

        if (aimTag != AIM_INHERIT && aimTag != AIM_DISABLED && aimTag != AIM_REPLACE) {
            throw violation("unknown aim override tag: " + aimTag);
        }

        Anchor anchor = hasAnchor ? readAnchor(buffer, limits) : null;
        RotationMask followMask = hasFollowMask ? readMask(buffer) : null;

        AimOverride aim = switch (aimTag) {
            case AIM_INHERIT -> AimOverride.inherit();
            case AIM_DISABLED -> AimOverride.disabled();
            case AIM_REPLACE -> AimOverride.replace(readAimTarget(buffer, limits));
            default -> throw violation("unknown aim override tag: " + aimTag);
        };

        ObstacleSettings obstacle = hasObstacle ? readObstacle(buffer) : null;

        return new RigPlaybackOverrides(anchor, followMask, aim, obstacle);
    }

    private Anchor readAnchor(RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
        Identifier typeId;

        try {
            typeId = buffer.readIdentifier();
        } catch (RuntimeException failure) {
            throw violation("invalid anchor type id: " + describe(failure));
        }

        AnchorType<?> anchorType = registries.anchors().find(typeId)
                .orElseThrow(() -> violation("unknown anchor type: " + typeId));

        StreamCodec<? super RegistryFriendlyByteBuf, Anchor> streamCodec;

        try {
            streamCodec = anchorStreamCodec(anchorType);
        } catch (RuntimeException unexpected) {
            throw violation("failed to obtain stream codec for anchor type " + typeId
                    + ": " + describe(unexpected));
        }

        if (streamCodec == null) {
            throw violation("anchor type " + typeId + " has no stream codec");
        }

        Anchor anchor;

        try {
            anchor = streamCodec.decode(buffer);
        } catch (RuntimeException unexpected) {
            throw violation("failed to decode anchor " + typeId + ": " + describe(unexpected));
        }

        if (anchor == null) {
            throw violation("anchor stream codec returned null for type " + typeId);
        }

        if (!typeId.equals(anchor.type())) {
            throw violation("anchor stream codec for " + typeId
                    + " returned mismatched type " + anchor.type());
        }

        return anchor;
    }

    private AimTarget readAimTarget(RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
        Anchor anchor = readAnchor(buffer, limits);
        RotationMask mask = readMask(buffer);

        double x = readFiniteDouble(buffer, "aim target offset x");
        double y = readFiniteDouble(buffer, "aim target offset y");
        double z = readFiniteDouble(buffer, "aim target offset z");

        return new AimTarget(anchor, mask, new Vec3(x, y, z));
    }

    private static RotationMask readMask(RegistryFriendlyByteBuf buffer) {
        return new RotationMask(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }

    private static ObstacleSettings readObstacle(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        double margin = buffer.readDouble();

        if (!Double.isFinite(margin) || margin < 0) {
            throw violation("obstacle margin must be finite and non-negative: " + margin);
        }

        return new ObstacleSettings(enabled, margin);
    }

    private static float readSpeed(RegistryFriendlyByteBuf buffer) {
        float speed = buffer.readFloat();

        if (!Float.isFinite(speed) || speed <= 0f) {
            throw violation("playback speed must be finite and positive: " + speed);
        }

        return speed;
    }

    private static float readStartTime(RegistryFriendlyByteBuf buffer) {
        float startTime = buffer.readFloat();

        if (!Float.isFinite(startTime) || startTime < 0f) {
            throw violation("playback start time must be finite and non-negative: " + startTime);
        }

        return startTime;
    }

    private static double readFiniteDouble(RegistryFriendlyByteBuf buffer, String field) {
        double value = buffer.readDouble();

        if (!Double.isFinite(value)) {
            throw violation(field + " must be finite: " + value);
        }

        return value;
    }

    private static RigId readRigId(RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
        String value = buffer.readUtf(stringLimit(limits));

        if (value.isBlank()) {
            throw violation("rig override id must not be blank");
        }

        return new RigId(value);
    }

    private static ResourceId readResourceId(RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
        String value = buffer.readUtf(stringLimit(limits));

        if (value.isBlank()) {
            throw violation("local resource id must not be blank");
        }

        return new ResourceId(value);
    }

    // endregion

    // region 辅助

    /// 不把任意上限直接当成字符串字符数：Minecraft 的 UTF 读写默认上限是 32767。
    private static int stringLimit(PlaybackLimits limits) {
        return Math.min(limits.maxTotalBytes(), MAX_STRING_CHARS);
    }

    private static void checkTotalBytes(RegistryFriendlyByteBuf buffer,
                                        int startIndex,
                                        PlaybackLimits limits) {
        long written = (long) buffer.writerIndex() - startIndex;

        if (written > limits.maxTotalBytes()) {
            throw violation("playback request is " + written
                    + " bytes, exceeds maxTotalBytes " + limits.maxTotalBytes());
        }
    }

    /// 按锚点类型取得网络 Codec。
    ///
    /// 注册表保存的是通配类型；类型 ID 与实际锚点已经由调用方核对，因此这里的
    /// 强制转换只用于恢复“该锚点类型产生该锚点实现”的 Java 泛型关系。
    @SuppressWarnings("unchecked")
    private static StreamCodec<? super RegistryFriendlyByteBuf, Anchor> anchorStreamCodec(
            AnchorType<?> anchorType) {
        return (StreamCodec<? super RegistryFriendlyByteBuf, Anchor>) anchorType.streamCodec();
    }

    private static String errorMessage(DataResult<?> result) {
        return result.error().map(error -> error.message()).orElse("unknown error");
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();

        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }

        return error.getClass().getSimpleName() + ": " + message;
    }

    private static ProtocolFailure violation(String message) {
        return new ProtocolFailure(message);
    }

    /// 编解码过程中的内部失败载体；只在 [encode] / [decode] 边界转成 [DataResult] 错误。
    private static final class ProtocolFailure extends RuntimeException {

        private ProtocolFailure(String message) {
            super(message);
        }
    }

    // endregion
}
