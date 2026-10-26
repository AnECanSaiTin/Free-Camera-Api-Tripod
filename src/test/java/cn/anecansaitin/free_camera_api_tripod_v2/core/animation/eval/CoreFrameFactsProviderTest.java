package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleArgs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleCollectionErrorReason;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleUnknownReason;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.WorldSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsPlan;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolutionContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolver;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolverRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollectionContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollector;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollectorRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 第一版 [CoreFrameFactsProvider] 的行为：逐项空间计划、解析失败、采样状态、重采缓存、
/// 预算、原快照引用与诊断作用域。
///
/// 解析器 / 采集器都是测试替身；世界读数由替身按上下文构造，不依赖真实客户端世界。
class CoreFrameFactsProviderTest {

    private static final Identifier DIMENSION =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier ANCHOR_TYPE =
            Identifier.fromNamespaceAndPath("test", "anchor");
    private static final Identifier TARGET_TYPE =
            Identifier.fromNamespaceAndPath("test", "target");

    private static final Anchor ANCHOR = new TestAnchor(ANCHOR_TYPE);
    private static final AimTarget TARGET = AimTarget.lookAt(new TestAnchor(TARGET_TYPE));
    private static final Vec3 POSITION = new Vec3(1, 2, 3);

    private static final RigId RIG = new RigId("rig");
    private static final RigId RIG_A = new RigId("rig_a");
    private static final RigId RIG_B = new RigId("rig_b");
    private static final RigId RIG_C = new RigId("rig_c");

    private static final long WORLD = 7L;
    private static final long DEFAULT_QUOTA = 1_000L;
    private static final int MAX_DEPTH = 16;

    private static final AnchorResolverRegistry NO_ANCHORS =
            new TestAnchorResolverRegistry(Map.of());
    private static final SampleCollectorRegistry NO_COLLECTORS =
            new TestSampleCollectorRegistry(Map.of());

    // region 计划与解析

    @Test
    void preparesOnlyPlannedRigsAndKeepsEffectiveAimVerbatim() {
        RigId anchorRig = new RigId("anchor_rig");
        RigId aimingRig = new RigId("aiming_rig");
        RigId unplanned = new RigId("unplanned");

        AimState effectiveAim = AimState.target(TARGET);
        FrameFactsPlan plan = new FrameFactsPlan(Map.of(
                anchorRig, FrameFactsPlan.RigDemand.anchored(ANCHOR, effectiveAim),
                aimingRig, FrameFactsPlan.RigDemand.aiming(AimState.off())));

        AnimationSnapshot snapshot = snapshot(3,
                List.of(rig(anchorRig), rig(aimingRig), rig(unplanned)),
                List.<WorldSample>of());

        AtomicInteger calls = new AtomicInteger();
        AnchorResolverRegistry resolvers = anchors(resolver(ANCHOR_TYPE, (anchor, context) -> {
            calls.incrementAndGet();
            return resolved(anchor);
        }));

        FrameFacts facts = new CoreFrameFactsProvider(resolvers, NO_COLLECTORS)
                .prepare(request(snapshot, plan, 10L, DEFAULT_QUOTA));

        assertEquals(Set.of(anchorRig, aimingRig), facts.rigs().keySet());
        assertTrue(facts.rig(unplanned).isEmpty());

        FrameFacts.RigFacts anchorFacts = facts.rig(anchorRig).orElseThrow();
        assertTrue(anchorFacts.anchor() instanceof SpatialResolution.Resolved);
        assertTrue(anchorFacts.target() instanceof SpatialResolution.NotRequired);
        assertSame(effectiveAim, anchorFacts.aim());

        FrameFacts.RigFacts aimingFacts = facts.rig(aimingRig).orElseThrow();
        assertTrue(aimingFacts.anchor() instanceof SpatialResolution.NotRequired);
        assertTrue(aimingFacts.target() instanceof SpatialResolution.NotRequired);
        assertEquals(AimState.off(), aimingFacts.aim());

        // 目标需求是 NotRequired：即使 aim 里有目标，也不能额外解析它。
        assertEquals(1, calls.get());
        assertTrue(facts.valid());
        assertEquals(0L, facts.sampleTableVersion());

        assertEquals(DIMENSION, facts.dimension());
        assertEquals(WORLD, facts.worldInstanceId());
        assertEquals(10L, facts.gameTick());
    }

