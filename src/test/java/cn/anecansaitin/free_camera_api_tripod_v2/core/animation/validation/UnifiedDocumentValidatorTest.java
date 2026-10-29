package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.validation;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeValidationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeValidator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 统一文档校验的分层、引用与类型钩子分派。
class UnifiedDocumentValidatorTest {

    private static final Revision REVISION = new Revision(7);

    @Test
    void rejectsDuplicateRigIds() {
        CameraRig first = CameraRig.create(new RigId("same"), "first", List.of(),
                new TrackId("a-aim"), new TrackId("a-active"));
        CameraRig second = CameraRig.create(new RigId("same"), "second", List.of(),
                new TrackId("b-aim"), new TrackId("b-active"));
        CameraAnimation document = CameraAnimation.create("doc", first, new TrackId("source"))
                .withRigs(List.of(first, second));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "Duplicate rig id"));
    }

    @Test
    void rejectsDuplicateTrackIdsAcrossRigs() {
        CurveTrack first = CurveTrack.ofCurve(new TrackId("dup"), "first", Curve.EMPTY);
        CurveTrack second = CurveTrack.ofCurve(new TrackId("dup"), "second", Curve.EMPTY);
        CameraRig a = CameraRig.create(new RigId("a"), "a", List.of(first),
                new TrackId("a-aim"), new TrackId("a-active"));
        CameraRig b = CameraRig.create(new RigId("b"), "b", List.of(second),
                new TrackId("b-aim"), new TrackId("b-active"));
        CameraAnimation document = CameraAnimation.create("doc", a, new TrackId("source"))
                .withRigs(List.of(a, b));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "Duplicate track id"));
    }

    @Test
    void rejectsCameraSourceReferenceToMissingRig() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraSourceTrack source = CameraSourceTrack.of(new TrackId("source"), "source",
                CameraSource.rig(new RigId("missing")), List.of());
        CameraAnimation document = CameraAnimation.create("doc", rig, source.id())
                .withCameraSource(source);

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "does not exist"));
    }

    @Test
    void rejectsTrackRefThatIsNotACurveTrack() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols(
                        List.of(new Variable("v", new TrackRef(new TrackId("aim")))), List.of()));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "does not resolve to a curve track"));
    }

    @Test
    void rejectsSourceRigThatIsInactiveAtAStepBoundary() {
        ActiveTrack active = ActiveTrack.of(new TrackId("active"), "active", true,
                List.of(ValueKeyframe.at(new KeyId("inactive"), 2f, false)));
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), active.id())
                .withView(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView
                        .full(active));
        CameraSourceTrack source = CameraSourceTrack.of(new TrackId("source"), "source",
                CameraSource.player(),
                List.of(ValueKeyframe.at(new KeyId("to-rig"), 2f, CameraSource.rig(rig.id()))));
        CameraAnimation document = CameraAnimation.create("doc", rig, source.id())
                .withCameraSource(source);

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "is inactive at 2.0s"));
    }

    @Test
    void rejectsAimMaskThatEnablesRoll() {
        TestAnchorType anchorType = new TestAnchorType();
        DocumentRegistries registries = registriesWithAnchor(anchorType);

        AimTrack aim = AimTrack.of(new TrackId("aim"), "aim",
                AimState.target(new AimTarget(new TestAnchor(anchorType.id()),
                        RotationMask.ALL, Vec3.ZERO)), List.of());
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                aim.id(), new TrackId("active")).withAim(aim);
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"));

        List<Diagnostic> diagnostics = validator(registries).validate(document);

        assertTrue(hasMessage(diagnostics, "must not enable Roll"));
    }

    @Test
    void dispatchesTrackTypeValidatorWithRevisionAndPath() {
        AtomicReference<TypeValidationContext> captured = new AtomicReference<>();
        FakeTrack track = new FakeTrack(new TrackId("fake"), "fake");
        FakeTrackType type = new FakeTrackType(track, captured);

        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        tracks.register(type);
        tracks.freeze();

        DocumentRegistries registries = new DocumentRegistries(
                frozenAnchors(), tracks, frozenSamples());

        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withTracks(List.of(track));

        List<Diagnostic> diagnostics = validator(registries).validate(document);

        assertEquals(1, diagnostics.size());
        assertEquals("test:fake_broken", diagnostics.getFirst().message() instanceof
                LocalizedMessage.Literal literal ? literal.text() : "");
        assertEquals(new Revision(7), captured.get().revision());
        assertEquals("tracks[0]", captured.get().fieldPath());
        assertEquals(new TrackId("fake"), captured.get().objectId());
    }

    @Test
    void rejectsUnknownAnchorTypeBeforeSemanticChecks() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"))
                .withSpace(new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space.Local(
                        new UnregisteredAnchor(), RotationMask.NONE));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "Unknown anchor type"));
    }

    @Test
    void validationStopsAfterErrorLayer() {
        FakeTrack track = new FakeTrack(new TrackId("fake"), "fake");
        FakeTrackType type = new FakeTrackType(track, new AtomicReference<>());

        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        tracks.register(type);
        tracks.freeze();

        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withTracks(List.of(track));

        List<Diagnostic> diagnostics = validator(new DocumentRegistries(
                frozenAnchors(), tracks, frozenSamples())).validate(document);

        assertEquals(1, diagnostics.size());
        assertFalse(diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() != Severity.ERROR));
    }

    @Test
    void rejectsFormulaWithUnknownVariable() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new Symbols(List.of(
                        new Variable("v", new Formula("missing + 1"))), List.of()));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "unknown variable"));
    }

    @Test
    void rejectsUnreferencedVariableCycle() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new Symbols(List.of(
                        new Variable("a", new Formula("a"))), List.of()));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "Variable cycle"), () -> "unexpected: " + diagnostics);
    }

    @Test
    void rejectsUnreferencedFunctionCycle() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new Symbols(List.of(), List.of(
                        new CustomFunction("f", List.of(), "f()"))));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "Function cycle"), () -> "unexpected: " + diagnostics);
    }

    @Test
    void rejectsTrackCycleHiddenByFunctionParameterScope() {
        CurveTrack track = CurveTrack.ofCurve(new TrackId("track"), "track",
                Curve.of(new Keyframe(new KeyId("track-key"), 0f, new Formula("f(1)"),
                        0f, 0f, 1f, 1f, Curve.Interpolation.LINEAR)));
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(track),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new Symbols(
                        List.of(new Variable("v", new Formula("x")),
                                new Variable("x", new TrackRef(track.id()))),
                        List.of(new CustomFunction("f", List.of("x"), "v"))));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "Track dependency cycle"),
                () -> "unexpected: " + diagnostics);
    }

    @Test
    void rejectsDuplicateKeyIdAcrossTracks() {
        CurveTrack first = CurveTrack.ofCurve(new TrackId("first"), "first",
                Curve.of(new Keyframe(new KeyId("shared-key"), 0f, new Constant(1f),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)));
        CurveTrack second = CurveTrack.ofCurve(new TrackId("second"), "second",
                Curve.of(new Keyframe(new KeyId("shared-key"), 0f, new Constant(2f),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)));
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(first, second),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "Duplicate key id"), () -> "unexpected: " + diagnostics);
    }

    @Test
    void acceptsDistinctKeyIdsAcrossTracks() {
        CurveTrack first = CurveTrack.ofCurve(new TrackId("first"), "first",
                Curve.of(new Keyframe(new KeyId("first-key"), 0f, new Constant(1f),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)));
        CurveTrack second = CurveTrack.ofCurve(new TrackId("second"), "second",
                Curve.of(new Keyframe(new KeyId("second-key"), 0f, new Constant(2f),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)));
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(first, second),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(diagnostics.isEmpty(), () -> "unexpected: " + diagnostics);
    }

    @Test
    void acceptsFormulaReferenceToExistingVariable() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new Symbols(List.of(
                        new Variable("a", new Constant(1f)),
                        new Variable("b", new Formula("a + 1"))), List.of()));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(diagnostics.isEmpty(), () -> "unexpected diagnostics: " + diagnostics);
    }

    @Test
    void functionParametersShadowDocumentVariables() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new Symbols(List.of(), List.of(
                        new CustomFunction("f", List.of("x"), "x + 1"))));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(diagnostics.isEmpty(), () -> "unexpected diagnostics: " + diagnostics);
    }

    @Test
    void rejectsUnboundSampleReference() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new Symbols(List.of(
                        new Variable("v", new Formula("sample(\"missing\")"))), List.of()));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "unbound sample"));
    }

    @Test
    void rejectsSyntaxErrorInFormula() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withSymbols(new Symbols(List.of(
                        new Variable("v", new Formula("1 +"))), List.of()));

        List<Diagnostic> diagnostics = validator(emptyRegistries()).validate(document);

        assertTrue(hasMessage(diagnostics, "cannot compile"));
    }

    // region 测试替身

    private static UnifiedDocumentValidator validator(DocumentRegistries registries) {
        return new UnifiedDocumentValidator(registries, REVISION);
    }

    private static DocumentRegistries emptyRegistries() {
        return new DocumentRegistries(frozenAnchors(), frozenTracks(), frozenSamples());
    }

    private static DocumentRegistries registriesWithAnchor(AnchorType<?> type) {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        anchors.register(type);
        anchors.freeze();
        return new DocumentRegistries(anchors, frozenTracks(), frozenSamples());
    }

    private static FrozenAnchorTypeRegistry frozenAnchors() {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        anchors.freeze();
        return anchors;
    }

    private static FrozenTrackTypeRegistry frozenTracks() {
        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        tracks.freeze();
        return tracks;
    }

    private static FrozenSampleTypeRegistry frozenSamples() {
        FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        samples.freeze();
        return samples;
    }

    private static boolean hasMessage(List<Diagnostic> diagnostics, String fragment) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.message() instanceof
                LocalizedMessage.Literal literal && literal.text().contains(fragment));
    }

    private record TestAnchor(Identifier type) implements Anchor {
    }

    private record TestAnchorType() implements AnchorType<TestAnchor> {
        private static final Identifier ID = Identifier.fromNamespaceAndPath("test", "anchor");

        @Override
        public Identifier id() {
            return ID;
        }

        @Override
        public MapCodec<TestAnchor> codec() {
            return MapCodec.unit(new TestAnchor(ID));
        }

        @Override
        public net.minecraft.network.codec.StreamCodec<? super
                net.minecraft.network.RegistryFriendlyByteBuf, TestAnchor> streamCodec() {
            return net.minecraft.network.codec.StreamCodec.unit(new TestAnchor(ID));
        }
    }

    private record UnregisteredAnchor() implements Anchor {
        private static final Identifier ID = Identifier.fromNamespaceAndPath("test", "unregistered");

        @Override
        public Identifier type() {
            return ID;
        }
    }

    private record FakeTrack(TrackId id, String name) implements AnimationTrack.ExtensionTrack {
        private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "fake");

        public FakeTrack withName(String newName) {
            return new FakeTrack(id, newName);
        }

        @Override
        public Identifier type() {
            return TYPE;
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

    private static final class FakeTrackType implements TrackType<FakeTrack> {
        private final FakeTrack track;
        private final AtomicReference<TypeValidationContext> captured;

        private FakeTrackType(FakeTrack track, AtomicReference<TypeValidationContext> captured) {
            this.track = track;
            this.captured = captured;
        }

        @Override
        public Identifier id() {
            return FakeTrack.TYPE;
        }

        @Override
        public MapCodec<FakeTrack> codec() {
            return MapCodec.unit(track);
        }

        @Override
        public Optional<TypeValidator<FakeTrack>> validator() {
            return Optional.of((value, context) -> {
                captured.set(context);
                return List.of(new Diagnostic(
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode(
                                "test:fake_broken"),
                        Severity.ERROR,
                        LocalizedMessage.literal("test:fake_broken"),
                        new DiagnosticScope.Document(context.revision()),
                        new Diagnostic.Location(context.objectId(), context.fieldPath()),
                        null));
            });
        }
    }

    // endregion
}
