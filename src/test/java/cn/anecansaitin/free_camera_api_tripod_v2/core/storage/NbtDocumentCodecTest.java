package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.DocumentCodec;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.DocumentDecodeResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// NBT 文档载荷编解码与深度 / 条目 / 字节限制（主设计 §8.1–§8.2）。
class NbtDocumentCodecTest {

    @Test
    void roundTripPreservesDocumentAndChecksAcceptedState() {
        DocumentCodec documents = DocumentCodec.of(frozenRegistries(), document -> List.of());
        NbtDocumentCodec codec = new NbtDocumentCodec(documents, NbtResourceLimits.DEFAULT);
        CameraAnimation document = document();

        DataResult<byte[]> encoded = codec.encode(document);
        assertTrue(encoded.result().isPresent(), () -> "encode failed: " + encoded.error());
        byte[] bytes = encoded.result().orElseThrow();
        assertTrue(bytes.length > 0);

        DocumentDecodeResult decoded = codec.decode(bytes);

        DocumentDecodeResult.Accepted accepted = assertType(
                DocumentDecodeResult.Accepted.class, decoded);
        assertEquals(document, accepted.document());
    }

    @Test
    void decodeRejectsPayloadOverByteLimitBeforeReading() {
        DocumentCodec documents = DocumentCodec.of(frozenRegistries(), document -> List.of());
        NbtDocumentCodec codec = new NbtDocumentCodec(documents,
                new NbtResourceLimits(4, 128, 1_000_000));

        DocumentDecodeResult result = codec.decode(new byte[]{1, 2, 3, 4, 5});

        assertType(DocumentDecodeResult.StructureFailed.class, result);
    }

    @Test
    void encodeRejectsPayloadOverByteLimit() {
        DocumentCodec documents = DocumentCodec.of(frozenRegistries(), document -> List.of());
        NbtDocumentCodec codec = new NbtDocumentCodec(documents,
                new NbtResourceLimits(1, 128, 1_000_000));

        DataResult<byte[]> result = codec.encode(document());

        assertTrue(result.error().isPresent(), "oversized NBT must be rejected");
    }

    @Test
    void malformedBytesBecomeStructureFailure() {
        DocumentCodec documents = DocumentCodec.of(frozenRegistries(), document -> List.of());
        NbtDocumentCodec codec = new NbtDocumentCodec(documents, NbtResourceLimits.DEFAULT);

        DocumentDecodeResult result = codec.decode(new byte[]{1, 2, 3});

        assertType(DocumentDecodeResult.StructureFailed.class, result);
    }

    @Test
    void tagGuardRejectsEntryAndDepthOverflows() {
        CompoundTag root = new CompoundTag();
        root.putString("a", "1");
        root.putString("b", "2");
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("x"));
        root.put("list", list);

        assertNotNull(NbtTagGuard.check(root, new NbtResourceLimits(1024, 128, 2)));
        assertNull(NbtTagGuard.check(root, new NbtResourceLimits(1024, 128, 4)));

        CompoundTag deep = new CompoundTag();
        ListTag level1 = new ListTag();
        ListTag level2 = new ListTag();
        level2.add(StringTag.valueOf("x"));
        level1.add(level2);
        deep.put("list", level1);

        assertNotNull(NbtTagGuard.check(deep, new NbtResourceLimits(1024, 2, 100)));
        assertNull(NbtTagGuard.check(deep, new NbtResourceLimits(1024, 3, 100)));
    }

    private static <T> T assertType(Class<T> type, Object value) {
        assertTrue(type.isInstance(value),
                () -> "expected " + type.getSimpleName() + " but got " + value);
        return type.cast(value);
    }

    private static DocumentRegistries frozenRegistries() {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        anchors.freeze();
        tracks.freeze();
        samples.freeze();
        return new DocumentRegistries(anchors, tracks, samples);
    }

    private static CameraAnimation document() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        return CameraAnimation.create("doc", rig, new TrackId("source"));
    }
}
