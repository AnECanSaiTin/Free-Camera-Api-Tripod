package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleCollectionErrorReason;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleUnknownReason;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 采样端口：VALUE、UNKNOWN + fallback、COLLECTION_ERROR。
class TrackPoolSampleTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void valueAndFallbackAreEvaluated() {
        TrackPoolEvaluator evaluator = new TrackPoolEvaluator();
        TrackPoolAnimation value = document("sample(\"height\") + 1");

        TrackPoolSampleSource source = (name, inputs) -> new SampleReading.Value(5f, 0);
        assertEquals(6, evaluator.evaluate(value,
                TrackPoolEvaluationInputs.at(0f), source, Revision.INITIAL)
                .getFirst().fov().value(), 1e-6);

        TrackPoolAnimation fallback = document("sample(\"height\", 7) + 1");
        TrackPoolSampleSource unavailable = (name, inputs) -> new SampleReading.Unknown(
                SampleUnknownReason.DATA_UNAVAILABLE, 0);
        assertEquals(8, evaluator.evaluate(fallback,
                TrackPoolEvaluationInputs.at(0f), unavailable, Revision.INITIAL)
                .getFirst().fov().value(), 1e-6);
    }

    @Test
    void playbackControllerPassesSampleSourceToFrame() {
        TrackPoolDocumentStore store = new TrackPoolDocumentStore(document("sample(\"height\", 3)"));
        TrackPoolPlaybackController controller = new TrackPoolPlaybackController(
                store, new TrackPoolFrameFactory(),
                (name, inputs) -> new SampleReading.Unknown(SampleUnknownReason.DATA_UNAVAILABLE, 0));

        assertTrue(controller.tick(0f) instanceof
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrameResult.Prepared);
    }

    @Test
    void missingAndCollectionErrorAreErrors() {
        TrackPoolEvaluator evaluator = new TrackPoolEvaluator();
        TrackPoolAnimation document = document("sample(\"height\")");

        var missing = evaluator.evaluate(document,
                TrackPoolEvaluationInputs.at(0f),
                (name, inputs) -> new SampleReading.Unknown(SampleUnknownReason.DATA_NOT_SYNCED, 0),
                Revision.INITIAL);
        assertTrue(missing.getFirst().fov().isError());

        var error = evaluator.evaluate(document,
                TrackPoolEvaluationInputs.at(0f),
                (name, inputs) -> new SampleReading.CollectionError(
                        SampleCollectionErrorReason.COLLECTOR_MISSING, 0),
                Revision.INITIAL);
        assertTrue(error.getFirst().fov().isError());
    }

    private static TrackPoolAnimation document(String formula) {
        RigProperties properties = new RigProperties(
                PositionProperty.disabled(),
                RotationProperty.disabled(),
                new FovProperty(PropertyBinding.enabled(new NumericSource.Formula(formula))),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                ObstacleProperty.disabled());
        return new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "rig").withProperties(properties)),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
    }
}
