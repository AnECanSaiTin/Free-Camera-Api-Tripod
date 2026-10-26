package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleArgs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleCollectionErrorReason;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.WorldSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsPlan;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsProvider;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolutionContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolver;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolverRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollectionContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollector;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollectorRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/// 第一版帧事实提供者：把宿主给出的空间计划与声明式世界采样冻结成一帧 [FrameFacts]。
///
/// ## 支持范围
///
/// - 只为 [FrameFactsRequest#plan] 中的机位生成 [FrameFacts.RigFacts]；计划之外的机位
///   即使存在于快照里也不会被解析。`NotRequired` 直接写 [SpatialResolution.NotRequired]，
///   不会调用解析器；`Required` 按 `anchor.type()` / `target.target().type()` 查
///   [AnchorResolverRegistry]，缺失、类型不符、抛异常、返回空或 `NotRequired` 都转成
///   [SpatialResolution.Failed]，并附带 ERROR 诊断。
/// - 生效注视状态 [FrameFacts.RigFacts#aim] 原样取自计划，不重新读取文档默认值、当前
///   播放器或编辑器选中机位。
/// - 遍历 `request.snapshot().document().samples()`，按 [WorldSample#kind] 查
///   [SampleCollectorRegistry]。采集器缺失、类型不符、抛异常、返回 `null`、返回的
///   `sampledAtTick` 不是本帧游戏刻，或返回非有限读数的情形都写
///   [SampleReading.CollectionError]；采集器返回的 `Unknown` 原样保留。任何失败都不回退
///   到上一周期成功值、默认数值或文档默认锚点。
/// - 本类**不读当前 store、不触发世界加载、不自行切换线程**。第一版也没有接入真实世界
///   查询面：交给解析器 / 采集器的上下文是请求的只读投影，受控查询
///   （`entity` / `player` / `blockState` / `lightLevel` / `entityAttribute`）恒为空，
///   `chunkLoaded` 恒为 `false`。这是保守的“当前取不到”，真实客户端读数面属于后续宿主
///   接线，不能由 provider 读取当前 store 冒充。
///
/// ## 采样节奏与缓存
///
/// 每个样本在实例内最多保留一条上一周期读数。缓存命中条件是
/// `SampleId + kind + args + resampleEveryTicks + dimension + worldInstanceId + 来源快照 revision`
/// 全部相同，且当前游戏刻仍在重采周期内。来源快照 revision 作为“声明表 / 资源重新解析”的
/// 边界：同一快照内按周期复用，重新发布后的快照不继承旧读数。
///
/// - 同一游戏刻不重复采集（即使 `resampleEveryTicks == 1`），渲染帧率不会放大采集次数；
/// - 游戏刻差小于 `resampleEveryTicks` 时复用上一周期读数；
/// - 参数、类型、周期、来源快照或世界实例变化时开始新周期，绝不复用旧值；
/// - 声明被删除后对应缓存项在下一帧提交时丢弃，实例内的缓存不会随帧数无界增长。
///
/// [SampleReading.CollectionError] 中的 `BUDGET_EXCEEDED` 不进入任何缓存：预算失败只对
/// 本次 [FrameFactsRequest#session] 有效，下一帧允许用新会话重试（复核 R44-N1）。其余
/// 读数（含 `Unknown` 与其它采集错误）按周期缓存，避免同一周期逐渲染帧重复调用采集器。
/// 缓存与表版本只保存“上一周期”的结果，不做会话无关的持久缓存。
///
/// [FrameFactsRequest] 不携带“哪些公式引用了哪些样本”的依赖信息，因此本版把快照声明的
/// **全部采样**都视为本帧所需采样：任何一项采集错误都会使 `valid == false`。
///
/// ## 表版本
///
/// [FrameFacts#sampleTableVersion] 是本实例内单调不减的计数器，从 `0` 开始：第一帧出现
/// 非空采样表时递增到 `1`；只有采样读数（数值 / 状态 / 原因）或采样时间元数据
/// （`sampledAtTick`）发生变化时才递增，`Unknown` 与 `CollectionError` 的状态变化同样计入。
/// 参数变化但读数与采样时间完全相同时不递增：表版本描述的是冻结后的读数表，不是文档
/// 声明本身。输出的表版本不是缓存输入键，否则每次写入新读数都会让缓存自我失效。
///
/// ## 预算与有效性
///
/// 每个实际执行的空间解析与采样采集分别消耗 [WORK_PER_SPATIAL_RESOLUTION] /
/// [WORK_PER_SAMPLE_COLLECTION] 单位工作；缓存命中不收费。额度不足时该项本身失败
/// （空间解析写 `Failed`，采样写 `BUDGET_EXCEEDED`），不抛异常，并继续处理其余条目。
/// [FrameFacts#valid] 为 `true` 当且仅当：所有 `Required` 空间解析都不是 `Failed`、
/// 采样表中没有 `CollectionError`。本帧没有任何一项因预算不足而失败时，即使额度恰好用尽
/// 也仍是有效帧；`Unknown` 不使帧无效。
///
/// ## 线程与生命周期
///
/// 宿主主线程串行调用，**本类不是线程安全的**：缓存、表版本与上一帧读数表都是可变字段。
/// 同一实例可以跨帧、跨世界复用（世界身份会作为缓存签名的一部分），但不得并发调用，
/// 也不得在 [prepare] 内部递归调用。解析器与采集器注册表按接口约定已冻结，本类只读。
@NullMarked
public final class CoreFrameFactsProvider implements FrameFactsProvider {

