package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

/// 网络播放请求的规模上限（主设计 §8.3：协议版本及必要的数据规模限制）。
///
/// 解码必须在**遍历 / 分配之前或过程中**检查上限，不能先完整读入再判断：
/// 随包文档可能很大，先分配再拒绝会把规模限制变成摆设。超限属于可报告的请求失败，
/// 不是“空文档”或“默认值”。
///
/// @param maxDocumentBytes 随包文档序列化后的最大字节数，正数
/// @param maxRigOverrides  按机位覆盖的最大条目数，非负
/// @param maxTotalBytes    整个请求负载的最大字节数，正数；必须不小于上限中的文档预算
@NullMarked
public record PlaybackLimits(
        int maxDocumentBytes,
        int maxRigOverrides,
        int maxTotalBytes) {

    public PlaybackLimits {
        if (maxDocumentBytes <= 0) {
            throw new IllegalArgumentException(
                    "PlaybackLimits maxDocumentBytes must be positive: " + maxDocumentBytes);
        }

        if (maxRigOverrides < 0) {
            throw new IllegalArgumentException(
                    "PlaybackLimits maxRigOverrides must be non-negative: " + maxRigOverrides);
        }

        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException(
                    "PlaybackLimits maxTotalBytes must be positive: " + maxTotalBytes);
        }

        if (maxTotalBytes < maxDocumentBytes) {
            throw new IllegalArgumentException(
                    "PlaybackLimits maxTotalBytes must be >= maxDocumentBytes: "
                            + maxTotalBytes + " < " + maxDocumentBytes);
        }
    }
}
