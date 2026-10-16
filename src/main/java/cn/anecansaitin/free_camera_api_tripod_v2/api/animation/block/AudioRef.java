package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.AudioRefId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 参考音轨的**创作数据**引用（主设计 §7.8，D25）。
///
/// ## 本批只定身份与边界，不实现
///
/// 音频整体安排在**最终音频阶段**（A0–A2）：全部非音频阶段验收通过后才实施。
/// 前期只保留文档模型与服务边界，因此本 record 只有数据结构，没有 Codec、
/// 没有解码、没有播放路径，也不要求音频字段参与前期验收。
///
/// ## 音频文件不随文档打包
///
/// 只保存**资源引用 + 内容哈希 + 编辑参数**。文件本身不打包（版权与体积），
/// 分享时显示缺失或哈希不匹配，并允许作者显式重绑定（主设计 §7.8）。
///
/// ## 创作数据与设备状态的分界
///
/// | 属于本 record（创作数据） | 不属于（运行状态） |
/// | --- | --- |
/// | 资源引用、内容哈希 | 设备实际播放位置 |
/// | 动画起始偏移、源片段范围、增益 | 解码进度、波形缓存 |
/// | | 资源诊断结果 |
///
/// 后者不进撤销栈、不进文档；音轨面板读会话音频状态而不是操作声源
/// （音频设计 §8.2）。
///
/// ## 与标记的关系
///
/// 标记**独立于音轨**，不挂在本 record 上（主设计 §7.8）：标记是文档级一等概念，
/// 音轨被删除或重绑不应带走标记。
///
/// ## 时间映射
///
/// 设动画起始偏移为 `offset`、源片段为 `[in, out)`、片段内倍率为 1，则
/// `sourceTime = in + (animationTime - offset)`，只在 `[offset, offset + out - in)`
/// 活动（音频设计 §8.2）。[sourceDuration] 因此就是 `out - in`。
///
/// @param id            稳定身份；文档内唯一
/// @param asset         资源引用；不把调用方绝对路径作为可读取路径
/// @param contentHash   预期内容哈希；可为空（未绑定或旧数据），用于检测内容被替换
/// @param offset        动画时间轴上的起始偏移，秒；必须有限且非负
/// @param sourceIn      源片段起点，秒；必须有限且非负
/// @param sourceDuration 源片段时长，秒；必须有限且为正
/// @param gain          创作增益，线性倍数；必须有限且非负（与用户音量分离）
@NullMarked
public record AudioRef(
        AudioRefId id,
        String asset,
        @Nullable String contentHash,
        float offset,
        float sourceIn,
        float sourceDuration,
        float gain) {

    public AudioRef {
        if (asset.isBlank()) {
            throw new IllegalArgumentException("AudioRef asset must not be blank");
        }

        if (!Float.isFinite(offset) || offset < 0f) {
            throw new IllegalArgumentException("AudioRef offset must be finite and non-negative: " + offset);
        }

        if (!Float.isFinite(sourceIn) || sourceIn < 0f) {
            throw new IllegalArgumentException("AudioRef sourceIn must be finite and non-negative: " + sourceIn);
        }

        if (!Float.isFinite(sourceDuration) || sourceDuration <= 0f) {
            throw new IllegalArgumentException(
                    "AudioRef sourceDuration must be finite and positive: " + sourceDuration);
        }

        if (!Float.isFinite(gain) || gain < 0f) {
            throw new IllegalArgumentException("AudioRef gain must be finite and non-negative: " + gain);
        }
    }

    /// 源片段终点（源时间轴口径）。
    public float sourceOut() {
        return sourceIn + sourceDuration;
    }

    /// 本音轨在**动画时间轴**上的活动终点：`offset + sourceDuration`。
    ///
    /// 用于文档时长计算；片段内倍率为 1 是当前口径，未来若增加独立片段倍率，
    /// 必须新增显式创作字段，不能悄悄改变时长规则（音频设计 §8.2）。
    public float endTime() {
        return offset + sourceDuration;
    }

    /// 动画时刻是否落在本音轨的活动区间内，区间为左闭右开 `[offset, endTime)`。
    public boolean activeAt(float animationTime) {
        return animationTime >= offset && animationTime < endTime();
    }

    public AudioRef withGain(float newGain) {
        return new AudioRef(id, asset, contentHash, offset, sourceIn, sourceDuration, newGain);
    }

    public AudioRef withOffset(float newOffset) {
        return new AudioRef(id, asset, contentHash, newOffset, sourceIn, sourceDuration, gain);
    }

    /// 重新绑定资源：换引用与哈希，保留时间与增益编辑。
    ///
    /// 资源缺失时的正常修复路径；不产生悬空引用。
    public AudioRef rebind(String newAsset, @Nullable String newContentHash) {
        return new AudioRef(id, newAsset, newContentHash, offset, sourceIn, sourceDuration, gain);
    }
}
