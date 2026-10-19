package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsPlan;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 宿主服务契约的结构不变量（R2 帧事实 / R3 客户端注册 / R4 能力，复核 R49 的两项 P2）。
///
/// 本批仍是接口与数据承载：解析器 / 采集器 / 提供者都没有实现，测试覆盖
/// 能力三态、帧请求的时间与计划、逐项需求、覆盖结果承载、诊断作用域来源与事实版本绑定。
class HostServiceContractTest {

    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier ANCHOR_TYPE =
            Identifier.fromNamespaceAndPath("free_camera_api_tripod", "test_anchor");
    private static final RigId RIG = new RigId("rig");

    private static final Anchor ANCHOR_A = () -> ANCHOR_TYPE;
    private static final Anchor ANCHOR_B = () -> ANCHOR_TYPE;
    private static final AimTarget TARGET_A = AimTarget.lookAt(ANCHOR_A);
    private static final AimTarget TARGET_B = AimTarget.lookAt(ANCHOR_B);

    @Test
    void typeCapabilityKeepsThreeStatesDistinct() {
        Identifier id = Identifier.fromNamespaceAndPath("test", "anchor");

        assertTrue(TypeCapability.available(id).available());
        assertFalse(TypeCapability.typeNotRegistered(id).available());
        assertFalse(TypeCapability.runtimeImplementationMissing(id).available());

        assertEquals(TypeCapabilityStatus.TYPE_NOT_REGISTERED,
                TypeCapability.typeNotRegistered(id).status());
        assertEquals(TypeCapabilityStatus.RUNTIME_IMPLEMENTATION_MISSING,
                TypeCapability.runtimeImplementationMissing(id).status());

        assertThrows(NullPointerException.class, () -> new TypeCapability(null,
                TypeCapabilityStatus.AVAILABLE));
        assertThrows(NullPointerException.class, () -> new TypeCapability(id, null));
    }

    @Test
    void frameFactsPlanSeparatesAnchorFromTargetDemand() {
        // 活动机位只读另一机位的锚点：只要求锚点，不因此要求目标（复核 R49-H1）。
        FrameFactsPlan.RigDemand anchorOnly =
                FrameFactsPlan.RigDemand.anchored(ANCHOR_A, AimState.target(TARGET_A));

        assertTrue(anchorOnly.needsAnchor());
        assertFalse(anchorOnly.needsTarget());
        assertTrue(anchorOnly.target().optionalValue().isEmpty());
        assertEquals(AimState.target(TARGET_A), anchorOnly.aim());

        FrameFactsPlan.RigDemand full =
                FrameFactsPlan.RigDemand.anchoredAndAiming(ANCHOR_A, TARGET_A, AimState.target(TARGET_A));

        assertTrue(full.needsAnchor());
        assertTrue(full.needsTarget());
        assertEquals(Optional.of(TARGET_A), full.target().optionalValue());

        FrameFactsPlan.RigDemand aimingOnly = FrameFactsPlan.RigDemand.aiming(AimState.off());

        assertFalse(aimingOnly.needsAnchor());
        assertFalse(aimingOnly.needsTarget());

        // 需要解析的目标必须是本帧生效的同一个目标，否则会解析一个不会被使用的目标。
        assertThrows(IllegalArgumentException.class, () -> new FrameFactsPlan.RigDemand(
                FrameFactsPlan.SpatialDemand.notRequired(),
                FrameFactsPlan.SpatialDemand.required(TARGET_B),
                AimState.target(TARGET_A)));
        assertThrows(IllegalArgumentException.class, () -> new FrameFactsPlan.RigDemand(
                FrameFactsPlan.SpatialDemand.notRequired(),
                FrameFactsPlan.SpatialDemand.required(TARGET_A),
                AimState.off()));

        assertThrows(NullPointerException.class, () -> FrameFactsPlan.RigDemand.aiming(null));
        assertTrue(FrameFactsPlan.SpatialDemand.notRequired().optionalValue().isEmpty());
        assertThrows(NullPointerException.class, () -> FrameFactsPlan.SpatialDemand.required(null));
    }

    @Test
    void frameFactsPlanCopiesItsMapAndKeepsEmptyPlan() {
        Map<RigId, FrameFactsPlan.RigDemand> rigs = new HashMap<>();
        rigs.put(RIG, FrameFactsPlan.RigDemand.aiming(AimState.off()));

        FrameFactsPlan plan = new FrameFactsPlan(rigs);
        rigs.clear();

        assertEquals(Set.of(RIG), plan.rigIds());
        assertTrue(plan.demand(RIG).isPresent());
        assertTrue(plan.demand(new RigId("other")).isEmpty());
        assertTrue(FrameFactsPlan.empty().rigIds().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> plan.rigs().clear());
        assertThrows(NullPointerException.class, () -> new FrameFactsPlan(null));
    }

