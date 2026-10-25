package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
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
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [FlatPlaybackProtocolCodec] 的真实字节往返与拒绝路径。
///
/// 缓冲区使用 `Unpooled.buffer()` + [RegistryAccess#EMPTY] 构造的真实
/// [RegistryFriendlyByteBuf]，不替换成只记录调用的假缓冲区；因此这里同时验证
/// 版本、分支 tag、条件字段顺序、NBT 文档和限额都在实际字节上成立。
class FlatPlaybackProtocolCodecTest {

    private static final Identifier FIXED_ANCHOR_TYPE =
            Identifier.fromNamespaceAndPath("test", "fixed_anchor");

    private static final PlaybackLimits LIMITS = new PlaybackLimits(1 << 20, 16, 1 << 21);

    private static final DocumentRegistries REGISTRIES = registries(true);
    private static final DocumentRegistries REGISTRIES_WITHOUT_ANCHOR = registries(false);

    private static final FlatPlaybackProtocolCodec CODEC = new FlatPlaybackProtocolCodec(REGISTRIES);
    private static final FlatPlaybackProtocolCodec CODEC_WITHOUT_ANCHOR =
            new FlatPlaybackProtocolCodec(REGISTRIES_WITHOUT_ANCHOR);

    // region 测试锚点

    private record FixedAnchor(Vec3 position) implements Anchor {

        @Override
        public Identifier type() {
            return FIXED_ANCHOR_TYPE;
        }
    }

    private static final class FixedAnchorType implements AnchorType<FixedAnchor> {

        private static final StreamCodec<RegistryFriendlyByteBuf, FixedAnchor> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public FixedAnchor decode(RegistryFriendlyByteBuf buffer) {
                        return new FixedAnchor(new Vec3(
                                buffer.readDouble(),
                                buffer.readDouble(),
                                buffer.readDouble()));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, FixedAnchor value) {
                        Vec3 position = value.position();
                        buffer.writeDouble(position.x());
                        buffer.writeDouble(position.y());
                        buffer.writeDouble(position.z());
                    }
                };

        @Override
        public Identifier id() {
            return FIXED_ANCHOR_TYPE;
        }

        @Override
        public MapCodec<FixedAnchor> codec() {
            return Vec3.CODEC.fieldOf("position").xmap(FixedAnchor::new, FixedAnchor::position);
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, FixedAnchor> streamCodec() {
            return STREAM_CODEC;
        }
    }

    private static DocumentRegistries registries(boolean withAnchorType) {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();

        if (withAnchorType) {
            anchors.register(new FixedAnchorType());
        }

        anchors.freeze();

        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        tracks.freeze();

        FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        samples.freeze();

        return new DocumentRegistries(anchors, tracks, samples);
    }

    // endregion

    // region 往返

    @Test
    void stopRoundTripsWithStableVersionAndActionTags() {
        PlaybackAction action = new PlaybackAction.Stop();
        RegistryFriendlyByteBuf buffer = encode(CODEC, action, LIMITS);

        assertEquals(PlaybackProtocol.CURRENT_VERSION, buffer.getInt(0));
        assertEquals(0, buffer.getByte(Integer.BYTES), "Stop tag must stay 0");
        assertEquals(Integer.BYTES + 1, buffer.readableBytes());

        assertEquals(action, decode(CODEC, buffer, LIMITS));
    }

    @Test
    void localStartRoundTripsPlaybackParameters() {
        PlaybackAction.Start action = new PlaybackAction.Start(
                new PlaybackSource.Local(new ResourceId("动画/演示")),
                Map.of(),
                1.75f,
                true,
                3.25f);

        RegistryFriendlyByteBuf buffer = encode(CODEC, action, LIMITS);

        assertEquals(PlaybackProtocol.CURRENT_VERSION, buffer.getInt(0));
        assertEquals(1, buffer.getByte(Integer.BYTES), "Start tag must stay 1");
        assertEquals(1, buffer.getByte(Integer.BYTES + 1), "Local source tag must stay 1");

        PlaybackAction decoded = decode(CODEC, buffer, LIMITS);

        assertEquals(action, decoded);
        assertEquals(1.75f, ((PlaybackAction.Start) decoded).speed());
        assertTrue(((PlaybackAction.Start) decoded).loop());
        assertEquals(3.25f, ((PlaybackAction.Start) decoded).startTime());
    }

