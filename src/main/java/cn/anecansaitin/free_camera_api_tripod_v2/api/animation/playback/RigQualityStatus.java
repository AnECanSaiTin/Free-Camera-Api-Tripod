package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 单路可见像素质量状态（主设计 §7.6.1）。
///
/// 记录完整显示矩形、与画布相交后的可见矩形、作者请求 / 实际渲染尺寸与密度，以及是否执行
/// 裁剪降采样。它只描述运行策略，不改写作者矩形、FOV、姿态或文档质量字段。
///
/// @param fullRect         完整显示矩形（输出像素）
/// @param visibleRect      与画布相交后的可见矩形；空表示完全出屏
/// @param requestedWidth   作者基准请求宽度，正整数
/// @param requestedHeight  作者基准请求高度，正整数
/// @param actualWidth      实际内部渲染宽度，非负；停路 / 目标已释放且未呈现时可为 0
/// @param actualHeight     实际内部渲染高度，非负；停路 / 目标已释放且未呈现时可为 0
/// @param density          实际 / 请求密度；未呈现且有可见矩形时必须为 0
/// @param clippedDownsample 是否按可见面积执行裁剪降采样
@NullMarked
public record RigQualityStatus(
        OutputPixelRect fullRect,
        @Nullable OutputPixelRect visibleRect,
        int requestedWidth,
        int requestedHeight,
        int actualWidth,
        int actualHeight,
        double density,
        boolean clippedDownsample) {

    public RigQualityStatus {
        Objects.requireNonNull(fullRect, "fullRect");

        if (requestedWidth <= 0 || requestedHeight <= 0
                || actualWidth < 0 || actualHeight < 0) {
            throw new IllegalArgumentException(
                    "RigQualityStatus requested dimensions must be positive and actual non-negative");
        }

        boolean releasedTarget = actualWidth == 0 || actualHeight == 0;

        if (releasedTarget && (actualWidth != 0 || actualHeight != 0)) {
            throw new IllegalArgumentException(
                    "RigQualityStatus actual dimensions must both be zero when target is released");
        }

        if (!Double.isFinite(density) || density < 0.0) {
            throw new IllegalArgumentException(
                    "RigQualityStatus density must be finite and non-negative: " + density);
        }

        if (releasedTarget && density != 0.0) {
            throw new IllegalArgumentException(
                    "RigQualityStatus density must be zero when actual target is released");
        }

        if (!releasedTarget && density == 0.0) {
            throw new IllegalArgumentException(
                    "RigQualityStatus density must be positive when actual target exists");
        }

        if (visibleRect != null && !fullRect.contains(visibleRect)) {
            throw new IllegalArgumentException(
                    "RigQualityStatus visibleRect must be inside fullRect");
        }
    }

    /// 是否完全出屏（没有可见交集）。
    public boolean offscreen() {
        return visibleRect == null;
    }
}
