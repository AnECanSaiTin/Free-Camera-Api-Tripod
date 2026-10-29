package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 轨道池提交结果。
@NullMarked
public sealed interface TrackPoolEditResult {

    record Applied(TrackPoolSnapshot snapshot) implements TrackPoolEditResult {
        public Applied {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record Rejected(List<Diagnostic> diagnostics) implements TrackPoolEditResult {
        public Rejected {
            diagnostics = List.copyOf(diagnostics);

            if (diagnostics.isEmpty()) {
                throw new IllegalArgumentException("Rejected result requires diagnostics");
            }
        }
    }
}