    /// 每次实际空间解析（锚点或目标）消耗的工作量单位。
    public static final int WORK_PER_SPATIAL_RESOLUTION = 1;

    /// 每次实际世界采样采集消耗的工作量单位；缓存命中不消耗。
    public static final int WORK_PER_SAMPLE_COLLECTION = 1;

    /// 请求类型在客户端没有注册解析器（运行能力缺失，不是文档损坏）。
    public static final DiagnosticCode SPATIAL_RESOLVER_MISSING =
            new DiagnosticCode("free_camera_api_tripod:spatial_resolver_missing");

    /// 注册表返回的解析器声明的类型与请求类型不一致。
    public static final DiagnosticCode SPATIAL_RESOLVER_TYPE_MISMATCH =
            new DiagnosticCode("free_camera_api_tripod:spatial_resolver_type_mismatch");

    /// 解析器抛出了运行时异常。
    public static final DiagnosticCode SPATIAL_RESOLVER_THREW =
            new DiagnosticCode("free_camera_api_tripod:spatial_resolver_threw");

    /// 解析器返回 `null`、`NotRequired` 或携带其它快照作用域的失败诊断。
    public static final DiagnosticCode SPATIAL_RESOLVER_INVALID_RESULT =
            new DiagnosticCode("free_camera_api_tripod:spatial_resolver_invalid_result");

    /// 本帧解析预算不足以完成该锚点 / 目标的解析。
    public static final DiagnosticCode SPATIAL_RESOLUTION_BUDGET_EXCEEDED =
            new DiagnosticCode("free_camera_api_tripod:spatial_resolution_budget_exceeded");

    private static final String ANCHOR_FIELD_PATH = "space.anchor";
    private static final String TARGET_FIELD_PATH = "space.aim.target";

    private final AnchorResolverRegistry anchorResolvers;
    private final SampleCollectorRegistry sampleCollectors;

    private Map<SampleId, CachedSample> sampleCache = Map.of();
    private Map<SampleId, SampleReading> lastSampleTable = Map.of();
    private long sampleTableVersion;

    /// 创建第一版帧事实提供者。
    ///
    /// @param anchorResolvers   已冻结的客户端锚点解析器注册表，只读
    /// @param sampleCollectors  已冻结的客户端采样采集器注册表，只读
    public CoreFrameFactsProvider(AnchorResolverRegistry anchorResolvers,
                                  SampleCollectorRegistry sampleCollectors) {
        this.anchorResolvers = Objects.requireNonNull(anchorResolvers, "anchorResolvers");
        this.sampleCollectors = Objects.requireNonNull(sampleCollectors, "sampleCollectors");
    }