    @Test
    void missingResolversFailEachRequiredDemandAndInvalidateFacts() {
        Anchor missingAnchor = new TestAnchor(kind("missing_anchor"));
        AimTarget missingTarget = AimTarget.lookAt(new TestAnchor(kind("missing_target")));

        FrameFactsPlan plan = new FrameFactsPlan(Map.of(RIG,
                FrameFactsPlan.RigDemand.anchoredAndAiming(
                        missingAnchor, missingTarget, AimState.target(missingTarget))));

        AnimationSnapshot snapshot = snapshot(7, List.of(rig(RIG)), List.<WorldSample>of());

        FrameFacts facts = new CoreFrameFactsProvider(NO_ANCHORS, NO_COLLECTORS)
                .prepare(request(snapshot, plan, 10L, DEFAULT_QUOTA));

        FrameFacts.RigFacts rigFacts = facts.rig(RIG).orElseThrow();
        assertFailed(rigFacts.anchor(), CoreFrameFactsProvider.SPATIAL_RESOLVER_MISSING, 7L);
        assertFailed(rigFacts.target(), CoreFrameFactsProvider.SPATIAL_RESOLVER_MISSING, 7L);
        assertFalse(facts.valid());
    }

    @Test
    void throwingAndMismatchedResolversFailWithoutEscaping() {
        Identifier mismatchType = kind("mismatch");
        Identifier otherType = kind("other");
        Identifier throwingType = kind("throwing");
        Identifier throwingTargetType = kind("throwing_target");

        Map<Identifier, AnchorResolver> entries = new LinkedHashMap<>();
        entries.put(mismatchType, resolver(otherType, (anchor, context) -> resolved(anchor)));
        entries.put(throwingType, resolver(throwingType, (anchor, context) -> {
            throw new IllegalStateException("boom");
        }));
        entries.put(throwingTargetType, resolver(throwingTargetType, (anchor, context) -> {
            throw new IllegalStateException("boom");
        }));

        Anchor mismatchAnchor = new TestAnchor(mismatchType);
        Anchor throwingAnchor = new TestAnchor(throwingType);
        AimTarget throwingTarget = AimTarget.lookAt(new TestAnchor(throwingTargetType));

        FrameFactsPlan plan = new FrameFactsPlan(Map.of(
                RIG_A, FrameFactsPlan.RigDemand.anchored(mismatchAnchor, AimState.off()),
                RIG_B, FrameFactsPlan.RigDemand.anchoredAndAiming(
                        throwingAnchor, throwingTarget, AimState.target(throwingTarget))));

        AnimationSnapshot snapshot = snapshot(9, List.of(rig(RIG_A), rig(RIG_B)), List.<WorldSample>of());

        FrameFacts facts = new CoreFrameFactsProvider(new TestAnchorResolverRegistry(entries), NO_COLLECTORS)
                .prepare(request(snapshot, plan, 10L, DEFAULT_QUOTA));

        assertFailed(facts.rig(RIG_A).orElseThrow().anchor(),
                CoreFrameFactsProvider.SPATIAL_RESOLVER_TYPE_MISMATCH, 9L);
        assertFailed(facts.rig(RIG_B).orElseThrow().anchor(),
                CoreFrameFactsProvider.SPATIAL_RESOLVER_THREW, 9L);
        assertFailed(facts.rig(RIG_B).orElseThrow().target(),
                CoreFrameFactsProvider.SPATIAL_RESOLVER_THREW, 9L);
        assertFalse(facts.valid());
    }

    @Test
    void illegalResolverResultsBecomeSnapshotScopedFailures() {
        Identifier nullType = kind("null_result");
        Identifier notRequiredType = kind("not_required_result");
        Identifier wrongScopeType = kind("wrong_scope_result");

        Map<Identifier, AnchorResolver> entries = new LinkedHashMap<>();
        entries.put(nullType, resolver(nullType, (anchor, context) -> null));
        entries.put(notRequiredType, resolver(notRequiredType,
                (anchor, context) -> new SpatialResolution.NotRequired()));
        entries.put(wrongScopeType, resolver(wrongScopeType, (anchor, context) ->
                SpatialResolution.failed(new DiagnosticScope.Document(new Revision(99)),
                        DiagnosticCode.BROKEN_REFERENCE, LocalizedMessage.literal("wrong snapshot"))));

        FrameFactsPlan plan = new FrameFactsPlan(Map.of(
                RIG_A, FrameFactsPlan.RigDemand.anchored(new TestAnchor(nullType), AimState.off()),
                RIG_B, FrameFactsPlan.RigDemand.anchored(new TestAnchor(notRequiredType), AimState.off()),
                RIG_C, FrameFactsPlan.RigDemand.anchored(new TestAnchor(wrongScopeType), AimState.off())));

        AnimationSnapshot snapshot = snapshot(5,
                List.of(rig(RIG_A), rig(RIG_B), rig(RIG_C)), List.<WorldSample>of());

        FrameFacts facts = new CoreFrameFactsProvider(new TestAnchorResolverRegistry(entries), NO_COLLECTORS)
                .prepare(request(snapshot, plan, 10L, DEFAULT_QUOTA));

        assertFailed(facts.rig(RIG_A).orElseThrow().anchor(),
                CoreFrameFactsProvider.SPATIAL_RESOLVER_INVALID_RESULT, 5L);
        assertFailed(facts.rig(RIG_B).orElseThrow().anchor(),
                CoreFrameFactsProvider.SPATIAL_RESOLVER_INVALID_RESULT, 5L);
        assertFailed(facts.rig(RIG_C).orElseThrow().anchor(),
                CoreFrameFactsProvider.SPATIAL_RESOLVER_INVALID_RESULT, 5L);
        assertFalse(facts.valid());
    }

