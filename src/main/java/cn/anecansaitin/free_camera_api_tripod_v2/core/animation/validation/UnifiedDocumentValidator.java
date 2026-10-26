package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.validation;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DocumentValidator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.ValidationLayer;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.DocumentObjectId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.PathNodeId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathNode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeValidationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeValidator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CoreDependencyGraph;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/// 统一文档校验的第一份实现。
///
/// 按 L1 → L2 → L3 → L4 分层执行；前一层存在 `ERROR` 时停止后续层。类型注册表暴露的
/// [TypeValidator] 按其声明的层参与同层分派，且只拿到本次文档版本构造的
/// [TypeValidationContext]（不在校验器里读取当前 store）。
///
/// ## 当前覆盖
///
/// - L2：稳定 ID 唯一性、同轨 `KeyId` 唯一、世界采样名称唯一、相机来源机位存在、
///   `TrackRef` 目标存在且为曲线轨道、注册类型存在、类型校验器分派。
/// - L3：注视掩码不得包含 Roll、来源机位在全部阶跃区间内活动、表达式引用绑定，
///   以及在完整候选上构建的依赖环诊断（含未被轨道引用的变量 / 函数与布局公式）。
/// - L4：曲线曲柄长度范围。
///
/// 表达式语法、资源规模、真实世界查询和跨文档引用不在本类范围；它们分别属于表达式编译器、
/// 存储限额与客户端运行能力。
@NullMarked
public final class UnifiedDocumentValidator implements DocumentValidator {

    private static final float MAX_HANDLE_LENGTH = 1.5f;

    private final DocumentRegistries registries;
    private final Revision revision;

    /// @param registries 已冻结的类型注册表
    /// @param revision   本次候选适用的发布序号；用于诊断作用域，不读取当前 store
    public UnifiedDocumentValidator(DocumentRegistries registries, Revision revision) {
        this.registries = Objects.requireNonNull(registries, "registries");
        this.revision = Objects.requireNonNull(revision, "revision");
    }

    @Override
    public List<Diagnostic> validate(CameraAnimation candidate) {
        Objects.requireNonNull(candidate, "candidate");

        List<TypeCheck> checks = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        collectTypeChecks(candidate, checks, diagnostics);

        diagnostics.addAll(runTypeChecks(checks, ValidationLayer.FORMAT));

        if (hasError(diagnostics)) {
            return List.copyOf(diagnostics);
        }

        checkStructure(candidate, diagnostics);
        diagnostics.addAll(runTypeChecks(checks, ValidationLayer.STRUCTURE));

        if (hasError(diagnostics)) {
            return List.copyOf(diagnostics);
        }

        checkSemantics(candidate, diagnostics);
        diagnostics.addAll(runTypeChecks(checks, ValidationLayer.SEMANTICS));

        if (hasError(diagnostics)) {
            return List.copyOf(diagnostics);
        }

        checkDomain(candidate, diagnostics);
        diagnostics.addAll(runTypeChecks(checks, ValidationLayer.DOMAIN));

        return List.copyOf(diagnostics);
    }

    // region 类型校验器分派

    private void collectTypeChecks(CameraAnimation document,
                                   List<TypeCheck> checks,
                                   List<Diagnostic> resolutionErrors) {
        for (int rigIndex = 0; rigIndex < document.rigs().size(); rigIndex++) {
            CameraRig rig = document.rigs().get(rigIndex);
            collectSpaceAnchor(rig, rigIndex, checks, resolutionErrors);
            collectAimAnchors(rig, rigIndex, checks, resolutionErrors);
        }

        for (int index = 0; index < document.tracks().size(); index++) {
            AnimationTrack.ExtensionTrack track = document.tracks().get(index);

            if (isReservedType(track.type())) {
                resolutionErrors.add(error(DiagnosticCode.INVALID_EDIT,
                        "Extension track uses reserved built-in type " + track.type(),
                        track.id(), "tracks[" + index + "].type"));
                continue;
            }

            TrackType<?> type = registries.tracks().find(track.type()).orElse(null);

            if (type == null) {
                resolutionErrors.add(error(DiagnosticCode.BROKEN_REFERENCE,
                        "Unknown extension track type " + track.type(),
                        track.id(), "tracks[" + index + "].type"));
                continue;
            }

            addTypeCheck(checks, type.validator(), track, track.id(), null,
                    "tracks[" + index + "]");
        }

        for (int index = 0; index < document.samples().size(); index++) {
            var sample = document.samples().get(index);
            SampleType<?> type = registries.samples().find(sample.kind()).orElse(null);

            if (type == null) {
                resolutionErrors.add(error(DiagnosticCode.BROKEN_REFERENCE,
                        "Unknown sample type " + sample.kind(),
                        sample.id(), "samples[" + index + "].kind"));
                continue;
            }

            addTypeCheck(checks, type.validator(), sample, sample.id(), null,
                    "samples[" + index + "]");
        }
    }