    @Override
    public FrameFacts prepare(FrameFactsRequest request) {
        Objects.requireNonNull(request, "request");

        // 诊断作用域只由本次请求快照决定，并在整帧内复用；不读当前 store 猜版本。
        DiagnosticScope.Document scope = new DiagnosticScope.Document(request.snapshot().revision());
        AnchorResolutionContext anchorContext = new RequestAnchorContext(request, scope);
        SampleCollectionContext sampleContext = new RequestSampleContext(request);

        Map<RigId, FrameFacts.RigFacts> rigs = prepareRigs(request, scope, anchorContext);
        SampleTable table = collectSamples(request, sampleContext);

        long version = table.readings().equals(lastSampleTable)
                ? sampleTableVersion
                : sampleTableVersion + 1;
        boolean valid = isValid(rigs, table.readings());

        // 先构造结果，全部成功后才提交缓存与表版本，避免异常时留下半更新状态。
        FrameFacts facts = new FrameFacts(
                request.snapshot(),
                request.dimension(),
                request.worldInstanceId(),
                request.gameTick(),
                request.partialTick(),
                request.worldTime(),
                version,
                rigs,
                table.readings(),
                valid);

        sampleTableVersion = version;
        lastSampleTable = table.readings();
        sampleCache = table.cache();

        return facts;
    }

    // region 空间准备

    /// 逐项准备计划中的机位；`Required` 才解析，`NotRequired` 不调用注册表。
    private Map<RigId, FrameFacts.RigFacts> prepareRigs(FrameFactsRequest request,
                                                        DiagnosticScope.Document scope,
                                                        AnchorResolutionContext context) {
        Map<RigId, FrameFacts.RigFacts> rigs = new LinkedHashMap<>();

        for (RigId rigId : request.plan().rigIds()) {
            FrameFactsPlan.RigDemand demand = request.plan().demand(rigId).orElseThrow();

            SpatialResolution anchor = demand.needsAnchor()
                    ? resolveSpatial(requiredValue(demand.anchor()), SpatialRole.ANCHOR, rigId,
                    scope, context, request.session())
                    : new SpatialResolution.NotRequired();

            // 目标解析的是 AimTarget 里的锚点；掩码与偏移属于合成阶段，不在本层展开。
            SpatialResolution target = demand.needsTarget()
                    ? resolveSpatial(requiredValue(demand.target()).target(), SpatialRole.TARGET,
                    rigId, scope, context, request.session())
                    : new SpatialResolution.NotRequired();

            // aim 是宿主已应用覆盖后的生效状态：原样承载，不重新求值。
            rigs.put(rigId, new FrameFacts.RigFacts(anchor, target, demand.aim()));
        }

        return Map.copyOf(rigs);
    }

    /// `Required` 分支已经由 `needsAnchor()` / `needsTarget()` 保证有值。
    private static <T> T requiredValue(FrameFactsPlan.SpatialDemand<T> demand) {
        return demand.optionalValue().orElseThrow();
    }

