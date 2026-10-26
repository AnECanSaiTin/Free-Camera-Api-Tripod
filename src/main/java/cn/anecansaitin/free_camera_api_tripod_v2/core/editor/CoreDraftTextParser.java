package cn.anecansaitin.free_camera_api_tripod_v2.core.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// 编辑器文本草稿的第一版结构化字段解析。
///
/// 只覆盖当前 core 能安全构造的操作，不猜测未支持字段：
///
/// - `EditTarget.Document`：`name`；
/// - `EditTarget.Rig`：`name`、`view.enabled`、`view.zOrder`、`view.resolutionScale`、
///   `view.updateEveryFrames`、`view.rect.{x,y,width,height}`、`obstacle.enabled`、`obstacle.margin`；
/// - `EditTarget.Track`：`name`；
/// - `EditTarget.Field`：按 owner 解释，字段路径来自调用参数。
///
/// 同一批字段先全部解析，再构造一个候选操作；任一字段失败时整批不产生候选。矩形四分量
/// 共享一次 `SetRigRect`，质量两项共享一次 `SetRigQuality`，避免同一目标被多个操作互相覆盖。
@NullMarked
final class CoreDraftTextParser {

    private CoreDraftTextParser() {
    }

    /// 解析一批字段原文。
    ///
    /// @param target  事务目标；`Field` 使用其 owner
    /// @param texts   字段路径 → 原文
    /// @param snapshot 当前快照
    /// @return 完整候选或错误；任一错误时候选为空
    static Result parse(EditTarget target, Map<String, String> texts, AnimationSnapshot snapshot) {
        EditTarget owner = target instanceof EditTarget.Field field ? field.owner() : target;

        return switch (owner) {
            case EditTarget.Document ignored -> parseDocument(texts, snapshot);
            case EditTarget.Rig rig -> parseRig(rig, texts, snapshot);
            case EditTarget.Track track -> parseTrack(track, texts, snapshot);
            default -> new Result(null, List.of(error(snapshot, null,
                    "Text editing is not supported for target " + owner.getClass().getSimpleName())));
        };
    }

    private static Result parseDocument(Map<String, String> texts, AnimationSnapshot snapshot) {
        List<EditOp> operations = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (var entry : texts.entrySet()) {
            if ("name".equals(entry.getKey())) {
                String name = entry.getValue().trim();

                if (name.isEmpty()) {
                    diagnostics.add(error(snapshot, entry.getKey(), "Document name must not be blank"));
                } else {
                    operations.add(new EditOp.RenameDocument(name));
                }
            } else {
                diagnostics.add(error(snapshot, entry.getKey(),
                        "Unsupported document draft field: " + entry.getKey()));
            }
        }

        return result(operations, diagnostics);
    }

    private static Result parseTrack(EditTarget.Track track,
                                     Map<String, String> texts,
                                     AnimationSnapshot snapshot) {
        List<EditOp> operations = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (snapshot.document().track(track.trackId()).isEmpty()) {
            return new Result(null, List.of(error(snapshot, "name",
                    "Track does not exist: " + track.trackId())));
        }

        for (var entry : texts.entrySet()) {
            if ("name".equals(entry.getKey())) {
                String name = entry.getValue().trim();

                if (name.isEmpty()) {
                    diagnostics.add(error(snapshot, entry.getKey(), "Track name must not be blank"));
                } else {
                    operations.add(new EditOp.RenameTrack(track.trackId(), name));
                }
            } else {
                diagnostics.add(error(snapshot, entry.getKey(),
                        "Unsupported track draft field: " + entry.getKey()));
            }
        }

        return result(operations, diagnostics);
    }