    // endregion

    // region 采样状态

    @Test
    void samplesProvideValueAndUnknownWithoutInvalidatingFacts() {
        Identifier valueKind = kind("value_sample");
        Identifier unknownKind = kind("unknown_sample");
        WorldSample valueSample = sample("value", valueKind, 1f, 1);
        WorldSample unknownSample = sample("unknown", unknownKind, 2f, 1);

        SampleCollectorRegistry collectors = collectors(
                collector(valueKind, (args, context) ->
                        new SampleReading.Value(42.5f, context.gameTick())),
                collector(unknownKind, (args, context) ->
                        new SampleReading.Unknown(SampleUnknownReason.CHUNK_NOT_LOADED, context.gameTick())));

        AnimationSnapshot snapshot = snapshotWithSamples(4, List.of(valueSample, unknownSample));
        FrameFacts facts = new CoreFrameFactsProvider(NO_ANCHORS, collectors)
                .prepare(request(snapshot, FrameFactsPlan.empty(), 12L, DEFAULT_QUOTA));

        assertEquals(new SampleReading.Value(42.5f, 12L), reading(facts, valueSample));
        assertEquals(new SampleReading.Unknown(SampleUnknownReason.CHUNK_NOT_LOADED, 12L),
                reading(facts, unknownSample));
        assertTrue(facts.valid());
        assertEquals(1L, facts.sampleTableVersion());
        assertTrue(facts.rigs().isEmpty());
    }

    @Test
    void sampleCollectionErrorsInvalidateFactsAndNeverFallback() {
        Identifier throwingKind = kind("throwing_sample");
        Identifier nullKind = kind("null_sample");
        Identifier staleKind = kind("stale_sample");
        Identifier missingKind = kind("missing_collector");

        WorldSample throwingSample = sample("throwing", throwingKind, 0f, 1);
        WorldSample nullSample = sample("null", nullKind, 0f, 1);
        WorldSample staleSample = sample("stale", staleKind, 0f, 1);
        WorldSample missingSample = sample("missing", missingKind, 0f, 1);

        SampleCollectorRegistry collectors = collectors(
                collector(throwingKind, (args, context) -> {
                    throw new IllegalStateException("boom");
                }),
                collector(nullKind, (args, context) -> null),
                collector(staleKind, (args, context) ->
                        new SampleReading.Value(1f, context.gameTick() + 1)));

        AnimationSnapshot snapshot = snapshotWithSamples(6,
                List.of(throwingSample, nullSample, staleSample, missingSample));
        FrameFacts facts = new CoreFrameFactsProvider(NO_ANCHORS, collectors)
                .prepare(request(snapshot, FrameFactsPlan.empty(), 20L, DEFAULT_QUOTA));

        assertCollectionError(reading(facts, throwingSample),
                SampleCollectionErrorReason.COLLECTOR_THREW, 20L);
        assertCollectionError(reading(facts, nullSample),
                SampleCollectionErrorReason.ILLEGAL_OUTPUT, 20L);
        assertCollectionError(reading(facts, staleSample),
                SampleCollectionErrorReason.ILLEGAL_OUTPUT, 20L);
        assertCollectionError(reading(facts, missingSample),
                SampleCollectionErrorReason.COLLECTOR_MISSING, 20L);
        assertFalse(facts.valid());
    }