    @Test
    void embeddedStartRoundTripsDocumentThroughNbt() {
        CameraAnimation document = document();
        PlaybackAction.Start action = new PlaybackAction.Start(
                new PlaybackSource.Embedded(document),
                Map.of(),
                1f,
                false,
                0f);

        PlaybackAction decoded = decode(CODEC, encode(CODEC, action, LIMITS), LIMITS);

        assertEquals(action, decoded);

        PlaybackSource.Embedded embedded = (PlaybackSource.Embedded)
                ((PlaybackAction.Start) decoded).source();

        assertEquals(document, embedded.animation());
        assertEquals(1, ((PlaybackAction.Start) decoded).toRequest(embedded.animation())
                .animation().rigs().size());
    }

    @Test
    void rigOverridesPreserveThreeStateAndPresenceFlags() {
        FixedAnchor disabledAnchor = new FixedAnchor(new Vec3(4.5, -1.25, 8.0));
        AimTarget replaceTarget = new AimTarget(
                new FixedAnchor(new Vec3(-2.0, 3.0, 4.0)),
                RotationMask.ALL,
                new Vec3(0.5, 1.5, -2.5));

        Map<RigId, RigPlaybackOverrides> overrides = new LinkedHashMap<>();
        overrides.put(new RigId("inherit"), RigPlaybackOverrides.INHERIT);
        overrides.put(new RigId("disabled"), new RigPlaybackOverrides(
                disabledAnchor,
                RotationMask.YAW_PITCH,
                AimOverride.disabled(),
                new ObstacleSettings(true, 0.75)));
        overrides.put(new RigId("replace"), new RigPlaybackOverrides(
                null,
                null,
                AimOverride.replace(replaceTarget),
                null));

        PlaybackAction.Start action = new PlaybackAction.Start(
                new PlaybackSource.Local(new ResourceId("animations/overrides")),
                overrides,
                2f,
                true,
                1.5f);

        PlaybackAction.Start decoded = (PlaybackAction.Start)
                decode(CODEC, encode(CODEC, action, LIMITS), LIMITS);

        assertEquals(action, decoded);
        assertEquals(Map.copyOf(overrides), decoded.rigOverrides());

        RigPlaybackOverrides inherited = decoded.rigOverrides().get(new RigId("inherit"));
        assertNull(inherited.anchor());
        assertNull(inherited.followMask());
        assertTrue(inherited.aim().inherits());
        assertNull(inherited.obstacle());

        RigPlaybackOverrides disabled = decoded.rigOverrides().get(new RigId("disabled"));
        assertEquals(disabledAnchor, disabled.anchor());
        assertEquals(RotationMask.YAW_PITCH, disabled.followMask());
        assertTrue(disabled.aim().disables());
        assertFalse(disabled.aim().inherits());
        assertEquals(new ObstacleSettings(true, 0.75), disabled.obstacle());

        RigPlaybackOverrides replaced = decoded.rigOverrides().get(new RigId("replace"));
        assertNull(replaced.anchor());
        assertNull(replaced.followMask());
        assertTrue(replaced.aim() instanceof AimOverride.Replace);
        assertEquals(replaceTarget, replaced.aim().fixedTarget().orElseThrow());
        assertNull(replaced.obstacle());
    }

    // endregion

    // region 版本与限额

    @Test
    void rejectsMismatchedProtocolVersion() {
        RegistryFriendlyByteBuf buffer = encode(CODEC, new PlaybackAction.Stop(), LIMITS);
        buffer.setInt(0, PlaybackProtocol.CURRENT_VERSION + 1);

        assertError(CODEC.decode(buffer, LIMITS), "version");

        RegistryFriendlyByteBuf zeroVersion = encode(CODEC, new PlaybackAction.Stop(), LIMITS);
        zeroVersion.setInt(0, 0);

        assertError(CODEC.decode(zeroVersion, LIMITS), "version");
    }