    /// 解析一个锚点或注视目标的锚点，并把所有失败编码成当前快照作用域的 `Failed`。
    private SpatialResolution resolveSpatial(Anchor anchor,
                                             SpatialRole role,
                                             RigId rigId,
                                             DiagnosticScope.Document scope,
                                             AnchorResolutionContext context,
                                             EvaluationSession session) {
        AnchorResolver resolver;

        // 注册表查询与类型校验也视为解析能力的一部分；它们抛出时同样不能把异常交给宿主。
        try {
            Optional<AnchorResolver> found = anchorResolvers.find(anchor.type());

            if (found == null || found.isEmpty()) {
                return failure(scope, SPATIAL_RESOLVER_MISSING, role, rigId,
                        "no resolver is registered for type '" + anchor.type() + "'");
            }

            resolver = found.get();

            if (!anchor.type().equals(resolver.typeId())) {
                return failure(scope, SPATIAL_RESOLVER_TYPE_MISMATCH, role, rigId,
                        "resolver for type '" + anchor.type() + "' reports type '"
                                + resolver.typeId() + "'");
            }
        } catch (RuntimeException exception) {
            return failure(scope, SPATIAL_RESOLVER_THREW, role, rigId,
                    "resolver lookup for type '" + anchor.type() + "' threw "
                            + exception.getClass().getSimpleName());
        }

        if (!session.tryConsumeWork(WORK_PER_SPATIAL_RESOLUTION)) {
            return failure(scope, SPATIAL_RESOLUTION_BUDGET_EXCEEDED, role, rigId,
                    "work budget exhausted before resolving type '" + anchor.type() + "'");
        }

        SpatialResolution resolution;

        try {
            resolution = resolver.resolve(anchor, context);
        } catch (RuntimeException exception) {
            return failure(scope, SPATIAL_RESOLVER_THREW, role, rigId,
                    "resolver for type '" + anchor.type() + "' threw "
                            + exception.getClass().getSimpleName());
        }

        return validateResolution(resolution, role, rigId, scope);
    }

    /// 解析器只能返回 `Resolved` 或携带**本次快照**作用域的 `Failed`；其余结果按失败处理。
    private static SpatialResolution validateResolution(SpatialResolution resolution,
                                                        SpatialRole role,
                                                        RigId rigId,
                                                        DiagnosticScope.Document scope) {
        if (resolution == null) {
            return failure(scope, SPATIAL_RESOLVER_INVALID_RESULT, role, rigId, "resolver returned null");
        }

        if (resolution instanceof SpatialResolution.NotRequired) {
            return failure(scope, SPATIAL_RESOLVER_INVALID_RESULT, role, rigId,
                    "resolver returned NotRequired");
        }

        if (resolution instanceof SpatialResolution.Failed failed) {
            DiagnosticScope actual = failed.diagnostic().scope();

            if (!(actual instanceof DiagnosticScope.Document document)
                    || !document.revision().equals(scope.revision())) {
                return failure(scope, SPATIAL_RESOLVER_INVALID_RESULT, role, rigId,
                        "resolver returned a failure diagnostic from another snapshot");
            }

            return resolution;
        }

        return resolution;
    }

    /// 构造带本次快照版本与机位定位的 ERROR 诊断。
    private static SpatialResolution failure(DiagnosticScope.Document scope,
                                             DiagnosticCode code,
                                             SpatialRole role,
                                             RigId rigId,
                                             String detail) {
        LocalizedMessage message = LocalizedMessage.literal(
                "Cannot resolve " + role.label() + " for rig '" + rigId.value() + "': " + detail);

        return SpatialResolution.failed(scope, code, message,
                new Diagnostic.Location(rigId, role.fieldPath()));
    }

    // endregion

    // region 世界采样

    /// 冻结本帧采样表，并把可复用的上一周期读数与新的采集结果合并。
    private SampleTable collectSamples(FrameFactsRequest request, SampleCollectionContext context) {
        Map<SampleId, SampleReading> readings = new LinkedHashMap<>();
        Map<SampleId, CachedSample> nextCache = new LinkedHashMap<>();

        for (WorldSample sample : request.snapshot().document().samples()) {
            // 同一文档内重复 ID 本应被校验拒绝；这里仍以本次结果为准，不依赖遍历顺序。
            CachedSample cached = nextCache.get(sample.id());

            if (cached == null) {
                cached = sampleCache.get(sample.id());
            }

            if (cached != null && !needsCollection(cached, sample, request)) {
                readings.put(sample.id(), cached.reading());
                nextCache.put(sample.id(), cached);
                continue;
            }

            SampleReading reading = collectSample(sample, context, request);
            readings.put(sample.id(), reading);

            // 预算失败依赖本次会话，绝不写入缓存；下一帧新会话可以重试。
            if (!isBudgetExceeded(reading)) {
                nextCache.put(sample.id(),
                        new CachedSample(SampleSignature.from(sample, request), reading));
            }
        }

        return new SampleTable(Map.copyOf(readings), Map.copyOf(nextCache));
    }