    private static Result parseRig(EditTarget.Rig target,
                                   Map<String, String> texts,
                                   AnimationSnapshot snapshot) {
        CameraRig rig = snapshot.document().rig(target.rigId()).orElse(null);

        if (rig == null) {
            return new Result(null, List.of(error(snapshot, "name",
                    "Rig does not exist: " + target.rigId())));
        }

        List<EditOp> operations = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        RectDraft rect = new RectDraft(rig.view().rect());
        Double resolutionScale = null;
        Integer updateEveryFrames = null;
        Boolean obstacleEnabled = null;
        Double obstacleMargin = null;

        for (var entry : texts.entrySet()) {
            String path = entry.getKey();
            String text = entry.getValue();

            switch (path) {
                case "name" -> {
                    String name = text.trim();

                    if (name.isEmpty()) {
                        diagnostics.add(error(snapshot, path, "Rig name must not be blank"));
                    } else {
                        operations.add(new EditOp.SetRigName(target.rigId(), name));
                    }
                }
                case "view.enabled" -> {
                    Boolean value = parseBoolean(text);

                    if (value == null) {
                        diagnostics.add(error(snapshot, path, "Expected true or false: " + text));
                    } else {
                        operations.add(new EditOp.SetRigEnabled(target.rigId(), value));
                    }
                }
                case "view.zOrder" -> {
                    Integer value = parseInteger(text);

                    if (value == null) {
                        diagnostics.add(error(snapshot, path, "Expected an integer: " + text));
                    } else {
                        operations.add(new EditOp.SetRigZOrder(target.rigId(), value));
                    }
                }
                case "view.resolutionScale" -> {
                    Double value = finiteDouble(text);

                    if (value == null || value <= 0.0 || value > 1.0) {
                        diagnostics.add(error(snapshot, path,
                                "resolutionScale must be in (0, 1]: " + text));
                    } else {
                        resolutionScale = value;
                    }
                }
                case "view.updateEveryFrames" -> {
                    Integer value = parseInteger(text);

                    if (value == null || value < 1) {
                        diagnostics.add(error(snapshot, path,
                                "updateEveryFrames must be an integer >= 1: " + text));
                    } else {
                        updateEveryFrames = value;
                    }
                }
                case "view.rect.x" -> {
                    rect.x = parseNumber(snapshot, path, text, diagnostics);
                    rect.changed = true;
                }
                case "view.rect.y" -> {
                    rect.y = parseNumber(snapshot, path, text, diagnostics);
                    rect.changed = true;
                }
                case "view.rect.width" -> {
                    rect.width = parsePositive(snapshot, path, text, diagnostics);
                    rect.changed = true;
                }
                case "view.rect.height" -> {
                    rect.height = parsePositive(snapshot, path, text, diagnostics);
                    rect.changed = true;
                }
                case "obstacle.enabled" -> {
                    Boolean value = parseBoolean(text);

                    if (value == null) {
                        diagnostics.add(error(snapshot, path, "Expected true or false: " + text));
                    } else {
                        obstacleEnabled = value;
                    }
                }
                case "obstacle.margin" -> {
                    Double value = finiteDouble(text);

                    if (value == null || value < 0.0) {
                        diagnostics.add(error(snapshot, path,
                                "obstacle margin must be finite and non-negative: " + text));
                    } else {
                        obstacleMargin = value;
                    }
                }
                default -> diagnostics.add(error(snapshot, path,
                        "Unsupported rig draft field: " + path));
            }
        }

        if (rect.changed) {
            RigView.Rect updated = rect.build();

            if (updated == null) {
                diagnostics.add(error(snapshot, "view.rect",
                        "Rect width and height must be positive"));
            } else {
                operations.add(new EditOp.SetRigRect(target.rigId(), updated));
            }
        }

        if (resolutionScale != null || updateEveryFrames != null) {
            double scale = resolutionScale == null ? rig.view().resolutionScale() : resolutionScale;
            int frames = updateEveryFrames == null ? rig.view().updateEveryFrames() : updateEveryFrames;
            operations.add(new EditOp.SetRigQuality(target.rigId(), scale, frames));
        }

        if (obstacleEnabled != null || obstacleMargin != null) {
            ObstacleSettings current = rig.obstacle();
            operations.add(new EditOp.SetRigObstacle(target.rigId(), new ObstacleSettings(
                    obstacleEnabled == null ? current.enabled() : obstacleEnabled,
                    obstacleMargin == null ? current.margin() : obstacleMargin)));
        }

        return result(operations, diagnostics);
    }

