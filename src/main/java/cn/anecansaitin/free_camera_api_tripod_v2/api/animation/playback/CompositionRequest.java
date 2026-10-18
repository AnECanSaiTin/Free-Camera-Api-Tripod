package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

/// 合成请求：输出尺寸与是否保留玩家底图（主设计 §7.6）。
///
/// 渲染目标、尺寸与临时状态不进入 common 文档；本类型只描述一次合成所需的输出参数。
///
/// @param width            输出像素宽度，正整数
/// @param height           输出像素高度，正整数
/// @param includePlayerBase 是否保留正常玩家画面作为底图（来源为 Player 时必须为真）
@NullMarked
public record CompositionRequest(int width, int height, boolean includePlayerBase) {

    public CompositionRequest {
        if (width < 1) {
            throw new IllegalArgumentException("CompositionRequest width must be >= 1: " + width);
        }

        if (height < 1) {
            throw new IllegalArgumentException("CompositionRequest height must be >= 1: " + height);
        }
    }
}