    /// 判断样本本周期的读数是否可以复用上一周期结果。
    private static boolean needsCollection(CachedSample cached,
                                           WorldSample sample,
                                           FrameFactsRequest request) {
        if (isBudgetExceeded(cached.reading())) {
            return true;
        }

        if (!cached.signature().matches(sample, request)) {
            return true;
        }

        long sampledAtTick = cached.reading().sampledAtTick();

        // 游戏刻回退（例如同一世界实例的异常时间源）时强制重采，避免旧读数永久驻留。
        if (request.gameTick() < sampledAtTick) {
            return true;
        }

        return request.gameTick() - sampledAtTick >= sample.resampleEveryTicks();
    }

    /// 调一个采集器；所有失败都落在 `SampleReading`，不向调用方抛异常。
    private SampleReading collectSample(WorldSample sample,
                                        SampleCollectionContext context,
                                        FrameFactsRequest request) {
        long gameTick = request.gameTick();
        SampleCollector collector;

        try {
            Optional<SampleCollector> found = sampleCollectors.find(sample.kind());

            if (found == null || found.isEmpty()) {
                return new SampleReading.CollectionError(
                        SampleCollectionErrorReason.COLLECTOR_MISSING, gameTick);
            }

            collector = found.get();

            if (!sample.kind().equals(collector.typeId())) {
                return new SampleReading.CollectionError(
                        SampleCollectionErrorReason.COLLECTOR_MISSING, gameTick);
            }
        } catch (RuntimeException exception) {
            return new SampleReading.CollectionError(
                    SampleCollectionErrorReason.COLLECTOR_THREW, gameTick);
        }

        if (!request.session().tryConsumeWork(WORK_PER_SAMPLE_COLLECTION)) {
            return new SampleReading.CollectionError(
                    SampleCollectionErrorReason.BUDGET_EXCEEDED, gameTick);
        }

        SampleReading reading;

        try {
            reading = collector.collect(sample.args(), context);
        } catch (RuntimeException exception) {
            return new SampleReading.CollectionError(
                    SampleCollectionErrorReason.COLLECTOR_THREW, gameTick);
        }

        // 读数的时间元数据由本帧游戏刻决定；采集器返回旧周期时间、null 或非有限数值都属于
        // 非法输出，不能把它当成本帧新读数，也不能回退旧值。
        if (reading == null
                || reading.sampledAtTick() != gameTick
                || reading instanceof SampleReading.Value value && !Float.isFinite(value.value())) {
            return new SampleReading.CollectionError(
                    SampleCollectionErrorReason.ILLEGAL_OUTPUT, gameTick);
        }

        return reading;
    }

    /// 读数是否可以由下一帧新会话重试；这类结果不得进入实例缓存。
    private static boolean isBudgetExceeded(SampleReading reading) {
        return reading instanceof SampleReading.CollectionError error
                && error.reason() == SampleCollectionErrorReason.BUDGET_EXCEEDED;
    }

    /// 计算帧有效性：Required 解析都不失败，且采样表没有采集错误。
    ///
    /// 预算不足已经逐项落成 `Failed` / `CollectionError`，因此这里不需要再单独读会话；
    /// 恰好用完额度但全部条目成功仍是有效帧。
    private static boolean isValid(Map<RigId, FrameFacts.RigFacts> rigs,
                                   Map<SampleId, SampleReading> readings) {
        for (FrameFacts.RigFacts facts : rigs.values()) {
            if (facts.anchor().isFailed() || facts.target().isFailed()) {
                return false;
            }
        }

        for (SampleReading reading : readings.values()) {
            if (reading instanceof SampleReading.CollectionError) {
                return false;
            }
        }

        return true;
    }

    // endregion

    // region 内部值

