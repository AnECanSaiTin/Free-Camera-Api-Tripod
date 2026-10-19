package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AudioRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.MarkerKind;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.OverlayElement;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.OverlayKind;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleArgs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.WorldSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DocumentValidator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.AudioRefId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.OverlayId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 两阶段解码与文档 Codec 往返（主设计 §4.2、§8.1–§8.3）。
///
/// 覆盖：
///
/// 1. **完整往返**：机位 / 通道 / 注视 / 活动 / 布局曲线 / 符号 / 标记 / 采样 / 音轨引用 /
///    扩展轨道全部经 Codec 写出再读回，值相等；
/// 2. **结构阶段拒绝**：版本不符、版本缺失、未知锚点 / 轨道 / 采样类型；
/// 3. **校验阶段拒绝**：结构成立但统一校验报 `ERROR`；只有 `WARNING` 时仍接受；
/// 4. **O1–O3 前的不序列化边界**：含非空叠加的文档编码失败，不静默丢弃。
class DocumentCodecTest {

    private static final Identifier ANCHOR_TYPE = Identifier.fromNamespaceAndPath("test", "fixed_anchor");
    private static final Identifier SAMPLE_TYPE = Identifier.fromNamespaceAndPath("test", "constant");
    private static final Identifier TRACK_TYPE = Identifier.fromNamespaceAndPath("test", "text");

    // region 测试用类型（模拟第三方扩展）

    private record FixedAnchor(Vec3 position) implements Anchor {
        @Override
        public Identifier type() {
            return ANCHOR_TYPE;
        }
    }

    private record FixedAnchorType() implements AnchorType<FixedAnchor> {
        @Override
        public Identifier id() {
            return ANCHOR_TYPE;
        }

        @Override
        public MapCodec<FixedAnchor> codec() {
            return Vec3.CODEC.fieldOf("position").xmap(FixedAnchor::new, FixedAnchor::position);
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, FixedAnchor> streamCodec() {
            return StreamCodec.<io.netty.buffer.ByteBuf, FixedAnchor>unit(new FixedAnchor(Vec3.ZERO));
        }
    }

    private record ConstantSampleArgs(float value) implements SampleArgs {
        @Override
        public Identifier type() {
            return SAMPLE_TYPE;
        }
    }

    private record ConstantSampleType() implements SampleType<ConstantSampleArgs> {
        @Override
        public Identifier id() {
            return SAMPLE_TYPE;
        }

        @Override
        public MapCodec<ConstantSampleArgs> codec() {
            return Codec.FLOAT.fieldOf("value").xmap(ConstantSampleArgs::new, ConstantSampleArgs::value);
        }
    }

    private record TextTrack(TrackId id, String name, String text) implements AnimationTrack.ExtensionTrack {
        @Override
        public Identifier type() {
            return TRACK_TYPE;
        }

        @Override
        public float duration() {
            return 0f;
        }

        @Override
        public int keyCount() {
            return 0;
        }
    }

    private record TextTrackType() implements TrackType<TextTrack> {
        @Override
        public Identifier id() {
            return TRACK_TYPE;
        }