    @Test
    void recollectionFailureDoesNotReusePreviousValue() {
        Identifier sampleKind = kind("recollect_failure");
        WorldSample sample = sample("recollect", sampleKind, 0f, 1);
        AtomicBoolean fail = new AtomicBoolean();

        SampleCollectorRegistry collectors = collectors(collector(sampleKind, (args, context) -> {
            if (fail.get()) {
                throw new IllegalStateException("transient");
            }

            return new SampleReading.Value(10f, context.gameTick());
        }));

        AnimationSnapshot snapshot = snapshotWithSamples(1, List.of(sample));
        CoreFrameFactsProvider provider = new CoreFrameFactsProvider(NO_ANCHORS, collectors);

        FrameFacts first = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 10L, DEFAULT_QUOTA));
        assertEquals(new SampleReading.Value(10f, 10L), reading(first, sample));
        assertTrue(first.valid());

        fail.set(true);
        FrameFacts second = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 11L, DEFAULT_QUOTA));

        // 到期重采失败：写采集错误，不能把上一周期成功值伪装成本帧读数。
        assertCollectionError(reading(second, sample),
                SampleCollectionErrorReason.COLLECTOR_THREW, 11L);
        assertFalse(second.valid());
    }

    // endregion

    // region 重采节奏与表版本

    @Test
    void resamplePeriodReusesCachedReadingWithinCycle() {
        Identifier sampleKind = kind("period_two");
        WorldSample sample = sample("period_two", sampleKind, 0f, 2);
        AtomicInteger calls = new AtomicInteger();

        SampleCollectorRegistry collectors = collectors(collector(sampleKind, (args, context) ->
                new SampleReading.Value(calls.incrementAndGet(), context.gameTick())));

        AnimationSnapshot snapshot = snapshotWithSamples(1, List.of(sample));
        CoreFrameFactsProvider provider = new CoreFrameFactsProvider(NO_ANCHORS, collectors);

        FrameFactsRequest firstRequest = request(snapshot, FrameFactsPlan.empty(), 10L, DEFAULT_QUOTA);
        FrameFacts first = provider.prepare(firstRequest);
        assertEquals(new SampleReading.Value(1f, 10L), reading(first, sample));
        assertEquals(1, calls.get());
        assertEquals(1L, first.sampleTableVersion());
        assertTrue(first.valid());

        // 同游戏刻重复准备：复用读数，不重复计费，也不重复采集。
        FrameFactsRequest sameTickRequest = request(snapshot, FrameFactsPlan.empty(), 10L, 1L);
        FrameFacts sameTick = provider.prepare(sameTickRequest);
        assertEquals(new SampleReading.Value(1f, 10L), reading(sameTick, sample));
        assertEquals(1, calls.get());
        assertEquals(0L, sameTickRequest.session().consumedWork());
        assertEquals(1L, sameTick.sampleTableVersion());
        assertTrue(sameTick.valid());

        // 周期 2：tick 11 仍复用，tick 12 到期重采。
        FrameFacts secondTick = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 11L, DEFAULT_QUOTA));
        assertEquals(new SampleReading.Value(1f, 10L), reading(secondTick, sample));
        assertEquals(1, calls.get());
        assertEquals(1L, secondTick.sampleTableVersion());

        FrameFacts thirdTick = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 12L, DEFAULT_QUOTA));
        assertEquals(new SampleReading.Value(2f, 12L), reading(thirdTick, sample));
        assertEquals(2, calls.get());
        assertEquals(2L, thirdTick.sampleTableVersion());

        FrameFacts fourthTick = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 13L, DEFAULT_QUOTA));
        assertEquals(new SampleReading.Value(2f, 12L), reading(fourthTick, sample));
        assertEquals(2, calls.get());
        assertEquals(2L, fourthTick.sampleTableVersion());
    }

    @Test
    void everyTickSampleIsCollectedOncePerGameTick() {
        Identifier sampleKind = kind("period_one");
        WorldSample sample = sample("period_one", sampleKind, 0f, 1);
        AtomicInteger calls = new AtomicInteger();

        SampleCollectorRegistry collectors = collectors(collector(sampleKind, (args, context) ->
                new SampleReading.Value(calls.incrementAndGet(), context.gameTick())));

        AnimationSnapshot snapshot = snapshotWithSamples(1, List.of(sample));
        CoreFrameFactsProvider provider = new CoreFrameFactsProvider(NO_ANCHORS, collectors);

        assertEquals(new SampleReading.Value(1f, 20L),
                reading(provider.prepare(request(snapshot, FrameFactsPlan.empty(), 20L, DEFAULT_QUOTA)), sample));
        assertEquals(new SampleReading.Value(1f, 20L),
                reading(provider.prepare(request(snapshot, FrameFactsPlan.empty(), 20L, DEFAULT_QUOTA)), sample));
        assertEquals(1, calls.get());

        assertEquals(new SampleReading.Value(2f, 21L),
                reading(provider.prepare(request(snapshot, FrameFactsPlan.empty(), 21L, DEFAULT_QUOTA)), sample));
        assertEquals(2, calls.get());
    }

    @Test
    void changedParametersOrPeriodForceRecollection() {
        Identifier sampleKind = kind("parameter_change");
        AtomicInteger calls = new AtomicInteger();

        SampleCollectorRegistry collectors = collectors(collector(sampleKind, (args, context) -> {
            calls.incrementAndGet();
            return new SampleReading.Value(((TestArgs) args).value(), context.gameTick());
        }));

        WorldSample firstSample = sample("parameter", sampleKind, 1f, 1);
        WorldSample secondSample = sample("parameter", sampleKind, 2f, 1);
        WorldSample thirdSample = sample("parameter", sampleKind, 2f, 2);

        AnimationSnapshot firstSnapshot = snapshotWithSamples(1, List.of(firstSample));
        AnimationSnapshot secondSnapshot = snapshotWithSamples(2, List.of(secondSample));
        AnimationSnapshot thirdSnapshot = snapshotWithSamples(3, List.of(thirdSample));

        CoreFrameFactsProvider provider = new CoreFrameFactsProvider(NO_ANCHORS, collectors);

        assertEquals(new SampleReading.Value(1f, 10L),
                reading(provider.prepare(request(firstSnapshot, FrameFactsPlan.empty(), 10L, DEFAULT_QUOTA)),
                        firstSample));
        assertEquals(1, calls.get());

        // 参数变化：即使游戏刻相同也不能复用旧值。
        assertEquals(new SampleReading.Value(2f, 10L),
                reading(provider.prepare(request(secondSnapshot, FrameFactsPlan.empty(), 10L, DEFAULT_QUOTA)),
                        secondSample));
        assertEquals(2, calls.get());

        // 周期变化同样开始新周期；读数与采样时间相同，因此表版本不再增加。
        FrameFacts third = provider.prepare(
                request(thirdSnapshot, FrameFactsPlan.empty(), 10L, DEFAULT_QUOTA));
        assertEquals(new SampleReading.Value(2f, 10L), reading(third, thirdSample));
        assertEquals(3, calls.get());
        assertEquals(2L, third.sampleTableVersion());
    }

    @Test
    void differentWorldInstanceForcesRecollection() {
        Identifier sampleKind = kind("world_change");
        WorldSample sample = sample("world_change", sampleKind, 0f, 100);
        AtomicInteger calls = new AtomicInteger();

        SampleCollectorRegistry collectors = collectors(collector(sampleKind, (args, context) -> {
            calls.incrementAndGet();
            return new SampleReading.Value(5f, context.gameTick());
        }));

        AnimationSnapshot snapshot = snapshotWithSamples(1, List.of(sample));
        CoreFrameFactsProvider provider = new CoreFrameFactsProvider(NO_ANCHORS, collectors);

        FrameFacts first = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), WORLD, 10L, 0.25f, DEFAULT_QUOTA));
        assertEquals(new SampleReading.Value(5f, 10L), reading(first, sample));
        assertEquals(1, calls.get());

        // 同维度新世界实例：缓存身份不同，即使同游戏刻、同周期也必须重采。
        FrameFacts second = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), WORLD + 1, 10L, 0.25f, DEFAULT_QUOTA));
        assertEquals(new SampleReading.Value(5f, 10L), reading(second, sample));
        assertEquals(2, calls.get());

        // 新世界实例内仍在周期 100 内：tick 11 复用。
        FrameFacts third = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), WORLD + 1, 11L, 0.25f, DEFAULT_QUOTA));
        assertEquals(new SampleReading.Value(5f, 10L), reading(third, sample));
        assertEquals(2, calls.get());
    }

    @Test
    void sampleTableVersionOnlyChangesWithReadingsOrTimeMetadata() {
        Identifier sampleKind = kind("versioned");
        WorldSample sample = sample("versioned", sampleKind, 0f, 1);
        AtomicBoolean unknown = new AtomicBoolean();

        SampleCollectorRegistry collectors = collectors(collector(sampleKind, (args, context) ->
                unknown.get()
                        ? new SampleReading.Unknown(SampleUnknownReason.DATA_UNAVAILABLE, context.gameTick())
                        : new SampleReading.Value(5f, context.gameTick())));

        AnimationSnapshot snapshot = snapshotWithSamples(1, List.of(sample));
        CoreFrameFactsProvider provider = new CoreFrameFactsProvider(NO_ANCHORS, collectors);

        FrameFacts first = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 10L, DEFAULT_QUOTA));
        assertEquals(1L, first.sampleTableVersion());

        FrameFacts same = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 10L, DEFAULT_QUOTA));
        assertEquals(1L, same.sampleTableVersion());

        // 同一数值但采样时间变了：表版本递增。
        FrameFacts nextTick = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 11L, DEFAULT_QUOTA));
        assertEquals(2L, nextTick.sampleTableVersion());

        FrameFacts repeated = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 11L, DEFAULT_QUOTA));
        assertEquals(2L, repeated.sampleTableVersion());

        // 有效性 / 状态变化（VALUE -> UNKNOWN）也递增；UNKNOWN 本身不使帧无效。
        unknown.set(true);
        FrameFacts unknownFacts = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 12L, DEFAULT_QUOTA));
        assertEquals(new SampleReading.Unknown(SampleUnknownReason.DATA_UNAVAILABLE, 12L),
                reading(unknownFacts, sample));
        assertEquals(3L, unknownFacts.sampleTableVersion());
        assertTrue(unknownFacts.valid());

        FrameFacts unknownAgain = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 12L, DEFAULT_QUOTA));
        assertEquals(3L, unknownAgain.sampleTableVersion());
    }

    // endregion

    // region 预算

    @Test
    void workBudgetIsChargedPerResolutionAndCollection() {
        WorldSample firstSample = sample("budget_a", kind("budget_a"), 0f, 1);
        WorldSample secondSample = sample("budget_b", kind("budget_b"), 0f, 1);
        FrameFactsPlan plan = fullPlan();
        AnimationSnapshot snapshot = snapshot(
                4, List.of(rig(RIG)), List.of(firstSample, secondSample));
        CoreFrameFactsProvider provider = new CoreFrameFactsProvider(
                anchors(resolvedResolver(ANCHOR_TYPE), resolvedResolver(TARGET_TYPE)),
                collectors(valueCollector(firstSample.kind()), valueCollector(secondSample.kind())));

        FrameFactsRequest firstRequest = request(snapshot, plan, 10L, DEFAULT_QUOTA);
        FrameFacts first = provider.prepare(firstRequest);
        assertTrue(first.valid());
        assertEquals(4L, firstRequest.session().consumedWork());

        // 同游戏刻：两次解析仍收费，两次采样缓存命中不收费。
        FrameFactsRequest secondRequest = request(snapshot, plan, 10L, DEFAULT_QUOTA);
        assertTrue(provider.prepare(secondRequest).valid());
        assertEquals(2L, secondRequest.session().consumedWork());
    }

    @Test
    void insufficientBudgetFailsItemsAndRetriesWithNextSession() {
        WorldSample firstSample = sample("starved_a", kind("starved_a"), 0f, 1);
        WorldSample secondSample = sample("starved_b", kind("starved_b"), 0f, 1);
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();

        FrameFactsPlan plan = fullPlan();
        AnimationSnapshot snapshot = snapshot(
                8, List.of(rig(RIG)), List.of(firstSample, secondSample));
        CoreFrameFactsProvider provider = new CoreFrameFactsProvider(
                anchors(resolvedResolver(ANCHOR_TYPE), resolvedResolver(TARGET_TYPE)),
                collectors(
                        collector(firstSample.kind(), (args, context) -> {
                            firstCalls.incrementAndGet();
                            return new SampleReading.Value(1f, context.gameTick());
                        }),
                        collector(secondSample.kind(), (args, context) -> {
                            secondCalls.incrementAndGet();
                            return new SampleReading.Value(2f, context.gameTick());
                        })));

        // 额度 1：只有第一项锚点解析能完成；目标与两次采样逐项失败，不抛异常。
        FrameFactsRequest starvedRequest = request(snapshot, plan, 10L, 1L);
        FrameFacts starved = provider.prepare(starvedRequest);

        FrameFacts.RigFacts rigFacts = starved.rig(RIG).orElseThrow();
        assertTrue(rigFacts.anchor() instanceof SpatialResolution.Resolved);
        assertFailed(rigFacts.target(),
                CoreFrameFactsProvider.SPATIAL_RESOLUTION_BUDGET_EXCEEDED, 8L);
        assertCollectionError(reading(starved, firstSample),
                SampleCollectionErrorReason.BUDGET_EXCEEDED, 10L);
        assertCollectionError(reading(starved, secondSample),
                SampleCollectionErrorReason.BUDGET_EXCEEDED, 10L);

        assertFalse(starved.valid());
        assertEquals(1L, starvedRequest.session().consumedWork());
        assertEquals(0, firstCalls.get());
        assertEquals(0, secondCalls.get());

        // 预算失败不进入实例缓存：下一帧新会话可以完整重试并成功。
        FrameFactsRequest retryRequest = request(snapshot, plan, 10L, DEFAULT_QUOTA);
        FrameFacts retried = provider.prepare(retryRequest);

        assertTrue(retried.valid());
        assertEquals(new SampleReading.Value(1f, 10L), reading(retried, firstSample));
        assertEquals(new SampleReading.Value(2f, 10L), reading(retried, secondSample));
        assertEquals(1, firstCalls.get());
        assertEquals(1, secondCalls.get());
        assertEquals(4L, retryRequest.session().consumedWork());
    }

    // endregion

    // region 来源、诊断作用域与上下文

    @Test
    void factsKeepExactSourceSnapshotReference() {
        AnimationSnapshot snapshot = snapshot(1, List.of(rig(RIG)), List.<WorldSample>of());
        AnimationSnapshot sameRevisionTwin =
                new AnimationSnapshot(snapshot.revision(), snapshot.document());

        CoreFrameFactsProvider provider = new CoreFrameFactsProvider(NO_ANCHORS, NO_COLLECTORS);

        FrameFacts first = provider.prepare(
                request(snapshot, FrameFactsPlan.empty(), 1L, DEFAULT_QUOTA));
        FrameFacts second = provider.prepare(
                request(sameRevisionTwin, FrameFactsPlan.empty(), 1L, DEFAULT_QUOTA));

        assertSame(snapshot, first.sourceSnapshot());
        assertSame(sameRevisionTwin, second.sourceSnapshot());
        assertNotSame(first.sourceSnapshot(), second.sourceSnapshot());
        assertEquals(snapshot.revision(), second.sourceRevision());
    }

    @Test
    void anchorContextAndFailuresUseRequestSnapshotRevision() {
        AtomicReference<AnchorResolutionContext> seen = new AtomicReference<>();
        AnchorResolver resolver = resolver(ANCHOR_TYPE, (anchor, context) -> {
            seen.set(context);
            return SpatialResolution.failed(context.diagnosticScope(),
                    DiagnosticCode.BROKEN_REFERENCE,
                    LocalizedMessage.literal("entity not loaded"));
        });

        FrameFactsPlan plan = new FrameFactsPlan(Map.of(RIG,
                FrameFactsPlan.RigDemand.anchored(ANCHOR, AimState.off())));
        AnimationSnapshot snapshot = snapshot(11, List.of(rig(RIG)), List.<WorldSample>of());
        FrameFactsRequest request = request(snapshot, plan, 9L, 10L, 0.75f, DEFAULT_QUOTA);

        FrameFacts facts = new CoreFrameFactsProvider(anchors(resolver), NO_COLLECTORS)
                .prepare(request);

        assertFailed(facts.rig(RIG).orElseThrow().anchor(), DiagnosticCode.BROKEN_REFERENCE, 11L);
        assertFalse(facts.valid());

        AnchorResolutionContext context = seen.get();
        assertNotNull(context);
        assertEquals(DIMENSION, context.dimension());
        assertEquals(9L, context.worldInstanceId());
        assertEquals(0.75f, context.partialTick(), 0.0f);
        assertEquals(11L, context.diagnosticScope().revision().value());
        assertTrue(context.entity(UUID.randomUUID()).isEmpty());
        assertTrue(context.player(UUID.randomUUID()).isEmpty());
    }

    @Test
    void sampleContextCarriesFrameMetadataAndSharedSession() {
        Identifier sampleKind = kind("context_sample");
        WorldSample sample = sample("context", sampleKind, 0f, 1);
        AtomicReference<SampleCollectionContext> seen = new AtomicReference<>();

        SampleCollectorRegistry collectors = collectors(collector(sampleKind, (args, context) -> {
            seen.set(context);
            return new SampleReading.Value(7f, context.gameTick());
        }));

        AnimationSnapshot snapshot = snapshotWithSamples(2, List.of(sample));
        FrameFactsRequest request = request(
                snapshot, FrameFactsPlan.empty(), 9L, 30L, 0.5f, DEFAULT_QUOTA);

        FrameFacts facts = new CoreFrameFactsProvider(NO_ANCHORS, collectors).prepare(request);
        assertTrue(facts.valid());

        SampleCollectionContext context = seen.get();
        assertNotNull(context);
        assertEquals(DIMENSION, context.dimension());
        assertEquals(9L, context.worldInstanceId());
        assertEquals(30L, context.gameTick());
        assertSame(request.session(), context.session());
        assertFalse(context.chunkLoaded(0, 0));
        assertTrue(context.blockState(0, 0, 0).isEmpty());
        assertTrue(context.lightLevel(0, 0, 0).isEmpty());
        assertTrue(context.entityAttribute(UUID.randomUUID(), kind("attribute")).isEmpty());
    }

    @Test
    void constructorRequiresRegistries() {
        assertThrows(NullPointerException.class,
                () -> new CoreFrameFactsProvider(null, NO_COLLECTORS));
        assertThrows(NullPointerException.class,
                () -> new CoreFrameFactsProvider(NO_ANCHORS, null));
    }

    // endregion

    // region 辅助

    private static FrameFactsPlan fullPlan() {
        return new FrameFactsPlan(Map.of(RIG,
                FrameFactsPlan.RigDemand.anchoredAndAiming(
                        ANCHOR, TARGET, AimState.target(TARGET))));
    }

    private static AnimationSnapshot snapshot(long revision,
                                              List<CameraRig> rigs,
                                              List<WorldSample> samples) {
        CameraRig first = rigs.getFirst();
        CameraAnimation document = CameraAnimation.create("doc", first, new TrackId("source"))
                .withRigs(rigs)
                .withSamples(samples);

        return new AnimationSnapshot(new Revision(revision), document);
    }

    /// 采样测试的便捷快照：固定一台未被计划引用的机位。
    private static AnimationSnapshot snapshotWithSamples(long revision, List<WorldSample> samples) {
        return snapshot(revision, List.of(rig(RIG)), samples);
    }

    private static CameraRig rig(RigId id) {
        return CameraRig.create(id, id.value(), List.of(),
                new TrackId(id.value() + "_aim"), new TrackId(id.value() + "_active"));
    }

    private static FrameFactsRequest request(AnimationSnapshot snapshot,
                                             FrameFactsPlan plan,
                                             long gameTick,
                                             long quota) {
        return request(snapshot, plan, WORLD, gameTick, 0.25f, quota);
    }

    private static FrameFactsRequest request(AnimationSnapshot snapshot,
                                             FrameFactsPlan plan,
                                             long worldInstanceId,
                                             long gameTick,
                                             float partialTick,
                                             long quota) {
        return new FrameFactsRequest(snapshot, 0f, DIMENSION, worldInstanceId, gameTick,
                partialTick,
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime(12000, 24000),
                plan, EvaluationSession.create(quota, MAX_DEPTH));
    }

    private static WorldSample sample(String name, Identifier kind, float argument, int resampleEveryTicks) {
        return new WorldSample(new SampleId(name), name, kind,
                new TestArgs(kind, argument), resampleEveryTicks);
    }

    private static SampleReading reading(FrameFacts facts, WorldSample sample) {
        return facts.sample(sample.id()).orElseThrow();
    }

    private static void assertFailed(SpatialResolution resolution, DiagnosticCode code, long revision) {
        assertTrue(resolution instanceof SpatialResolution.Failed,
                "expected Failed but was " + resolution);

        Diagnostic diagnostic = ((SpatialResolution.Failed) resolution).diagnostic();
        assertEquals(code, diagnostic.code());
        assertEquals(Severity.ERROR, diagnostic.severity());
        assertTrue(diagnostic.scope() instanceof DiagnosticScope.Document,
                "failure must use a document scope");

        DiagnosticScope.Document scope = (DiagnosticScope.Document) diagnostic.scope();
        assertEquals(new Revision(revision), scope.revision());
        assertFalse(diagnostic.message().toString().isBlank());
    }

    private static void assertCollectionError(SampleReading reading,
                                              SampleCollectionErrorReason reason,
                                              long sampledAtTick) {
        assertEquals(new SampleReading.CollectionError(reason, sampledAtTick), reading);
    }

    private static Identifier kind(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }

    private static SpatialResolution resolved(Anchor anchor) {
        return new SpatialResolution.Resolved(ResolvedAnchor.at(anchor, POSITION));
    }

    private static AnchorResolver resolvedResolver(Identifier typeId) {
        return resolver(typeId, (anchor, context) -> resolved(anchor));
    }

    private static AnchorResolver resolver(Identifier typeId, ResolutionBehavior behavior) {
        return new TestResolver(typeId, behavior);
    }

    private static SampleCollector valueCollector(Identifier typeId) {
        return collector(typeId, (args, context) ->
                new SampleReading.Value(1f, context.gameTick()));
    }

    private static SampleCollector collector(Identifier typeId, CollectionBehavior behavior) {
        return new TestCollector(typeId, behavior);
    }

    private static AnchorResolverRegistry anchors(AnchorResolver... resolvers) {
        Map<Identifier, AnchorResolver> entries = new LinkedHashMap<>();

        for (AnchorResolver resolver : resolvers) {
            entries.put(resolver.typeId(), resolver);
        }

        return new TestAnchorResolverRegistry(entries);
    }

    private static SampleCollectorRegistry collectors(SampleCollector... collectors) {
        Map<Identifier, SampleCollector> entries = new LinkedHashMap<>();

        for (SampleCollector collector : collectors) {
            entries.put(collector.typeId(), collector);
        }

        return new TestSampleCollectorRegistry(entries);
    }

    private record TestAnchor(Identifier type) implements Anchor {
    }

    private record TestArgs(Identifier type, float value) implements SampleArgs {
    }

    @FunctionalInterface
    private interface ResolutionBehavior {
        SpatialResolution resolve(Anchor anchor, AnchorResolutionContext context);
    }

    private record TestResolver(Identifier typeId, ResolutionBehavior behavior) implements AnchorResolver {

        @Override
        public SpatialResolution resolve(Anchor anchor, AnchorResolutionContext context) {
            return behavior.resolve(anchor, context);
        }
    }

    @FunctionalInterface
    private interface CollectionBehavior {
        SampleReading collect(SampleArgs args, SampleCollectionContext context);
    }

    private record TestCollector(Identifier typeId, CollectionBehavior behavior) implements SampleCollector {

        @Override
        public SampleReading collect(SampleArgs args, SampleCollectionContext context) {
            return behavior.collect(args, context);
        }
    }

    /// 测试用只读注册表；`register` 明确拒绝，等价于“启动后已冻结”。
    private static class TestRegistry<T> implements TypeRegistry<T> {

        private final Map<Identifier, T> entries;

        private TestRegistry(Map<Identifier, T> entries) {
            this.entries = Map.copyOf(entries);
        }

        @Override
        public Optional<T> find(Identifier id) {
            return Optional.ofNullable(entries.get(id));
        }

        @Override
        public Set<Identifier> ids() {
            return entries.keySet();
        }

        @Override
        public void register(T type) {
            throw new UnsupportedOperationException("test registry is frozen");
        }

        @Override
        public void freeze() {
            // 已冻结；幂等空操作。
        }

        @Override
        public boolean frozen() {
            return true;
        }
    }

    private static final class TestAnchorResolverRegistry extends TestRegistry<AnchorResolver>
            implements AnchorResolverRegistry {

        private TestAnchorResolverRegistry(Map<Identifier, AnchorResolver> entries) {
            super(entries);
        }
    }

    private static final class TestSampleCollectorRegistry extends TestRegistry<SampleCollector>
            implements SampleCollectorRegistry {

        private TestSampleCollectorRegistry(Map<Identifier, SampleCollector> entries) {
            super(entries);
        }
    }

    // endregion
}