    private void collectSpaceAnchor(CameraRig rig,
                                    int rigIndex,
                                    List<TypeCheck> checks,
                                    List<Diagnostic> resolutionErrors) {
        if (!(rig.space() instanceof Space.Local local) || local.defaultAnchor() == null) {
            return;
        }

        collectAnchor(local.defaultAnchor(), rig.id(), rig.id(),
                "rigs[" + rigIndex + "].space.default_anchor", checks, resolutionErrors);
    }

    private void collectAimAnchors(CameraRig rig,
                                   int rigIndex,
                                   List<TypeCheck> checks,
                                   List<Diagnostic> resolutionErrors) {
        AimTrack aim = rig.aim();

        collectAimStateAnchor(aim.defaultValue(), rig.id(), aim.id(),
                "rigs[" + rigIndex + "].aim.default", checks, resolutionErrors);

        for (int keyIndex = 0; keyIndex < aim.keys().size(); keyIndex++) {
            ValueKeyframe<AimState> key = aim.keys().get(keyIndex);
            collectAimStateAnchor(key.value(), rig.id(), aim.id(),
                    "rigs[" + rigIndex + "].aim.keys[" + keyIndex + "].value",
                    checks, resolutionErrors);
        }
    }

    private void collectAimStateAnchor(AimState state,
                                       RigId ownerRig,
                                       DocumentObjectId objectId,
                                       String fieldPath,
                                       List<TypeCheck> checks,
                                       List<Diagnostic> resolutionErrors) {
        AimTarget target = state.targetOrNull();

        if (target != null) {
            collectAnchor(target.target(), ownerRig, objectId, fieldPath + ".target",
                    checks, resolutionErrors);
        }
    }

    private void collectAnchor(Anchor anchor,
                               RigId ownerRig,
                               DocumentObjectId objectId,
                               String fieldPath,
                               List<TypeCheck> checks,
                               List<Diagnostic> resolutionErrors) {
        AnchorType<?> type = registries.anchors().find(anchor.type()).orElse(null);

        if (type == null) {
            resolutionErrors.add(error(DiagnosticCode.BROKEN_REFERENCE,
                    "Unknown anchor type " + anchor.type(), objectId, fieldPath + ".type"));
            return;
        }

        addTypeCheck(checks, type.validator(), anchor, objectId, ownerRig, fieldPath);
    }

    private void addTypeCheck(List<TypeCheck> checks,
                              Optional<? extends TypeValidator<?>> validator,
                              Object value,
                              DocumentObjectId objectId,
                              @Nullable RigId ownerRig,
                              String fieldPath) {
        validator.ifPresent(check -> checks.add(new TypeCheck(check.layer(), fieldPath, objectId, ownerRig,
                () -> safeValidate(check, value, objectId, ownerRig, fieldPath))));
    }

    @SuppressWarnings("unchecked")
    private List<Diagnostic> safeValidate(TypeValidator<?> validator,
                                          Object value,
                                          DocumentObjectId objectId,
                                          @Nullable RigId ownerRig,
                                          String fieldPath) {
        try {
            TypeValidationContext context = new TypeValidationContext(
                    revision, objectId, ownerRig, fieldPath);
            List<Diagnostic> result = ((TypeValidator<Object>) validator).validate(value, context);
            return result == null ? List.of() : List.copyOf(result);
        } catch (RuntimeException exception) {
            return List.of(error(DiagnosticCode.INVALID_EDIT,
                    "Type validator failed: " + messageOf(exception), objectId, fieldPath));
        }
    }