    @Test
    void frameFactsRequestCarriesAnimationTimeAndPlan() {
        AnimationSnapshot snapshot = snapshot();
        EvaluationSession session = EvaluationSession.create(64L, 8);
        FrameFactsPlan plan = new FrameFactsPlan(Map.of(RIG,
                FrameFactsPlan.RigDemand.anchoredAndAiming(ANCHOR_A, TARGET_A, AimState.target(TARGET_A))));

        FrameFactsRequest request = new FrameFactsRequest(
                snapshot, 9f, DIMENSION, 5L, 10L, 0.5f, plan, session);

        assertEquals(9f, request.animationTime());
        assertEquals(10L, request.gameTick());
        assertEquals(5L, request.worldInstanceId());
        assertEquals(plan, request.plan());
        assertEquals(session, request.session());
        assertSameSnapshot(snapshot, request.snapshot());

        assertThrows(IllegalArgumentException.class, () -> new FrameFactsRequest(
                snapshot, Float.NaN, DIMENSION, 5L, 10L, 0.5f, plan, session));
        assertThrows(IllegalArgumentException.class, () -> new FrameFactsRequest(
                snapshot, -1f, DIMENSION, 5L, 10L, 0.5f, plan, session));
        assertThrows(IllegalArgumentException.class, () -> new FrameFactsRequest(
                snapshot, 9f, DIMENSION, -1L, 10L, 0.5f, plan, session));
        assertThrows(IllegalArgumentException.class, () -> new FrameFactsRequest(
                snapshot, 9f, DIMENSION, 5L, -1L, 0.5f, plan, session));
        assertThrows(IllegalArgumentException.class, () -> new FrameFactsRequest(
                snapshot, 9f, DIMENSION, 5L, 10L, 1.0f, plan, session));
        assertThrows(NullPointerException.class, () -> new FrameFactsRequest(
                snapshot, 9f, DIMENSION, 5L, 10L, 0.5f, null, session));
        assertThrows(NullPointerException.class, () -> new FrameFactsRequest(
                snapshot, 9f, DIMENSION, 5L, 10L, 0.5f, plan, null));
        assertThrows(NullPointerException.class, () -> new FrameFactsRequest(
                null, 9f, DIMENSION, 5L, 10L, 0.5f, plan, session));
    }

    @Test
    void frameFactsRequestRejectsRigsOutsideTheSnapshot() {
        FrameFactsPlan plan = new FrameFactsPlan(Map.of(new RigId("ghost"),
                FrameFactsPlan.RigDemand.aiming(AimState.off())));

        assertThrows(IllegalArgumentException.class, () -> new FrameFactsRequest(
                snapshot(), 9f, DIMENSION, 5L, 10L, 0.5f, plan,
                EvaluationSession.create(16L, 4)));
    }

    @Test
    void sameGameTickCanLocateDifferentAnimationTimes() {
        AnimationSnapshot snapshot = snapshot();
        EvaluationSession session = EvaluationSession.create(64L, 8);

        FrameFactsRequest atNine = new FrameFactsRequest(snapshot, 9f, DIMENSION, 5L, 10L, 0.5f,
                new FrameFactsPlan(Map.of(RIG, FrameFactsPlan.RigDemand.aiming(AimState.target(TARGET_A)))),
                session);
        FrameFactsRequest atTen = new FrameFactsRequest(snapshot, 10f, DIMENSION, 5L, 10L, 0.5f,
                new FrameFactsPlan(Map.of(RIG, FrameFactsPlan.RigDemand.aiming(AimState.target(TARGET_B)))),
                session);

        assertEquals(atNine.gameTick(), atTen.gameTick());
        assertNotEquals(atNine, atTen);
        assertEquals(AimState.target(TARGET_A), atNine.plan().demand(RIG).orElseThrow().aim());
        assertEquals(AimState.target(TARGET_B), atTen.plan().demand(RIG).orElseThrow().aim());
    }

    @Test
    void overrideResultsAreCarriedByThePlan() {
        // 宿主先把覆盖算成生效状态，再把结果交给帧准备；提供者不再重读文档默认值。
        AimState documentAim = AimState.target(TARGET_A);

        assertEquals(documentAim, effectiveAim(AimOverride.inherit(), documentAim));
        assertEquals(AimState.off(), effectiveAim(AimOverride.disabled(), documentAim));
        assertEquals(AimState.target(TARGET_B), effectiveAim(AimOverride.replace(TARGET_B), documentAim));

        FrameFactsPlan replaced = new FrameFactsPlan(Map.of(RIG,
                FrameFactsPlan.RigDemand.anchored(ANCHOR_A,
                        effectiveAim(AimOverride.replace(TARGET_B), documentAim))));

        assertEquals(AimState.target(TARGET_B), replaced.demand(RIG).orElseThrow().aim());

        // 局部机位没有文档默认锚点时，由播放请求提供的锚点必须进入计划。
        FrameFactsPlan noDefaultAnchor = new FrameFactsPlan(Map.of(
                RIG, FrameFactsPlan.RigDemand.anchored(ANCHOR_B, AimState.off())));

        assertEquals(Optional.of(ANCHOR_B),
                noDefaultAnchor.demand(RIG).orElseThrow().anchor().optionalValue());
    }