    /// 锚点与目标共用同一套解析机制，仅诊断文案和字段路径不同。
    private enum SpatialRole {
        ANCHOR("anchor", ANCHOR_FIELD_PATH),
        TARGET("target", TARGET_FIELD_PATH);

        private final String label;
        private final String fieldPath;

        SpatialRole(String label, String fieldPath) {
            this.label = label;
            this.fieldPath = fieldPath;
        }

        private String label() {
            return label;
        }

        private String fieldPath() {
            return fieldPath;
        }
    }

    /// 一帧的采样表与下一帧仍可复用的缓存项。
    private record SampleTable(Map<SampleId, SampleReading> readings,
                               Map<SampleId, CachedSample> cache) {
    }

    /// 缓存身份：参数 / 类型 / 周期 / 来源快照任一变化或换世界实例都必须重新采集。
    private record SampleSignature(Identifier kind,
                                   SampleArgs args,
                                   int resampleEveryTicks,
                                   Identifier dimension,
                                   long worldInstanceId,
                                   long sourceRevision) {

        static SampleSignature from(WorldSample sample, FrameFactsRequest request) {
            return new SampleSignature(sample.kind(), sample.args(), sample.resampleEveryTicks(),
                    request.dimension(), request.worldInstanceId(),
                    request.snapshot().revision().value());
        }

        boolean matches(WorldSample sample, FrameFactsRequest request) {
            return kind.equals(sample.kind())
                    && args.equals(sample.args())
                    && resampleEveryTicks == sample.resampleEveryTicks()
                    && dimension.equals(request.dimension())
                    && worldInstanceId == request.worldInstanceId()
                    && sourceRevision == request.snapshot().revision().value();
        }
    }

    /// 上一周期读数及其缓存身份。
    private record CachedSample(SampleSignature signature, SampleReading reading) {
        CachedSample {
            Objects.requireNonNull(signature, "signature");
            Objects.requireNonNull(reading, "reading");
        }
    }

    /// 请求的锚点上下文投影：维度、世界实例、partial tick 与本次快照的诊断作用域。
    ///
    /// 第一版没有世界读端口，实体 / 玩家查询恒为空；解析器据此返回失败或未知，而不是由
    /// provider 读取玩家相机、当前 store 或触发加载来补足输入。
    private record RequestAnchorContext(FrameFactsRequest request,
                                        DiagnosticScope.Document diagnosticScope)
            implements AnchorResolutionContext {

        @Override
        public Identifier dimension() {
            return request.dimension();
        }

        @Override
        public long worldInstanceId() {
            return request.worldInstanceId();
        }

        @Override
        public float partialTick() {
            return request.partialTick();
        }

        @Override
        public Optional<EntityReading> entity(UUID entityId) {
            return Optional.empty();
        }

        @Override
        public Optional<EntityReading> player(UUID playerId) {
            return Optional.empty();
        }
    }

    /// 请求的采样上下文投影：只暴露帧元数据与会话，受控世界查询恒为“不可用”。
    ///
    /// `chunkLoaded` 恒为 `false`，`blockState` / `lightLevel` / `entityAttribute` 恒为空；
    /// 采集器只能把结果写成 `Unknown`，不得由 provider 猜测世界状态或回退默认值。
    private record RequestSampleContext(FrameFactsRequest request) implements SampleCollectionContext {

        @Override
        public Identifier dimension() {
            return request.dimension();
        }

        @Override
        public long worldInstanceId() {
            return request.worldInstanceId();
        }

        @Override
        public long gameTick() {
            return request.gameTick();
        }

        @Override
        public boolean chunkLoaded(int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public Optional<BlockState> blockState(int x, int y, int z) {
            return Optional.empty();
        }

        @Override
        public OptionalInt lightLevel(int x, int y, int z) {
            return OptionalInt.empty();
        }

        @Override
        public Optional<Float> entityAttribute(UUID entityId, Identifier attributeId) {
            return Optional.empty();
        }

        @Override
        public EvaluationSession session() {
            return request.session();
        }
    }

    // endregion
}
