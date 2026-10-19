package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 扩展轨道的纯派生时间轴能力（主设计 §4.1、§3.1）。
///
/// 需要接入统一时间轴编辑的第三方轨道额外实现本接口，只暴露“有哪些稳定键、在什么时刻”，
/// 不发放原地增删移动方法。实际编辑仍由类型能力把请求转换成 `EditOp`，不能直接修改轨道。
/// 实现必须返回按时间严格递增、时间有限非负且 KeyId 唯一的不可变列表。
@NullMarked
public interface ExtensionTimelineTrack extends AnimationTrack.ExtensionTrack {

    /// 派生时间轴键；按时间严格递增。
    List<TimelineKey> timelineKeys();

    /// 一个扩展轨道键的只读视图。
    ///
    /// @param keyId 稳定键身份
    /// @param time  时间，秒；有限且非负
    record TimelineKey(KeyId keyId, float time) {

        public TimelineKey {
            if (keyId == null) {
                throw new IllegalArgumentException("TimelineKey keyId must not be null");
            }

            if (!Float.isFinite(time) || time < 0f) {
                throw new IllegalArgumentException("TimelineKey time must be finite and non-negative: " + time);
            }
        }
    }
}