    private static Result result(List<EditOp> operations, List<Diagnostic> diagnostics) {
        if (!diagnostics.isEmpty()) {
            return new Result(null, List.copyOf(diagnostics));
        }

        if (operations.isEmpty()) {
            return new Result(null, List.of());
        }

        EditOp candidate = operations.size() == 1
                ? operations.getFirst()
                : new EditOp.Batch(operations);
        return new Result(candidate, List.of());
    }

    private static @Nullable AnimatedNumber parseNumber(AnimationSnapshot snapshot,
                                                         String path,
                                                         String text,
                                                         List<Diagnostic> diagnostics) {
        Double value = finiteDouble(text);

        if (value == null) {
            diagnostics.add(error(snapshot, path, "Expected a finite number: " + text));
            return null;
        }

        return new AnimatedNumber.Value(new Constant((float) (double) value));
    }

    private static @Nullable AnimatedNumber parsePositive(AnimationSnapshot snapshot,
                                                           String path,
                                                           String text,
                                                           List<Diagnostic> diagnostics) {
        AnimatedNumber number = parseNumber(snapshot, path, text, diagnostics);

        if (number == null) {
            return null;
        }

        if (!(number instanceof AnimatedNumber.Value value)
                || !(value.source() instanceof Constant constant)
                || constant.value() <= 0f) {
            diagnostics.add(error(snapshot, path, "Width/height must be positive: " + text));
            return null;
        }

        return number;
    }

    private static @Nullable Boolean parseBoolean(String text) {
        if ("true".equalsIgnoreCase(text.trim())) {
            return true;
        }

        if ("false".equalsIgnoreCase(text.trim())) {
            return false;
        }

        return null;
    }

    private static @Nullable Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static @Nullable Double finiteDouble(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Diagnostic error(AnimationSnapshot snapshot, @Nullable String path, String message) {
        return new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                LocalizedMessage.literal(message),
                new DiagnosticScope.Document(snapshot.revision()),
                path == null ? null : new Diagnostic.Location(null, path), null);
    }

    /// 解析结果。
    record Result(@Nullable EditOp candidate, List<Diagnostic> diagnostics) {
        Result {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final class RectDraft {
        private final RigView.Rect original;
        private @Nullable AnimatedNumber x;
        private @Nullable AnimatedNumber y;
        private @Nullable AnimatedNumber width;
        private @Nullable AnimatedNumber height;
        private boolean changed;

        private RectDraft(RigView.Rect original) {
            this.original = original;
        }

        private RigView.Rect build() {
            return switch (original) {
                case RigView.Rect.Normalized normalized -> {
                    AnimatedNumber nx = x == null ? normalized.x() : x;
                    AnimatedNumber ny = y == null ? normalized.y() : y;
                    AnimatedNumber nw = width == null ? normalized.width() : width;
                    AnimatedNumber nh = height == null ? normalized.height() : height;
                    yield new RigView.Rect.Normalized(nx, ny, nw, nh);
                }
                case RigView.Rect.Anchored anchored -> {
                    AnimatedNumber nx = x == null ? anchored.x() : x;
                    AnimatedNumber ny = y == null ? anchored.y() : y;
                    AnimatedNumber nw = width == null ? anchored.width() : width;
                    AnimatedNumber nh = height == null ? anchored.height() : height;
                    yield new RigView.Rect.Anchored(anchored.corner(), nx, ny, nw, nh);
                }
            };
        }
    }
}