    @Test
    void anchorResolverCanBuildSnapshotScopedDiagnostics() {
        DiagnosticScope.Document scope = new DiagnosticScope.Document(new Revision(7));

        AnchorResolver resolver = new AnchorResolver() {
            @Override
            public Identifier typeId() {
                return ANCHOR_TYPE;
            }

            @Override
            public SpatialResolution resolve(Anchor anchor, AnchorResolutionContext context) {
                // 版本只能来自上下文，不能读当前 store 猜测。
                return SpatialResolution.failed(context.diagnosticScope(),
                        DiagnosticCode.BROKEN_REFERENCE,
                        LocalizedMessage.literal("entity not loaded"),
                        new Diagnostic.Location(null, "space.anchor"));
            }
        };

        SpatialResolution resolution = resolver.resolve(ANCHOR_A, context(scope));

        assertTrue(resolution.isFailed());

        Diagnostic diagnostic = ((SpatialResolution.Failed) resolution).diagnostic();

        assertEquals(scope, diagnostic.scope());
        assertEquals(new Revision(7), ((DiagnosticScope.Document) diagnostic.scope()).revision());
        assertEquals(Severity.ERROR, diagnostic.severity());
        assertEquals(DiagnosticCode.BROKEN_REFERENCE, diagnostic.code());
        assertEquals("space.anchor", diagnostic.location().fieldPath());

        // 同一个启动期注册的解析器服务不同快照时，失败归属跟随本次上下文的版本。
        SpatialResolution nextRevision = resolver.resolve(ANCHOR_A,
                context(new DiagnosticScope.Document(new Revision(8))));

        assertEquals(new Revision(8),
                ((DiagnosticScope.Document) ((SpatialResolution.Failed) nextRevision)
                        .diagnostic().scope()).revision());

        assertThrows(NullPointerException.class,
                () -> SpatialResolution.failed(null, DiagnosticCode.BROKEN_REFERENCE,
                        LocalizedMessage.literal("missing scope")));
        assertThrows(IllegalArgumentException.class, () -> new SpatialResolution.Failed(
                new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.WARNING,
                        LocalizedMessage.literal("warning"), scope, null, null)));
    }

    @Test
    void entityReadingRequiresBothCurrentAndPreviousValues() {
        Vec3 position = new Vec3(1.0, 2.0, 3.0);

        AnchorResolutionContext.EntityReading reading = new AnchorResolutionContext.EntityReading(
                position, Rotation.NONE, position, Rotation.NONE, false);

        assertEquals(position, reading.position());
        assertFalse(reading.hasHistory());

        assertThrows(NullPointerException.class, () -> new AnchorResolutionContext.EntityReading(
                null, Rotation.NONE, position, Rotation.NONE, false));
        assertThrows(NullPointerException.class, () -> new AnchorResolutionContext.EntityReading(
                position, null, position, Rotation.NONE, false));
    }

    // region 辅助

    private static AnimationSnapshot snapshot() {
        CameraRig rig = CameraRig.create(RIG, "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"));

        return new AnimationSnapshot(new Revision(1), document);
    }

    private static AnchorResolutionContext context(DiagnosticScope.Document scope) {
        return new AnchorResolutionContext() {
            @Override
            public Identifier dimension() {
                return DIMENSION;
            }

            @Override
            public long worldInstanceId() {
                return 7L;
            }

            @Override
            public float partialTick() {
                return 0.5f;
            }

            @Override
            public Optional<EntityReading> entity(UUID entityId) {
                return Optional.empty();
            }

            @Override
            public Optional<EntityReading> player(UUID playerId) {
                return Optional.empty();
            }

            @Override
            public DiagnosticScope.Document diagnosticScope() {
                return scope;
            }
        };
    }

    private static AimState effectiveAim(AimOverride override, AimState documentAim) {
        return switch (override) {
            case AimOverride.Inherit ignored -> documentAim;
            case AimOverride.Disabled ignored -> AimState.off();
            case AimOverride.Replace replace -> AimState.target(replace.target());
        };
    }

    private static void assertSameSnapshot(AnimationSnapshot expected, AnimationSnapshot actual) {
        assertSame(expected, actual, "request must keep the exact snapshot reference");
        assertEquals(expected, actual);
    }

    // endregion
}
