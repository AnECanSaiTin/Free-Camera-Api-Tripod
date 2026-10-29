package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.QuaternionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池属性绑定校验：缺失引用、类型不匹配与合法组合。
class TrackPoolValidatorTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void validBindingsHaveNoDiagnostics() {
        TrackId numberId = new TrackId("number");
        TrackId activeId = new TrackId("active");
        TrackId sourceId = new TrackId("source");
        TrackId rotationId = new TrackId("rotation");

        TrackPoolAnimation document = document(
                List.of(CurveTrack.ofCurve(numberId, "number", Curve.EMPTY),
                        ActiveTrack.enabled(activeId),
                        CameraSourceTrack.player(sourceId),
                        new QuaternionTrack(rotationId, "rotation", Quaternion.IDENTITY, List.of())),
                new RigProperties(
                        new PositionProperty(
                                PropertyBinding.enabled(new NumericSource.Track(numberId)),
                                PropertyBinding.disabled(),
                                PropertyBinding.disabled()),
                        new RotationProperty(PropertyBinding.enabled(
                                new QuaternionSource.Track(rotationId))),
                        new FovProperty(PropertyBinding.disabled()),
                        new ActiveProperty(PropertyBinding.enabled(
                                new BooleanSource.Track(activeId))),
                        RectProperty.fullScreen(),
                        ObstacleProperty.disabled()),
                PropertyBinding.enabled(new CameraSourceProperty.Track(sourceId)));

