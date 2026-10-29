package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleArgs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleUnknownReason;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolutionContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolver;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollectionContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollector;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeCapabilityStatus;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 类型能力三态与冻结注册表快照（主设计 §2.2、§6.3、§7.7）。
class CoreTypeCapabilityServiceTest {

    private static final Identifier ANCHOR_ID = Identifier.fromNamespaceAndPath("test", "anchor");
    private static final Identifier SAMPLE_ID = Identifier.fromNamespaceAndPath("test", "sample");
    private static final Identifier UNKNOWN_ID = Identifier.fromNamespaceAndPath("test", "unknown");

    @Test
    void unknownTypesAreNotRegistered() {
        CoreTypeCapabilityService service = new Fixture().freeze();

        assertEquals(TypeCapabilityStatus.TYPE_NOT_REGISTERED, service.anchor(UNKNOWN_ID).status());
        assertEquals(TypeCapabilityStatus.TYPE_NOT_REGISTERED, service.sample(UNKNOWN_ID).status());
        assertEquals(TypeCapabilityStatus.TYPE_NOT_REGISTERED, service.track(UNKNOWN_ID).status());
    }

    @Test
    void builtInTrackIdsAreAvailableWithoutExtensionRegistration() {
        CoreTypeCapabilityService service = new Fixture().freeze();

        for (Identifier id : List.of(CurveTrack.TYPE, AimTrack.TYPE,
                ActiveTrack.TYPE, CameraSourceTrack.TYPE)) {
            assertEquals(TypeCapabilityStatus.AVAILABLE, service.track(id).status(),
                    () -> "built-in track type must be available: " + id);
        }
    }

    @Test
    void registeredAnchorWithoutResolverIsRuntimeMissing() {
        Fixture fixture = new Fixture();
        fixture.anchors.register(new TestAnchorType());
        CoreTypeCapabilityService service = fixture.freeze();

        assertEquals(TypeCapabilityStatus.RUNTIME_IMPLEMENTATION_MISSING,
                service.anchor(ANCHOR_ID).status());
    }

    @Test
    void registeredAnchorWithResolverIsAvailable() {
        Fixture fixture = new Fixture();
        fixture.anchors.register(new TestAnchorType());
        fixture.anchorResolvers.register(new TestAnchorResolver(ANCHOR_ID));
        CoreTypeCapabilityService service = fixture.freeze();

        assertEquals(TypeCapabilityStatus.AVAILABLE, service.anchor(ANCHOR_ID).status());
    }

    @Test
    void resolverForUnknownTypeDoesNotCreateTheType() {
        Fixture fixture = new Fixture();
        fixture.anchorResolvers.register(new TestAnchorResolver(UNKNOWN_ID));
        CoreTypeCapabilityService service = fixture.freeze();

        assertEquals(TypeCapabilityStatus.TYPE_NOT_REGISTERED,
                service.anchor(UNKNOWN_ID).status());
    }

    @Test
    void registeredSampleWithoutCollectorIsRuntimeMissing() {
        Fixture fixture = new Fixture();
        fixture.samples.register(new TestSampleType());
        CoreTypeCapabilityService service = fixture.freeze();

        assertEquals(TypeCapabilityStatus.RUNTIME_IMPLEMENTATION_MISSING,
                service.sample(SAMPLE_ID).status());
    }

    @Test
    void registeredSampleWithCollectorIsAvailable() {
        Fixture fixture = new Fixture();
        fixture.samples.register(new TestSampleType());
        fixture.sampleCollectors.register(new TestSampleCollector(SAMPLE_ID));
        CoreTypeCapabilityService service = fixture.freeze();

        assertEquals(TypeCapabilityStatus.AVAILABLE, service.sample(SAMPLE_ID).status());
    }

    @Test
    void registeredExtensionTrackIsAvailable() {
        Fixture fixture = new Fixture();
        fixture.tracks.register(new FakeTrackType());
        CoreTypeCapabilityService service = fixture.freeze();

        assertEquals(TypeCapabilityStatus.AVAILABLE, service.track(FakeTrack.TYPE).status());
    }