    @Test
    void rejectsEmbeddedDocumentOverLimitOnEncodeAndDecode() {
        PlaybackAction.Start action = new PlaybackAction.Start(
                new PlaybackSource.Embedded(document()),
                Map.of(),
                1f,
                false,
                0f);

        RegistryFriendlyByteBuf valid = encode(CODEC, action, LIMITS);
        int documentLength = embeddedDocumentLength(valid);

        assertTrue(documentLength > 1);

        PlaybackLimits documentTooSmall = new PlaybackLimits(
                documentLength - 1, LIMITS.maxRigOverrides(), LIMITS.maxTotalBytes());

        assertError(CODEC.encode(buffer(), action, documentTooSmall), "maxDocumentBytes");
        assertError(CODEC.decode(bufferOf(copyBytes(valid)), documentTooSmall), "maxDocumentBytes");

        PlaybackLimits exactDocumentLimit = new PlaybackLimits(
                documentLength, LIMITS.maxRigOverrides(), LIMITS.maxTotalBytes());

        assertSuccess(CODEC.encode(buffer(), action, exactDocumentLimit));
    }

    @Test
    void rejectsTotalBytesOverLimitOnEncodeAndDecode() {
        PlaybackAction.Start action = new PlaybackAction.Start(
                new PlaybackSource.Local(new ResourceId("a".repeat(200))),
                Map.of(),
                1f,
                false,
                0f);

        RegistryFriendlyByteBuf valid = encode(CODEC, action, LIMITS);
        int totalBytes = valid.readableBytes();
        int tooSmall = totalBytes - 1;

        assertTrue(tooSmall > 1);

        PlaybackLimits totalTooSmall = new PlaybackLimits(1, LIMITS.maxRigOverrides(), tooSmall);

        assertError(CODEC.encode(buffer(), action, totalTooSmall), "maxTotalBytes");
        assertError(CODEC.decode(bufferOf(copyBytes(valid)), totalTooSmall), "maxTotalBytes");

        PlaybackLimits exactTotalLimit = new PlaybackLimits(1, LIMITS.maxRigOverrides(), totalBytes);

        assertSuccess(CODEC.encode(buffer(), action, exactTotalLimit));
        assertSuccess(CODEC.decode(bufferOf(copyBytes(valid)), exactTotalLimit));
    }

    @Test
    void rejectsRigOverrideCountOverLimitOnEncodeAndDecode() {
        Map<RigId, RigPlaybackOverrides> twoOverrides = Map.of(
                new RigId("a"), RigPlaybackOverrides.INHERIT,
                new RigId("b"), RigPlaybackOverrides.INHERIT);

        PlaybackAction.Start action = new PlaybackAction.Start(
                new PlaybackSource.Local(new ResourceId("animations/two")),
                twoOverrides,
                1f,
                false,
                0f);

        RegistryFriendlyByteBuf valid = encode(CODEC, action, LIMITS);
        PlaybackLimits oneOverrideOnly = new PlaybackLimits(1024, 1, 4096);

        assertError(CODEC.encode(buffer(), action, oneOverrideOnly), "maxRigOverrides");
        assertError(CODEC.decode(bufferOf(copyBytes(valid)), oneOverrideOnly), "maxRigOverrides");

        PlaybackLimits exactOverrideLimit = new PlaybackLimits(1024, 2, 4096);

        assertSuccess(CODEC.encode(buffer(), action, exactOverrideLimit));
        assertSuccess(CODEC.decode(bufferOf(copyBytes(valid)), exactOverrideLimit));
    }

    // endregion

    // region 未知类型与截断

    @Test
    void rejectsUnknownAnchorTypeOnEncodeAndDecode() {
        FixedAnchor anchor = new FixedAnchor(new Vec3(1.0, 2.0, 3.0));
        RigPlaybackOverrides overrides = new RigPlaybackOverrides(
                anchor, RotationMask.ALL, AimOverride.inherit(), null);

        PlaybackAction.Start action = new PlaybackAction.Start(
                new PlaybackSource.Local(new ResourceId("animations/anchor")),
                Map.of(new RigId("rig"), overrides),
                1f,
                false,
                0f);

        RegistryFriendlyByteBuf valid = encode(CODEC, action, LIMITS);

        assertError(CODEC_WITHOUT_ANCHOR.encode(buffer(), action, LIMITS), "unknown anchor type");
        assertError(CODEC_WITHOUT_ANCHOR.decode(bufferOf(copyBytes(valid)), LIMITS),
                "unknown anchor type");
    }

