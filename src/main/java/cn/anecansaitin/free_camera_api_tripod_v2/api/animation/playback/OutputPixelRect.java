package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// 输出画布像素坐标中的矩形（主设计 §7.6.1）。
///
/// 坐标允许为负或超出画布；作者矩形部分出屏是正常情形。宽高必须为正，非法几何不能伪装成
/// 出屏。像素口径是最终输出画布，不是 GUI 逻辑像素。
///
/// @param x      左上角横坐标
/// @param y      左上角纵坐标
/// @param width  宽度，正整数
/// @param height 高度，正整数
@NullMarked
public record OutputPixelRect(int x, int y, int width, int height) {

    public OutputPixelRect {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "OutputPixelRect dimensions must be positive: " + width + "x" + height);
        }
    }

    /// 面积，使用 long 避免 int 溢出。
    public long area() {
        return (long) width * height;
    }

    /// 与另一矩形的交集；完全不相交时为空。
    public Optional<OutputPixelRect> intersection(OutputPixelRect other) {
        long left = Math.max((long) x, other.x);
        long top = Math.max((long) y, other.y);
        long right = Math.min((long) x + width, (long) other.x + other.width);
        long bottom = Math.min((long) y + height, (long) other.y + other.height);

        if (right <= left || bottom <= top) {
            return Optional.empty();
        }

        return Optional.of(new OutputPixelRect(
                Math.toIntExact(left), Math.toIntExact(top),
                Math.toIntExact(right - left), Math.toIntExact(bottom - top)));
    }

    /// 是否完整包含另一矩形。
    public boolean contains(OutputPixelRect other) {
        return (long) x <= other.x && (long) y <= other.y
                && (long) x + width >= (long) other.x + other.width
                && (long) y + height >= (long) other.y + other.height;
    }
}
