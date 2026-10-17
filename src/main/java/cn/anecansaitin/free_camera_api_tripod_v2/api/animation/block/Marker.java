package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import org.jspecify.annotations.NullMarked;

/// 文档级标记：时刻 + 标签 + 种类（主设计 §3.1）。
///
/// ## 文档级而不是每机位一份
///
/// 标记对齐的是**时间**，与哪台机位出画面无关；每机位复制一份会让“同一处卡点”
/// 在 N 个机位里出现 N 次，音轨与吸附也会各说各话。
///
/// ## 标记没有直接执行副作用，但仍参与时长推导
///
/// 标记本身**不进入播放执行路径**：它不产生 Tick 事件、不执行指令、不发声
/// （需要“播放经过就触发”就用文档级 Tick 轨道，主设计 §7.4）。
///
/// 但“不触发事件”不等于“不影响播放范围”：按当前设计规则，标记仍参与
/// [CameraAnimation#duration] 的推导（主设计 §3.1）。是否分离播放时长与编辑可视范围
/// 属于另行裁决，不能只凭本节前半句删除标记的时长贡献。
///
/// ## 时间口径
///
/// 时间**以秒存储**，不做隐式量化（主设计 §4.4）。标记可以是子帧位置：
/// 音频卡点常常落在帧之间，强制量化会破坏对齐。
///
/// ## 自动候选必须经作者确认
///
/// 自动节拍检测产生的标记**只是候选**，必须由作者显式确认后才提交为文档标记
/// （主设计 §7.8、§8.4 的同一条口径）。因此候选状态属于编辑器会话，不属于本类型——
/// 文档里的每一个标记都是作者已经确认过的。
///
/// @param id    稳定身份；文档内唯一，移动与改名都保留它
/// @param time  时刻，单位秒；必须有限且非负
/// @param label 标签文本；可为空串（允许先打点、后命名），但不为 null
/// @param kind  种类
@NullMarked
public record Marker(MarkerId id, float time, String label, MarkerKind kind) {

    public Marker {
        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException("Marker time must be finite and non-negative: " + time);
        }

        // -0.0 规范化；其余有效时间不变（主设计 §4.4）
        if (time == 0f) {
            time = 0f;
        }
    }

    /// 新建无标签标记。
    public static Marker at(MarkerId id, float time) {
        return new Marker(id, time, "", MarkerKind.GENERIC);
    }

    public Marker withTime(float newTime) {
        return new Marker(id, newTime, label, kind);
    }

    public Marker withLabel(String newLabel) {
        return new Marker(id, time, newLabel, kind);
    }

    public Marker withKind(MarkerKind newKind) {
        return new Marker(id, time, label, newKind);
    }

    /// 换身份，其余不变。只在**复制标记**时由 `IdGenerator` 配套使用。
    public Marker withId(MarkerId newId) {
        return new Marker(newId, time, label, kind);
    }
}
