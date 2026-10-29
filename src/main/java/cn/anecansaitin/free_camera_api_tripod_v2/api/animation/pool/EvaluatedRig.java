package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SampleStatus;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 一台机位在某一时刻的属性求值结果。
///
/// `ABSENT` 表示属性关闭或引用空轨道，调用方沿用基础行为；`ERROR` 表示来源存在但求值失败，
/// 不得把它降级成 `ABSENT`，也不得用 0 代替。静态输出配置原样携带。
@NullMarked
public record EvaluatedRig(
        RigId id,
        RigOutput output,
        ChannelSample x,
        ChannelSample y,
        ChannelSample z,
        EvaluatedValue<Quaternion> rotation,
        ChannelSample fov,
        EvaluatedValue<Boolean> active,
        EvaluatedRect rect,
        EvaluatedValue<Boolean> obstacleEnabled,
        ChannelSample obstacleMargin) {

    public EvaluatedRig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(z, "z");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(fov, "fov");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(obstacleEnabled, "obstacleEnabled");
        Objects.requireNonNull(obstacleMargin, "obstacleMargin");
    }

    /// 有类型属性求值结果。
    ///
    /// @param status     值 / 未编写 / 错误
    /// @param value      值；仅 VALUE 非空
    /// @param diagnostic 错误诊断；ERROR 必须非空
    public record EvaluatedValue<T>(
            SampleStatus status,
            @Nullable T value,
            @Nullable Diagnostic diagnostic) {

        public EvaluatedValue {
            Objects.requireNonNull(status, "status");

            if (status == SampleStatus.ERROR && diagnostic == null) {
                throw new IllegalArgumentException("ERROR value must carry a diagnostic");
            }

            if (status == SampleStatus.VALUE && value == null) {
                throw new IllegalArgumentException("VALUE result must be non-null");
            }

            if (status != SampleStatus.VALUE && value != null) {
                throw new IllegalArgumentException("Non-VALUE result must not carry a value");
            }
        }

        public static <T> EvaluatedValue<T> absent() {
            return new EvaluatedValue<>(SampleStatus.ABSENT, null, null);
        }

        public static <T> EvaluatedValue<T> value(T value) {
            return new EvaluatedValue<>(SampleStatus.VALUE, Objects.requireNonNull(value), null);
        }

        public static <T> EvaluatedValue<T> error(Diagnostic diagnostic) {
            return new EvaluatedValue<>(SampleStatus.ERROR, null, Objects.requireNonNull(diagnostic));
        }

        public boolean isValue() {
            return status == SampleStatus.VALUE;
        }

        public boolean isError() {
            return status == SampleStatus.ERROR;
        }
    }

    /// 布局矩形结果；分支与角落是静态配置，四个分量独立求值。
    public record EvaluatedRect(
            Kind kind,
            RigView.Rect.@Nullable Corner corner,
            ChannelSample x,
            ChannelSample y,
            ChannelSample width,
            ChannelSample height) {

        public EvaluatedRect {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
            Objects.requireNonNull(width, "width");
            Objects.requireNonNull(height, "height");

            if (kind == Kind.ANCHORED && corner == null) {
                throw new IllegalArgumentException("Anchored rect requires a corner");
            }

            if (kind == Kind.NORMALIZED && corner != null) {
                throw new IllegalArgumentException("Normalized rect must not carry a corner");
            }
        }

        public enum Kind {
            NORMALIZED,
            ANCHORED
        }
    }
}