    @Test
    void unfrozenRegistryIsRejected() {
        Fixture fixture = new Fixture();
        DocumentRegistries registries = new DocumentRegistries(
                fixture.anchors, fixture.tracks, fixture.samples);

        assertThrows(IllegalStateException.class,
                () -> new CoreTypeCapabilityService(registries,
                        fixture.anchorResolvers, fixture.sampleCollectors));
    }

    @Test
    void unfrozenClientRegistryIsRejectedEvenWhenCommonRegistriesAreFrozen() {
        Fixture fixture = new Fixture();
        fixture.anchors.freeze();
        fixture.tracks.freeze();
        fixture.samples.freeze();
        DocumentRegistries registries = new DocumentRegistries(
                fixture.anchors, fixture.tracks, fixture.samples);

        assertThrows(IllegalStateException.class,
                () -> new CoreTypeCapabilityService(registries,
                        fixture.anchorResolvers, fixture.sampleCollectors));
    }

    @Test
    void nullTypeIdIsRejected() {
        CoreTypeCapabilityService service = new Fixture().freeze();

        assertThrows(NullPointerException.class, () -> service.anchor(null));
        assertThrows(NullPointerException.class, () -> service.sample(null));
        assertThrows(NullPointerException.class, () -> service.track(null));
    }

    private static final class Fixture {
        private final FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        private final FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        private final FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        private final FrozenAnchorResolverRegistry anchorResolvers =
                new FrozenAnchorResolverRegistry();
        private final FrozenSampleCollectorRegistry sampleCollectors =
                new FrozenSampleCollectorRegistry();

        CoreTypeCapabilityService freeze() {
            anchors.freeze();
            tracks.freeze();
            samples.freeze();
            anchorResolvers.freeze();
            sampleCollectors.freeze();

            return new CoreTypeCapabilityService(new DocumentRegistries(anchors, tracks, samples),
                    anchorResolvers, sampleCollectors);
        }
    }

    private record TestAnchor(Identifier type) implements Anchor {
    }

    private record TestAnchorType() implements AnchorType<TestAnchor> {

        @Override
        public Identifier id() {
            return ANCHOR_ID;
        }

        @Override
        public MapCodec<TestAnchor> codec() {
            return MapCodec.unit(new TestAnchor(ANCHOR_ID));
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, TestAnchor> streamCodec() {
            return StreamCodec.unit(new TestAnchor(ANCHOR_ID));
        }
    }

    private record TestAnchorResolver(Identifier typeId) implements AnchorResolver {

        @Override
        public SpatialResolution resolve(Anchor anchor, AnchorResolutionContext context) {
            return new SpatialResolution.NotRequired();
        }
    }

    private record TestSampleArgs(Identifier type) implements SampleArgs {
    }

    private record TestSampleType() implements SampleType<TestSampleArgs> {

        @Override
        public Identifier id() {
            return SAMPLE_ID;
        }

        @Override
        public MapCodec<TestSampleArgs> codec() {
            return MapCodec.unit(new TestSampleArgs(SAMPLE_ID));
        }
    }

    private record TestSampleCollector(Identifier typeId) implements SampleCollector {

        @Override
        public SampleReading collect(SampleArgs args, SampleCollectionContext context) {
            return new SampleReading.Unknown(SampleUnknownReason.TARGET_NOT_FOUND, 0L);
        }
    }

    private record FakeTrack(TrackId id, String name) implements AnimationTrack.ExtensionTrack {

        private static final Identifier TYPE = Identifier.fromNamespaceAndPath("test", "fake_track");

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

    private record FakeTrackType() implements TrackType<FakeTrack> {

        @Override
        public Identifier id() {
            return FakeTrack.TYPE;
        }

        @Override
        public MapCodec<FakeTrack> codec() {
            return MapCodec.unit(new FakeTrack(new TrackId("fake"), "fake"));
        }
    }
}
