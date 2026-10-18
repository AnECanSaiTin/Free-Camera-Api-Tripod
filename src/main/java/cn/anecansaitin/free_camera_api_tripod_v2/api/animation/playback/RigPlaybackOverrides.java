package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 单路播放覆盖：锚点、跟随掩码、注视与避障（主设计 §7.1、§6.3）。
///
/// 空字段表示**继承文档 / 请求默认**，不是“显式禁用”：
///
/// - [anchor] 为空时使用机位文档默认锚点；局部机位没有默认锚点时必须在请求中提供，
///   否则准备阶段报缺输入。固定锚点解析失败后不得回退默认锚点。
/// - [followMask] 为空时继承机位 `Space.Local.followMask`。
/// - [aim] 使用 `AimOverride` 三态；`Disabled` 是显式禁用，回写文档 `Off` 不能关闭
///   固定 `Replace` 覆盖。
/// - [obstacle] 为空时继承文档默认避障设置。
///
/// @param anchor     锚点覆盖；空 = 继承文档默认
/// @param followMask 旋转跟随掩码覆盖；空 = 继承
/// @param aim        注视覆盖；默认 `Inherit`
/// @param obstacle   避障覆盖；空 = 继承
@NullMarked
public record RigPlaybackOverrides(
        @Nullable Anchor anchor,
        @Nullable RotationMask followMask,
        AimOverride aim,
        @Nullable ObstacleSettings obstacle) {

    /// 全部继承文档默认。
    public static final RigPlaybackOverrides INHERIT =
            new RigPlaybackOverrides(null, null, AimOverride.inherit(), null);

    public RigPlaybackOverrides {
        if (aim == null) {
            aim = AimOverride.inherit();
        }
    }
}
