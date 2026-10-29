package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import org.jspecify.annotations.NullMarked;

/// 轨道池公式的显式采样读取端口。
///
/// 宿主持有世界事实并在帧准备阶段提供读数；轨道池求值器不隐式读取世界。没有端口时
/// `sample(...)` 返回结构化错误，不能当 0，也不能静默跳过。
@FunctionalInterface
@NullMarked
public interface TrackPoolSampleSource {

    /// 没有宿主采样端口：所有读数均为可兜底的“数据不可用”。
    TrackPoolSampleSource NONE = (name, inputs) -> new SampleReading.Unknown(
            cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleUnknownReason.DATA_UNAVAILABLE, 0);

    /// 读取一个采样声明。
    ///
    /// @param name   采样声明名
    /// @param inputs 本帧显式输入
    /// @return 读数；不可用时返回 `Unknown`，采集失败返回 `CollectionError`
    SampleReading read(String name, TrackPoolEvaluationInputs inputs);
}
