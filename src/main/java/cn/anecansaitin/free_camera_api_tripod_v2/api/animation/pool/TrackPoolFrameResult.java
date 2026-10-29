package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 轨道池帧准备结果。
@NullMarked
public sealed interface TrackPoolFrameResult {

    record Prepared(TrackPoolFrame frame) implements TrackPoolFrameResult {
        public Prepared {
            Objects.requireNonNull(frame, "frame");
        }
    }

    record Rejected(List<Diagnostic> diagnostics) implements TrackPoolFrameResult {
        public Rejected {
            diagnostics = List.copyOf(diagnostics);

            if (diagnostics.isEmpty()) {
                throw new IllegalArgumentException("Rejected frame requires diagnostics");
            }
        }
    }
}