        assertTrue(new TrackPoolValidator().validate(document, Revision.INITIAL).isEmpty());
    }

    @Test
    void missingAndWrongTypeReferencesAreReported() {
        TrackId activeId = new TrackId("active");
        TrackPoolAnimation missing = document(
                List.of(ActiveTrack.enabled(activeId)),
                properties(PropertyBinding.enabled(new NumericSource.Track(new TrackId("missing")))),
                PropertyBinding.disabled());
        var missingDiagnostics = new TrackPoolValidator().validate(missing, Revision.INITIAL);

        assertEquals(1, missingDiagnostics.size());
        assertEquals(DiagnosticCode.BROKEN_REFERENCE, missingDiagnostics.getFirst().code());

        TrackPoolAnimation wrongType = document(
                List.of(ActiveTrack.enabled(activeId)),
                properties(PropertyBinding.enabled(new NumericSource.Track(activeId))),
                PropertyBinding.disabled());
        var wrongDiagnostics = new TrackPoolValidator().validate(wrongType, Revision.INITIAL);

        assertEquals(1, wrongDiagnostics.size());
        assertTrue(wrongDiagnostics.getFirst().message().toString().contains("CurveTrack"));
    }

    @Test
    void oldAimTrackAndReservedExtensionTypeAreRejected() {
        var aim = cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack.off(
                new TrackId("aim"));
        var extension = new AnimationTrack.ExtensionTrack() {
            @Override
            public TrackId id() {
                return new TrackId("ext");
            }

            @Override
            public String name() {
                return "ext";
            }

            @Override
            public net.minecraft.resources.Identifier type() {
                return CurveTrack.TYPE;
            }

            @Override
            public float duration() {
                return 0f;
            }

            @Override
            public int keyCount() {
                return 0;
            }
        };
        TrackPoolAnimation document = document(List.of(aim, extension),
                RigProperties.disabled(), PropertyBinding.disabled());

        var diagnostics = new TrackPoolValidator().validate(document, Revision.INITIAL);

        assertEquals(2, diagnostics.size(), () -> "diagnostics: " + diagnostics);
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().toString().contains("AimTrack")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().toString().contains("built-in")));
    }

    @Test
    void extensionTimelineKeysMustBeStrictlyIncreasingAndUnique() {
        var track = new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack() {
            @Override
            public List<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack.TimelineKey> timelineKeys() {
                return List.of(
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack.TimelineKey(
                                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId("same"), 2f),
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack.TimelineKey(
                                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId("same"), 1f));
            }

            @Override
            public TrackId id() {
                return new TrackId("timeline");
            }

            @Override
            public String name() {
                return "timeline";
            }

            @Override
            public net.minecraft.resources.Identifier type() {
                return net.minecraft.resources.Identifier.fromNamespaceAndPath("test", "timeline");
            }

            @Override
            public float duration() {
                return 2f;
            }

            @Override
            public int keyCount() {
                return 2;
            }
        };

        var diagnostics = new TrackPoolValidator().validate(
                document(List.of(track), RigProperties.disabled(), PropertyBinding.disabled()),
                Revision.INITIAL);

        assertEquals(2, diagnostics.size(), () -> "diagnostics: " + diagnostics);
    }

    @Test
    void duplicateAuxiliaryIdsAreReported() {
        var markerId = new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId("same");
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "rig")), PropertyBinding.disabled(),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols.EMPTY,
                List.of(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker.at(markerId, 1f),
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker.at(markerId, 2f)),
                List.of(), List.of(), List.of(), cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange.AUTO);

        var diagnostics = new TrackPoolValidator().validate(document, Revision.INITIAL);

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().message().toString().contains("Duplicate marker"));
    }

    @Test
    void invalidConstantDomainsAreReported() {
        RigProperties properties = new RigProperties(
                PositionProperty.disabled(),
                RotationProperty.disabled(),
                new FovProperty(PropertyBinding.enabled(new NumericSource.Constant(-1))),
                ActiveProperty.disabled(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty.Normalized(
                        PropertyBinding.disabled(), PropertyBinding.disabled(),
                        PropertyBinding.enabled(new NumericSource.Constant(0)),
                        PropertyBinding.enabled(new NumericSource.Constant(1))),
                new ObstacleProperty(PropertyBinding.disabled(),
                        PropertyBinding.enabled(new NumericSource.Constant(-2))));
        TrackPoolAnimation document = document(List.of(), properties, PropertyBinding.disabled());

        var diagnostics = new TrackPoolValidator().validate(document, Revision.INITIAL);

        assertEquals(3, diagnostics.size(), () -> "diagnostics: " + diagnostics);
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().toString().contains("positive")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().toString().contains("non-negative")));
    }

    @Test
    void invalidPropertyFormulaIsReportedAtValidation() {
        RigProperties properties = new RigProperties(
                PositionProperty.disabled(),
                RotationProperty.disabled(),
                new FovProperty(PropertyBinding.enabled(new NumericSource.Formula("("))),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                ObstacleProperty.disabled());
        TrackPoolAnimation document = document(List.of(), properties, PropertyBinding.disabled());

        var diagnostics = new TrackPoolValidator().validate(document, Revision.INITIAL);

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().message().toString().contains("Formula cannot compile"));
    }

    @Test
    void cameraSourceConstantMustReferenceExistingRig() {
        TrackPoolAnimation document = document(
                List.of(), RigProperties.disabled(),
                PropertyBinding.enabled(new CameraSourceProperty.Constant(
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource.rig(
                                new RigId("missing")))));

        var diagnostics = new TrackPoolValidator().validate(document, Revision.INITIAL);

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().message().toString().contains("missing rig"));
    }

    @Test
    void poolCurveMustNotCarryLegacyCameraChannel() {
        TrackId channel = new TrackId("channel");
        CurveTrack track = CurveTrack.ofChannel(channel, "channel",
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel.POSITION_X,
                Curve.EMPTY);
        TrackPoolAnimation document = document(List.of(track), RigProperties.disabled(),
                PropertyBinding.disabled());

        var diagnostics = new TrackPoolValidator().validate(document, Revision.INITIAL);

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().message().toString().contains("camera channel"));
    }

    @Test
    void cameraSourceBindingRequiresCameraSourceTrack() {
        TrackId activeId = new TrackId("active");
        TrackPoolAnimation document = document(
                List.of(ActiveTrack.enabled(activeId)),
                RigProperties.disabled(),
                PropertyBinding.enabled(new CameraSourceProperty.Track(activeId)));

        var diagnostics = new TrackPoolValidator().validate(document, Revision.INITIAL);

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().message().toString().contains("CameraSourceTrack"));
    }

    private static RigProperties properties(PropertyBinding<NumericSource> x) {
        return new RigProperties(
                new PositionProperty(x, PropertyBinding.disabled(), PropertyBinding.disabled()),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                ObstacleProperty.disabled());
    }

    private static TrackPoolAnimation document(List<AnimationTrack> tracks,
                                               RigProperties properties,
                                               PropertyBinding<CameraSourceProperty> source) {
        PooledRig rig = PooledRig.create(RIG, "rig").withProperties(properties);
        return new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", tracks,
                List.of(rig), source, Symbols.EMPTY, List.of(), List.of(), List.of(), List.of(),
                PlaybackRange.AUTO);
    }
}