    private static List<Diagnostic> runTypeChecks(List<TypeCheck> checks, ValidationLayer layer) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (TypeCheck check : checks) {
            if (check.layer() == layer) {
                diagnostics.addAll(check.run().get());
            }
        }

        return diagnostics;
    }

    // endregion

    // region L2

    private void checkStructure(CameraAnimation document, List<Diagnostic> diagnostics) {
        checkRigIds(document, diagnostics);
        checkTrackIds(document, diagnostics);
        checkPathNodeIds(document, diagnostics);
        checkDocumentIds(document, diagnostics);
        checkSymbolNames(document, diagnostics);
        checkSampleNames(document, diagnostics);
        checkKeyIds(document, diagnostics);
        checkReferences(document, diagnostics);
    }

    private void checkRigIds(CameraAnimation document, List<Diagnostic> diagnostics) {
        Set<RigId> ids = new HashSet<>();

        for (CameraRig rig : document.rigs()) {
            if (!ids.add(rig.id())) {
                diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                        "Duplicate rig id " + rig.id(), rig.id(), "rigs"));
            }
        }
    }

    private void checkTrackIds(CameraAnimation document, List<Diagnostic> diagnostics) {
        Set<TrackId> ids = new HashSet<>();

        for (CameraRig rig : document.rigs()) {
            for (CurveTrack channel : rig.channels()) {
                checkTrackId(ids, channel, "channels", diagnostics);
            }

            checkTrackId(ids, rig.aim(), "aim", diagnostics);
            checkTrackId(ids, rig.view().active(), "view.active", diagnostics);
        }

        checkTrackId(ids, document.cameraSource(), "camera_source", diagnostics);

        for (AnimationTrack.ExtensionTrack track : document.tracks()) {
            checkTrackId(ids, track, "tracks", diagnostics);
        }
    }

    private void checkTrackId(Set<TrackId> ids,
                              AnimationTrack track,
                              String field,
                              List<Diagnostic> diagnostics) {
        if (!ids.add(track.id())) {
            diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                    "Duplicate track id " + track.id(), track.id(), field + ".id"));
        }
    }

    private void checkPathNodeIds(CameraAnimation document, List<Diagnostic> diagnostics) {
        for (CameraRig rig : document.rigs()) {
            Set<PathNodeId> ids = new HashSet<>();

            for (PathNode node : rig.path().nodes()) {
                if (!ids.add(node.id())) {
                    diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                            "Duplicate path node id " + node.id(), rig.id(), "path.nodes"));
                }
            }
        }
    }

    private void checkDocumentIds(CameraAnimation document, List<Diagnostic> diagnostics) {
        Set<Object> markerIds = new HashSet<>();
        Set<Object> sampleIds = new HashSet<>();
        Set<Object> audioIds = new HashSet<>();
        Set<Object> overlayIds = new HashSet<>();

        document.markers().forEach(marker -> {
            if (!markerIds.add(marker.id())) {
                diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                        "Duplicate marker id " + marker.id(), marker.id(), "markers"));
            }
        });

        document.samples().forEach(sample -> {
            if (!sampleIds.add(sample.id())) {
                diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                        "Duplicate sample id " + sample.id(), sample.id(), "samples"));
            }
        });

        document.audioRefs().forEach(ref -> {
            if (!audioIds.add(ref.id())) {
                diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                        "Duplicate audio ref id " + ref.id(), ref.id(), "audio_refs"));
            }
        });

        document.overlays().forEach(overlay -> {
            if (!overlayIds.add(overlay.id())) {
                diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                        "Duplicate overlay id " + overlay.id(), overlay.id(), "overlays"));
            }
        });
    }

    private void checkSymbolNames(CameraAnimation document, List<Diagnostic> diagnostics) {
        Set<String> variables = new HashSet<>();
        Set<String> functions = new HashSet<>();

        for (Variable variable : document.symbols().variables()) {
            if (!variables.add(variable.name())) {
                diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                        "Duplicate variable name " + variable.name(), null, "symbols.variables"));
            }
        }

        for (CustomFunction function : document.symbols().functions()) {
            if (!functions.add(function.name())) {
                diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                        "Duplicate function name " + function.name(), null, "symbols.functions"));
            }
        }
    }

    private void checkSampleNames(CameraAnimation document, List<Diagnostic> diagnostics) {
        Set<String> names = new HashSet<>();

        for (var sample : document.samples()) {
            if (!names.add(sample.name())) {
                diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                        "Duplicate sample name " + sample.name(), sample.id(), "samples.name"));
            }
        }
    }

    /// 全文档作用域内 `KeyId` 必须唯一（主设计 §3.1）。
    ///
    /// 构造期只保证单轨唯一；发布校验必须跨机位、跨轨道、跨状态 / 布局曲线与扩展时间轴再核对，
    /// 否则按 ID 改值会出现多个命中。非 `ExtensionTimelineTrack` 的扩展轨道无法派生键身份，
    /// 由类型校验器负责其内部约束；本方法只覆盖框架可见的键。
    private void checkKeyIds(CameraAnimation document, List<Diagnostic> diagnostics) {
        Map<KeyId, String> seen = new LinkedHashMap<>();

        for (int rigIndex = 0; rigIndex < document.rigs().size(); rigIndex++) {
            CameraRig rig = document.rigs().get(rigIndex);
            String prefix = "rigs[" + rigIndex + "]";

            for (int channelIndex = 0; channelIndex < rig.channels().size(); channelIndex++) {
                CurveTrack channel = rig.channels().get(channelIndex);
                checkCurveKeyIds(channel.curve(), channel.id(),
                        prefix + ".channels[" + channelIndex + "].curve", seen, diagnostics);
            }

            checkStateKeyIds(rig.aim().keys(), rig.aim().id(),
                    prefix + ".aim.keys", seen, diagnostics);
            checkStateKeyIds(rig.view().active().keys(), rig.view().active().id(),
                    prefix + ".view.active.keys", seen, diagnostics);
            checkRectKeyIds(rig.view().rect(), rig.id(),
                    prefix + ".view.rect", seen, diagnostics);
        }

        checkStateKeyIds(document.cameraSource().keys(), document.cameraSource().id(),
                "camera_source.keys", seen, diagnostics);

        for (int trackIndex = 0; trackIndex < document.tracks().size(); trackIndex++) {
            AnimationTrack track = document.tracks().get(trackIndex);

            if (!(track instanceof ExtensionTimelineTrack timeline)) {
                continue;
            }

            String fieldPath = "tracks[" + trackIndex + "].keys";

            for (int keyIndex = 0; keyIndex < timeline.timelineKeys().size(); keyIndex++) {
                ExtensionTimelineTrack.TimelineKey key = timeline.timelineKeys().get(keyIndex);
                recordKeyId(key.keyId(), track.id(), fieldPath + "[" + keyIndex + "]",
                        seen, diagnostics);
            }
        }
    }

    private void checkCurveKeyIds(Curve curve,
                                  DocumentObjectId objectId,
                                  String fieldPath,
                                  Map<KeyId, String> seen,
                                  List<Diagnostic> diagnostics) {
        for (int index = 0; index < curve.keys().size(); index++) {
            Keyframe key = curve.keys().get(index);
            recordKeyId(key.keyId(), objectId, fieldPath + ".keys[" + index + "]",
                    seen, diagnostics);
        }
    }

    private void checkStateKeyIds(List<? extends ValueKeyframe<?>> keys,
                                  DocumentObjectId objectId,
                                  String fieldPath,
                                  Map<KeyId, String> seen,
                                  List<Diagnostic> diagnostics) {
        for (int index = 0; index < keys.size(); index++) {
            ValueKeyframe<?> key = keys.get(index);
            recordKeyId(key.keyId(), objectId, fieldPath + "[" + index + "]",
                    seen, diagnostics);
        }
    }

    private void checkRectKeyIds(RigView.Rect rect,
                                 DocumentObjectId objectId,
                                 String fieldPath,
                                 Map<KeyId, String> seen,
                                 List<Diagnostic> diagnostics) {
        switch (rect) {
            case RigView.Rect.Normalized normalized -> {
                checkAnimatedKeyIds(normalized.x(), objectId, fieldPath + ".x", seen, diagnostics);
                checkAnimatedKeyIds(normalized.y(), objectId, fieldPath + ".y", seen, diagnostics);
                checkAnimatedKeyIds(normalized.width(), objectId, fieldPath + ".width", seen, diagnostics);
                checkAnimatedKeyIds(normalized.height(), objectId, fieldPath + ".height", seen, diagnostics);
            }
            case RigView.Rect.Anchored anchored -> {
                checkAnimatedKeyIds(anchored.x(), objectId, fieldPath + ".x", seen, diagnostics);
                checkAnimatedKeyIds(anchored.y(), objectId, fieldPath + ".y", seen, diagnostics);
                checkAnimatedKeyIds(anchored.width(), objectId, fieldPath + ".width", seen, diagnostics);
                checkAnimatedKeyIds(anchored.height(), objectId, fieldPath + ".height", seen, diagnostics);
            }
        }
    }

    private void checkAnimatedKeyIds(AnimatedNumber number,
                                     DocumentObjectId objectId,
                                     String fieldPath,
                                     Map<KeyId, String> seen,
                                     List<Diagnostic> diagnostics) {
        if (number instanceof AnimatedNumber.Keyed keyed) {
            checkCurveKeyIds(keyed.curve(), objectId, fieldPath + ".curve", seen, diagnostics);
        }
    }

    private void recordKeyId(KeyId keyId,
                             DocumentObjectId objectId,
                             String fieldPath,
                             Map<KeyId, String> seen,
                             List<Diagnostic> diagnostics) {
        String previous = seen.putIfAbsent(keyId, fieldPath);

        if (previous != null) {
            diagnostics.add(error(DiagnosticCode.DUPLICATE_KEY_ID,
                    "Duplicate key id " + keyId + " at " + fieldPath
                            + "; first seen at " + previous,
                    objectId, fieldPath));
        }
    }

    private void checkReferences(CameraAnimation document, List<Diagnostic> diagnostics) {
        checkCameraSource(document, document.cameraSource().defaultValue(),
                document.cameraSource().id(), "camera_source.default", diagnostics);

        for (int index = 0; index < document.cameraSource().keys().size(); index++) {
            checkCameraSource(document, document.cameraSource().keys().get(index).value(),
                    document.cameraSource().id(), "camera_source.keys[" + index + "]",
                    diagnostics);
        }

        for (Variable variable : document.symbols().variables()) {
            if (variable.source() instanceof TrackRef ref) {
                boolean curve = document.curveTrack(ref.trackId()).isPresent();

                if (!curve) {
                    diagnostics.add(error(DiagnosticCode.BROKEN_REFERENCE,
                            "TrackRef " + ref.trackId()
                                    + " does not resolve to a curve track",
                            null, "symbols.variables." + variable.name()));
                }
            }
        }
    }

    private void checkCameraSource(CameraAnimation document,
                                   CameraSource source,
                                   DocumentObjectId objectId,
                                   String fieldPath,
                                   List<Diagnostic> diagnostics) {
        if (source instanceof CameraSource.Rig rig && document.rig(rig.rigId()).isEmpty()) {
            diagnostics.add(error(DiagnosticCode.BROKEN_REFERENCE,
                    "Camera source rig does not exist: " + rig.rigId(), objectId, fieldPath));
        }
    }

    // endregion

    // region L3

    private void checkSemantics(CameraAnimation document, List<Diagnostic> diagnostics) {
        checkAimMasks(document, diagnostics);
        checkSourceActivity(document, diagnostics);
        diagnostics.addAll(new ExpressionValidation(document, revision).validate());
        // 独立依赖图的环诊断进入强制校验；未定义引用 / arity 由 ExpressionValidation 报告，
        // 这里只取环，避免同一条问题重复两遍（主设计 §4.2 L3）。
        diagnostics.addAll(CoreDependencyGraph.build(document, revision).cycleDiagnostics());
    }

    private void checkAimMasks(CameraAnimation document, List<Diagnostic> diagnostics) {
        for (CameraRig rig : document.rigs()) {
            checkAimMask(rig.aim().defaultValue(), rig.aim().id(), "aim.default", diagnostics);

            for (int index = 0; index < rig.aim().keys().size(); index++) {
                checkAimMask(rig.aim().keys().get(index).value(), rig.aim().id(),
                        "aim.keys[" + index + "]", diagnostics);
            }
        }
    }

    private void checkAimMask(AimState state,
                              DocumentObjectId objectId,
                              String fieldPath,
                              List<Diagnostic> diagnostics) {
        AimTarget target = state.targetOrNull();

        if (target != null && target.mask().z()) {
            diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                    "Aim target must not enable Roll (z mask); Roll belongs to rotation channels",
                    objectId, fieldPath + ".target.mask"));
        }
    }

    private void checkSourceActivity(CameraAnimation document, List<Diagnostic> diagnostics) {
        Set<Float> boundaries = new TreeSet<>();
        boundaries.add(0f);

        for (ValueKeyframe<CameraSource> key : document.cameraSource().keys()) {
            boundaries.add(key.time());
        }

        for (CameraRig rig : document.rigs()) {
            for (ValueKeyframe<Boolean> key : rig.view().active().keys()) {
                boundaries.add(key.time());
            }
        }

        for (float time : boundaries) {
            CameraSource source = document.cameraSource().at(time);

            if (!(source instanceof CameraSource.Rig selected)) {
                continue;
            }

            CameraRig rig = document.rig(selected.rigId()).orElse(null);

            if (rig == null) {
                continue;
            }

            if (!rig.view().enabled()) {
                diagnostics.add(error(DiagnosticCode.BROKEN_REFERENCE,
                        "Camera source rig " + selected.rigId()
                                + " is disabled at " + time + "s",
                        document.cameraSource().id(), "camera_source"));
                continue;
            }

            if (!rig.view().active().at(time)) {
                diagnostics.add(error(DiagnosticCode.BROKEN_REFERENCE,
                        "Camera source rig " + selected.rigId()
                                + " is inactive at " + time + "s",
                        document.cameraSource().id(), "camera_source"));
            }
        }
    }

    // endregion

    // region L4

    private void checkDomain(CameraAnimation document, List<Diagnostic> diagnostics) {
        for (CameraRig rig : document.rigs()) {
            for (CurveTrack channel : rig.channels()) {
                checkHandles(channel, diagnostics);
            }
        }
    }

    private void checkHandles(CurveTrack track, List<Diagnostic> diagnostics) {
        for (Keyframe key : track.curve().keys()) {
            if (key.inLength() < 0f || key.inLength() > MAX_HANDLE_LENGTH
                    || key.outLength() < 0f || key.outLength() > MAX_HANDLE_LENGTH) {
                diagnostics.add(error(DiagnosticCode.INVALID_EDIT,
                        "Keyframe handle length must be in [0, " + MAX_HANDLE_LENGTH + "]: "
                                + key.inLength() + " / " + key.outLength(),
                        track.id(), "curve.keys." + key.keyId()));
            }
        }
    }

    // endregion

    // region 诊断

    private Diagnostic error(DiagnosticCode code,
                             String message,
                             @Nullable DocumentObjectId objectId,
                             String fieldPath) {
        return new Diagnostic(code, Severity.ERROR, LocalizedMessage.literal(message),
                new DiagnosticScope.Document(revision),
                new Diagnostic.Location(objectId, fieldPath), null);
    }

    private static boolean hasError(List<Diagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    // endregion

    private static boolean isReservedType(Identifier type) {
        return type.equals(CurveTrack.TYPE)
                || type.equals(AimTrack.TYPE)
                || type.equals(ActiveTrack.TYPE)
                || type.equals(CameraSourceTrack.TYPE);
    }

    private record TypeCheck(ValidationLayer layer,
                             String fieldPath,
                             DocumentObjectId objectId,
                             @Nullable RigId ownerRig,
                             Supplier<List<Diagnostic>> run) {
    }
}
