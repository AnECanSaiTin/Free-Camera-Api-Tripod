package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;

/// 一帧的世界时间事实：当前世界日内的时刻与完整昼夜长度。
///
/// ## 为什么不写死 24000
///
/// 旧实现把 `wt` 定义成 `getOverworldClockTime() % 24000 / 24000`，但当前版本的昼夜长度
/// **不是固定值**，可被数据包修改。因此宿主必须同时提供 [dayTime] 与 [dayLength]，
/// 由本类型统一归一化，不能在各求值入口各自除以 24000 或各自选择单位。
///
/// [progress] 是 `wt` 的唯一来源：通道公式与布局公式都通过
/// [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts#worldTime]
/// 读取同一个值，避免“同一帧同一公式在不同入口得到不同 `wt`”。
///
/// ## 范围
///
/// [dayTime] 允许任意整数（按 [dayLength] 取模），以兼容宿主给出的累计时刻；
/// [dayLength] 必须是正数。归一化结果恒在 `[0, 1)`。
///
/// @param dayTime  当前世界日内的累计时刻，单位游戏刻；按 [dayLength] 取模
/// @param dayLength 完整昼夜长度，单位游戏刻；必须为正
@NullMarked
public record WorldTime(long dayTime, long dayLength) {

    public WorldTime {
        if (dayLength <= 0L) {
            throw new IllegalArgumentException(
                    "WorldTime dayLength must be positive: " + dayLength);
        }
    }

    /// 便捷构造：以当前时刻与昼夜长度建立事实。
    public static WorldTime of(long dayTime, long dayLength) {
        return new WorldTime(dayTime, dayLength);
    }

    /// 一天进度 `wt`，恒在 `[0, 1)`。
    ///
    /// 使用 `floorMod` 而不是 `%`，负的 [dayTime] 也会落到合法相位；
    /// 浮点转换若因舍入得到 `1.0f`，回退到最后一个小正数，保持半开区间。
    public float progress() {
        long phase = Math.floorMod(dayTime, dayLength);
        float value = (float) ((double) phase / (double) dayLength);

        return value >= 1f ? Math.nextDown(1f) : value;
    }
}