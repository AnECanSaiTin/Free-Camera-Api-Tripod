package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 一帧已求值的轨道池文档。
///
/// 只包含属性求值结果与文档级相机来源；世界事实、避障修正、布局像素映射与相机应用仍由
/// 后续准备/合成阶段完成。
@NullMarked
public record TrackPoolFrame(
        Revision revision,
        float time,
        List<EvaluatedRig> rigs,
        EvaluatedRig.EvaluatedValue<CameraSource> cameraSource,
        float playbackStart,
        float playbackEnd) {

    public TrackPoolFrame {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(rigs, "rigs");
        Objects.requireNonNull(cameraSource, "cameraSource");

        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException("TrackPoolFrame time must be finite and non-negative");
        }

        rigs = List.copyOf(rigs);
    }
}