    @Test
    void rejectsEmbeddedDocumentWithUnknownAnchorType() {
        PlaybackAction.Start action = new PlaybackAction.Start(
                new PlaybackSource.Embedded(document()),
                Map.of(),
                1f,
                false,
                0f);

        RegistryFriendlyByteBuf valid = encode(CODEC, action, LIMITS);

        assertError(CODEC_WITHOUT_ANCHOR.decode(bufferOf(copyBytes(valid)), LIMITS),
                "embedded document decode failed");
    }

    @Test
    void rejectsEveryTruncatedPrefixWithoutReturningPartialAction() {
        FixedAnchor overrideAnchor = new FixedAnchor(new Vec3(1.0, 2.0, 3.0));
        Map<RigId, RigPlaybackOverrides> overrides = Map.of(
                new RigId("full"), new RigPlaybackOverrides(
                        overrideAnchor,
                        RotationMask.YAW_PITCH,
                        AimOverride.replace(new AimTarget(
                                new FixedAnchor(new Vec3(4.0, 5.0, 6.0)),
                                RotationMask.ALL,
                                new Vec3(0.25, 0.5, 0.75))),
                        new ObstacleSettings(true, 0.25)));

        PlaybackAction.Start action = new PlaybackAction.Start(
                new PlaybackSource.Local(new ResourceId("animations/truncated")),
                overrides,
                1.5f,
                true,
                2f);

        byte[] encoded = copyBytes(encode(CODEC, action, LIMITS));

        assertSuccess(CODEC.decode(bufferOf(encoded), LIMITS));

        for (int length = 0; length < encoded.length; length++) {
            int prefixLength = length;
            DataResult<PlaybackAction> decoded =
                    CODEC.decode(bufferOf(Arrays.copyOf(encoded, prefixLength)), LIMITS);

            assertTrue(decoded.result().isEmpty(),
                    () -> "prefix of " + prefixLength + " bytes unexpectedly decoded");
            assertTrue(decoded.error().isPresent(),
                    () -> "prefix of " + prefixLength + " bytes has no error message");
        }
    }

    // endregion

    // region 辅助

    private static CameraAnimation document() {
        CameraRig rig = CameraRig.create(
                        new RigId("rig"),
                        "rig",
                        List.of(),
                        new TrackId("aim"),
                        new TrackId("active"))
                .withSpace(new Space.Local(
                        new FixedAnchor(new Vec3(1.25, 2.5, -3.75)),
                        RotationMask.ALL));

        return CameraAnimation.create("doc", rig, new TrackId("source"));
    }

    /// 纯 JVM 测试不需要真实连接上下文；两参构造只为取得带注册表访问的缓冲区。
    @SuppressWarnings("deprecation")
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static RegistryFriendlyByteBuf bufferOf(byte[] bytes) {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeBytes(bytes);
        return buffer;
    }

    private static RegistryFriendlyByteBuf encode(PlaybackProtocolCodec codec,
                                                  PlaybackAction action,
                                                  PlaybackLimits limits) {
        RegistryFriendlyByteBuf buffer = buffer();
        assertSuccess(codec.encode(buffer, action, limits));
        return buffer;
    }

    private static PlaybackAction decode(PlaybackProtocolCodec codec,
                                         RegistryFriendlyByteBuf buffer,
                                         PlaybackLimits limits) {
        DataResult<PlaybackAction> result = codec.decode(buffer, limits);

        assertSuccess(result);
        return result.result().orElseThrow();
    }

    private static byte[] copyBytes(RegistryFriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }

    private static int embeddedDocumentLength(RegistryFriendlyByteBuf buffer) {
        RegistryFriendlyByteBuf probe = bufferOf(copyBytes(buffer));
        probe.readInt();  // protocol version
        probe.readByte(); // action tag
        probe.readByte(); // source tag
        return probe.readVarInt();
    }

    private static void assertSuccess(DataResult<?> result) {
        assertTrue(result.result().isPresent(), () -> "expected success: " + result);
    }

    private static void assertError(DataResult<?> result, String messageFragment) {
        assertTrue(result.result().isEmpty(), () -> "expected error: " + result);
        assertTrue(result.error().isPresent(), "expected an error message");

        String message = result.error().orElseThrow().message();
        String fragment = messageFragment.toLowerCase(Locale.ROOT);

        assertTrue(message.toLowerCase(Locale.ROOT).contains(fragment),
                () -> "expected error message containing '" + messageFragment + "': " + message);
    }

    // endregion
}
