package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 时间轴吸附设置（主设计 §9.1、UI §6.3）。
///
/// 吸附是显式编辑动作，不隐式量化键时间；吸附后仍由会话检查最终时间与键冲突，拒绝时不
/// 静默移动其他键。
///
/// @param enabled          是否启用吸附
/// @param pixelTolerance   屏幕像素容差，非负有限
/// @param sources          启用的吸附源；顺序即稳定优先级
@NullMarked
public record SnappingSettings(boolean enabled, float pixelTolerance, List<SnapSource> sources) {

    public SnappingSettings {
        if (!Float.isFinite(pixelTolerance) || pixelTolerance < 0f) {
            throw new IllegalArgumentException(
                    "SnappingSettings pixelTolerance must be finite and non-negative: " + pixelTolerance);
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        sources = List.copyOf(sources);

        for (SnapSource source : sources) {
            Objects.requireNonNull(source, "source");
        }
    }

    /// 默认关闭吸附。
    public static SnappingSettings disabled() {
        return new SnappingSettings(false, 4f, List.of());
    }
}