        @Override
        public MapCodec<TextTrack> codec() {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    TrackId.CODEC.fieldOf("id").forGetter(TextTrack::id),
                    Codecs.nonBlankString().fieldOf("name").forGetter(TextTrack::name),
                    Codec.STRING.fieldOf("text").forGetter(TextTrack::text)
            ).apply(instance, TextTrack::new));
        }
    }

    // endregion

    // region 测试用注册表

    private static class TestRegistry<T> implements TypeRegistry<T> {
        private final Function<T, Identifier> id;
        private final Map<Identifier, T> types = new LinkedHashMap<>();
        private boolean frozen;

        private TestRegistry(Function<T, Identifier> id) {
            this.id = id;
        }

        @Override
        public Optional<T> find(Identifier key) {
            return Optional.ofNullable(types.get(key));
        }

        @Override
        public Set<Identifier> ids() {
            return Set.copyOf(types.keySet());
        }

        @Override
        public void register(T type) {
            if (frozen) {
                throw new IllegalStateException("Registry is frozen");
            }

            if (types.putIfAbsent(id.apply(type), type) != null) {
                throw new IllegalArgumentException("Duplicate type: " + id.apply(type));
            }
        }

        @Override
        public void freeze() {
            frozen = true;
        }

        @Override
        public boolean frozen() {
            return frozen;
        }
    }

    private static final class TestAnchors extends TestRegistry<AnchorType<?>> implements AnchorTypeRegistry {
        private TestAnchors() {
            super(AnchorType::id);
        }
    }

    private static final class TestSamples extends TestRegistry<SampleType<?>> implements SampleTypeRegistry {
        private TestSamples() {
            super(SampleType::id);
        }
    }

    private static final class TestTracks extends TestRegistry<TrackType<?>> implements TrackTypeRegistry {
        private TestTracks() {
            super(TrackType::id);
        }
    }

    private static DocumentRegistries registries() {
        TestAnchors anchors = new TestAnchors();
        anchors.register(new FixedAnchorType());
        anchors.freeze();

        TestSamples samples = new TestSamples();
        samples.register(new ConstantSampleType());
        samples.freeze();

        TestTracks tracks = new TestTracks();
        tracks.register(new TextTrackType());
        tracks.freeze();

        return new DocumentRegistries(anchors, tracks, samples);
    }

    // endregion

    // region 文档构造

    private static Curve channelCurve() {
        Keyframe key = new Keyframe(new KeyId("channel-key"), 4f, new Constant(0.5f),
                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR);

        return Curve.of(key);
    }

    private static Curve layoutCurve() {
        Keyframe key = new Keyframe(new KeyId("layout-key"), 6f, new Constant(0.3f),
                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR);

        return Curve.of(key);
    }

    private static CameraAnimation document() {
        ActiveTrack active = ActiveTrack.enabled(new TrackId("active"));

        RigView.Rect rect = new RigView.Rect.Normalized(
                AnimatedNumber.constant(0f),
                AnimatedNumber.constant(0f),
                AnimatedNumber.keyed(layoutCurve(), 0.5f),
                AnimatedNumber.constant(1f));

        AimTrack aim = AimTrack.of(new TrackId("aim"), "aim", AimState.OFF, List.of(
                ValueKeyframe.at(new KeyId("aim-key"), 3f,
                        AimState.target(new AimTarget(new FixedAnchor(new Vec3(4, 5, 6)),
                                RotationMask.YAW_PITCH, Vec3.ZERO)))));

        CameraRig rig = CameraRig.create(new RigId("rig"), "rig",
                        List.of(CurveTrack.ofChannel(new TrackId("rotation.x"), "rotation.x",
                                CameraChannel.ROTATION_X, channelCurve())),
                        aim.id(), active.id())
                .withAim(aim)
                .withSpace(new Space.Local(new FixedAnchor(new Vec3(1, 2, 3)), RotationMask.ALL))
                .withView(RigView.full(active).withRect(rect));

        return CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new Symbols(
                        List.of(new Variable("speed", new Formula("2 * 3"))),
                        List.of(new CustomFunction("f", List.of("x"), "x + 1"))))
                .withMarkers(List.of(new Marker(new MarkerId("marker"), 1.5f, "beat", MarkerKind.BEAT)))
                .withSamples(List.of(new WorldSample(new SampleId("sample"), "ground",
                        SAMPLE_TYPE, new ConstantSampleArgs(1f), 2)))
                .withAudioRefs(List.of(new AudioRef(new AudioRefId("audio"), "assets/test.ogg",
                        null, 0f, 0f, 4f, 1f)))
                .withTracks(List.<AnimationTrack.ExtensionTrack>of(new TextTrack(new TrackId("text"), "text", "hello")));
    }

    private static JsonObject encode(DocumentCodec codec, CameraAnimation document) {
        DataResult<JsonElement> encoded = codec.encode(document, JsonOps.INSTANCE);

        assertTrue(encoded.result().isPresent(), () -> "encode failed: " + encoded);
        return encoded.result().orElseThrow().getAsJsonObject();
    }

    // endregion

    @Test
    void roundTripsFullDocumentThroughCodec() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());
        CameraAnimation document = document();

        JsonObject json = encode(codec, document);

        assertEquals(CameraAnimation.CURRENT_DATA_VERSION, json.get("data_version").getAsInt());
        assertTrue(json.has("rigs"));
        assertTrue(json.has("camera_source"));
        assertTrue(json.has("samples"));
        assertTrue(json.has("audio_refs"));
        assertFalse(json.has("overlays"), "O1-O3 前不写 overlays");

        DocumentDecodeResult decoded = codec.decode(JsonOps.INSTANCE, json);

        assertTrue(decoded.accepted(), () -> "decode failed: " + decoded);
        assertEquals(document, decoded.candidate().orElseThrow());
    }

    @Test
    void rejectsUnsupportedAndMissingDataVersionInStructurePhase() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());
        JsonObject json = encode(codec, document());

        JsonObject future = json.deepCopy();
        future.addProperty("data_version", CameraAnimation.CURRENT_DATA_VERSION + 1);

        DocumentDecodeResult rejected = codec.decode(JsonOps.INSTANCE, future);

        assertTrue(rejected instanceof DocumentDecodeResult.StructureFailed);
        assertTrue(((DocumentDecodeResult.StructureFailed) rejected).message().contains("data version"));

        JsonObject missing = json.deepCopy();
        missing.remove("data_version");

        assertTrue(codec.decode(JsonOps.INSTANCE, missing) instanceof DocumentDecodeResult.StructureFailed);
    }

    @Test
    void rejectsUnknownOpenTypesInStructurePhase() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());
        JsonObject json = encode(codec, document());

        JsonObject unknownAnchor = json.deepCopy();
        anchorObject(unknownAnchor).addProperty("type", "test:missing_anchor");

        DocumentDecodeResult anchorResult = codec.decode(JsonOps.INSTANCE, unknownAnchor);

        assertTrue(anchorResult instanceof DocumentDecodeResult.StructureFailed);
        assertTrue(((DocumentDecodeResult.StructureFailed) anchorResult).message().contains("Unknown anchor type"));

        JsonObject unknownTrack = json.deepCopy();
        unknownTrack.getAsJsonArray("tracks").get(0).getAsJsonObject().addProperty("type", "test:missing_track");

        DocumentDecodeResult trackResult = codec.decode(JsonOps.INSTANCE, unknownTrack);

        assertTrue(trackResult instanceof DocumentDecodeResult.StructureFailed);
        assertTrue(((DocumentDecodeResult.StructureFailed) trackResult).message().contains("Unknown track type"));

        JsonObject unknownSample = json.deepCopy();
        unknownSample.getAsJsonArray("samples").get(0).getAsJsonObject()
                .addProperty("kind", "test:missing_sample");

        DocumentDecodeResult sampleResult = codec.decode(JsonOps.INSTANCE, unknownSample);

        assertTrue(sampleResult instanceof DocumentDecodeResult.StructureFailed);
        assertTrue(((DocumentDecodeResult.StructureFailed) sampleResult).message().contains("Unknown sample type"));
    }

    @Test
    void validationPhaseRejectsErrorsAndAcceptsWarnings() {
        JsonObject json = encode(DocumentCodec.of(registries(), candidate -> List.of()), document());

        DocumentValidator error = candidate -> List.of(diagnostic(Severity.ERROR));
        DocumentDecodeResult rejected = DocumentCodec.of(registries(), error)
                .decode(JsonOps.INSTANCE, json.deepCopy());

        assertTrue(rejected instanceof DocumentDecodeResult.ValidationFailed);
        assertFalse(rejected.accepted());
        assertTrue(rejected.structureError().isEmpty());
        assertEquals(1, rejected.diagnostics().size());

        DocumentValidator warning = candidate -> List.of(diagnostic(Severity.WARNING));
        DocumentDecodeResult accepted = DocumentCodec.of(registries(), warning)
                .decode(JsonOps.INSTANCE, json.deepCopy());

        assertTrue(accepted.accepted());
        assertEquals(1, accepted.diagnostics().size());
    }

    @Test
    void encodeRejectsOverlaysUntilContentTypesExist() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());

        OverlayElement overlay = new OverlayElement(new OverlayId("overlay"), OverlayKind.LETTERBOX,
                true, 0, 0f, 10f, new Constant(1f), 0f, 0f, "");

        CameraAnimation withOverlay = document().withOverlays(List.of(overlay));
        DataResult<JsonElement> encoded = codec.encode(withOverlay, JsonOps.INSTANCE);

        assertTrue(encoded.isError());
        assertTrue(encoded.error().isPresent());

        // 根 Codec 也必须拒绝：不能让直接调用方绕过 DocumentCodec 的检查（R34 C1）。
        assertTrue(CameraAnimation.codec(registries())
                .encodeStart(JsonOps.INSTANCE, withOverlay).isError());
    }

    @Test
    void decodeRejectsNonEmptyOverlaysInsteadOfDroppingThem() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());
        JsonObject json = encode(codec, document());

        JsonObject withOverlay = json.deepCopy();
        withOverlay.add("overlays", new com.google.gson.JsonArray());
        withOverlay.getAsJsonArray("overlays").add(new JsonObject());

        DocumentDecodeResult rejected = codec.decode(JsonOps.INSTANCE, withOverlay);

        assertTrue(rejected instanceof DocumentDecodeResult.StructureFailed);
        assertTrue(((DocumentDecodeResult.StructureFailed) rejected).message().contains("overlays"));

        // 空列表仍然接受：字段存在但没有作者数据，不构成丢失。
        JsonObject emptyOverlays = json.deepCopy();
        emptyOverlays.add("overlays", new com.google.gson.JsonArray());

        assertTrue(codec.decode(JsonOps.INSTANCE, emptyOverlays).accepted());
    }

    @Test
    void rejectsFractionalAndOverflowingIntegers() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());
        JsonObject json = encode(codec, document());

        JsonObject fractionalVersion = json.deepCopy();
        fractionalVersion.addProperty("data_version", 1.9);

        assertTrue(codec.decode(JsonOps.INSTANCE, fractionalVersion)
                instanceof DocumentDecodeResult.StructureFailed);

        JsonObject overflowingVersion = json.deepCopy();
        overflowingVersion.addProperty("data_version", 4294967297L);

        assertTrue(codec.decode(JsonOps.INSTANCE, overflowingVersion)
                instanceof DocumentDecodeResult.StructureFailed);

        JsonObject fractionalFrames = json.deepCopy();
        fractionalFrames.getAsJsonArray("rigs").get(0).getAsJsonObject()
                .getAsJsonObject("view").addProperty("update_every_frames", 1.9);

        assertTrue(codec.decode(JsonOps.INSTANCE, fractionalFrames)
                instanceof DocumentDecodeResult.StructureFailed);

        JsonObject fractionalTicks = json.deepCopy();
        fractionalTicks.getAsJsonArray("samples").get(0).getAsJsonObject()
                .addProperty("resample_every_ticks", 1.9);

        assertTrue(codec.decode(JsonOps.INSTANCE, fractionalTicks)
                instanceof DocumentDecodeResult.StructureFailed);
    }

    @Test
    void decodeResultDefendsSeverityInvariants() {
        CameraAnimation candidate = document();

        assertThrows(IllegalArgumentException.class,
                () -> new DocumentDecodeResult.Accepted(candidate, List.of(diagnostic(Severity.ERROR))));

        assertThrows(IllegalArgumentException.class,
                () -> new DocumentDecodeResult.ValidationFailed(List.of(diagnostic(Severity.WARNING))));

        assertTrue(new DocumentDecodeResult.ValidationFailed(List.of(diagnostic(Severity.ERROR)))
                .diagnostics()
                .stream()
                .anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR));
    }

    @Test
    void rejectsHighPrecisionNonIntegersPreservingLiteral() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());
        String json = encode(codec, document()).toString();

        // 用 JsonParser 保留原始字面量：先经 double 会被舍入成 1，精确十进制检查必须拒绝。
        assertTrue(codec.decode(JsonOps.INSTANCE, replaceVersion(json, "1.0000000000000001"))
                instanceof DocumentDecodeResult.StructureFailed);
        assertTrue(codec.decode(JsonOps.INSTANCE, replaceVersion(json, "0.99999999999999999"))
                instanceof DocumentDecodeResult.StructureFailed);
        assertTrue(codec.decode(JsonOps.INSTANCE, replaceVersion(json, "2147483647.0000001"))
                instanceof DocumentDecodeResult.StructureFailed);
        assertTrue(codec.decode(JsonOps.INSTANCE, replaceVersion(json, "-2147483648.0000001"))
                instanceof DocumentDecodeResult.StructureFailed);

        // 数值上确为整数的小数写法仍然接受（1.0 是整数，不是截断）。
        DocumentDecodeResult accepted = codec.decode(JsonOps.INSTANCE, replaceVersion(json, "1.0"));

        assertTrue(accepted.accepted());
    }

    @Test
    void roundTripsThroughCompressedJsonOps() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());
        CameraAnimation document = document();

        DataResult<JsonElement> encoded = codec.encode(document, JsonOps.COMPRESSED);

        assertTrue(encoded.result().isPresent(), () -> "COMPRESSED encode failed: " + encoded);

        DocumentDecodeResult decoded = codec.decode(JsonOps.COMPRESSED, encoded.result().orElseThrow());

        assertTrue(decoded.accepted(), () -> "COMPRESSED decode failed: " + decoded);
        assertEquals(document, decoded.candidate().orElseThrow());

        // 根 Codec 的 COMPRESSED 往返同样必须成立（R36-N2 回归点）。
        DataResult<JsonElement> rootEncoded = CameraAnimation.codec(registries())
                .encodeStart(JsonOps.COMPRESSED, document);

        assertTrue(rootEncoded.result().isPresent());

        CameraAnimation rootDecoded = CameraAnimation.codec(registries())
                .parse(JsonOps.COMPRESSED, rootEncoded.result().orElseThrow())
                .result()
                .orElseThrow();

        assertEquals(document, rootDecoded);
    }

    @Test
    void compressedOpsStillRejectsOverlaysOnEncode() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());

        OverlayElement overlay = new OverlayElement(new OverlayId("overlay"), OverlayKind.LETTERBOX,
                true, 0, 0f, 10f, new Constant(1f), 0f, 0f, "");

        assertTrue(codec.encode(document().withOverlays(List.of(overlay)), JsonOps.COMPRESSED)
                .isError());
    }

    @Test
    void strictIntUsesBinaryValueForFloatAndDouble() {
        // Float.toString() 只保证最短可回读文本：100000008f 会输出 1.0000001E8。
        // 必须以二进制值判断，不能按文本转换（R38-N1）。
        assertEquals(100000008, Codecs.strictInt()
                .parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createFloat(100000008f))
                .result()
                .orElseThrow());

        assertEquals(Integer.MIN_VALUE, Codecs.strictInt()
                .parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createFloat((float) Integer.MIN_VALUE))
                .result()
                .orElseThrow());

        assertEquals(100000008, Codecs.strictInt()
                .parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createDouble(100000008d))
                .result()
                .orElseThrow());

        assertTrue(Codecs.strictInt()
                .parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createFloat(1.5f)).isError());
        assertTrue(Codecs.strictInt()
                .parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createFloat(5.0e9f)).isError());
        assertTrue(Codecs.strictInt()
                .parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createFloat((float) Integer.MAX_VALUE)).isError());
    }

    @Test
    void documentAcceptsFloatTypedIntegerWithoutChangingValue() {
        DocumentCodec codec = DocumentCodec.of(registries(), candidate -> List.of());
        JsonObject json = encode(codec, document());

        json.getAsJsonArray("rigs").get(0).getAsJsonObject()
                .getAsJsonObject("view")
                .add("z_order", new JsonPrimitive(100000008f));

        DocumentDecodeResult decoded = codec.decode(JsonOps.INSTANCE, json);

        assertTrue(decoded.accepted());
        assertEquals(100000008,
                decoded.candidate().orElseThrow().rigs().get(0).view().zOrder());
    }

    // region 辅助

    private static JsonObject replaceVersion(String json, String literal) {
        return JsonParser.parseString(json.replaceFirst(
                java.util.regex.Pattern.quote("\"data_version\":1"),
                java.util.regex.Matcher.quoteReplacement("\"data_version\":" + literal))).getAsJsonObject();
    }

    private static JsonObject anchorObject(JsonObject root) {
        return root.getAsJsonArray("rigs").get(0).getAsJsonObject()
                .getAsJsonObject("space").getAsJsonObject("default_anchor");
    }

    private static Diagnostic diagnostic(Severity severity) {
        return new Diagnostic(new DiagnosticCode("test:validation"), severity,
                LocalizedMessage.literal("test diagnostic"),
                new DiagnosticScope.Document(Revision.INITIAL), null, null);
    }

    // endregion
}
