package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 采样缓存键：一次求值结果的身份（主设计 §3.4）。
///
/// 键必须同时包含：
///
/// - **快照身份**与**求值上下文身份**（不同存储实例、预览候选不得混用）；
/// - **世界实例身份**（同维度重进世界也变化，不能由维度 / 游戏刻 / revision 推导）；
/// - **帧序号**与**采样时间**（同帧同轨的不同时刻不得命中同一键；时间按 `-0.0 → 0.0` 正规化）；
/// - **机位上下文**（共享符号读取空间事实时必带 `RigId`；`TrackRef` 使用被引用轨道的所属机位）；
/// - **轨道 / 曲线 ID**；
/// - **空间事实身份**与**采样表版本**。
///
/// 相等性只按这些字段判定；失败结果也使用同一个键缓存，不因 NaN 哨兵重复求值。
///
/// @param snapshotId      快照身份（区分存储实例，不等同发布序号）
/// @param contextId       求值上下文身份
/// @param worldInstanceId 世界实例身份，非负
/// @param frameIndex      帧序号，非负
/// @param sampleTime      采样时间，秒；必须有限且非负，`-0.0` 规范化为 `0.0`
/// @param rigId           机位上下文；文档级求值可为空
/// @param trackId         轨道 / 曲线 ID
/// @param factsIdentity   空间事实身份
/// @param sampleTableVersion 采样表版本
@NullMarked
public record EvaluationKey(
        long snapshotId,
        long contextId,
        long worldInstanceId,
        long frameIndex,
        float sampleTime,
        @Nullable RigId rigId,
        TrackId trackId,
        long factsIdentity,
        long sampleTableVersion) {

    public EvaluationKey {
        if (snapshotId < 0 || contextId < 0 || worldInstanceId < 0 || frameIndex < 0
                || factsIdentity < 0 || sampleTableVersion < 0) {
            throw new IllegalArgumentException("EvaluationKey numeric identities must be non-negative");
        }

        if (!Float.isFinite(sampleTime) || sampleTime < 0f) {
            throw new IllegalArgumentException(
                    "EvaluationKey sampleTime must be finite and non-negative: " + sampleTime);
        }

        // -0.0 规范化；其余有效时间不变（主设计 §4.4）
        if (sampleTime == 0f) {
            sampleTime = 0f;
        }

        Objects.requireNonNull(trackId, "trackId");
    }

    /// 由显式采样请求生成缓存键：统一入口，避免调用方各自拼装身份。
    ///
    /// `snapshotId` 必须是独立分配的运行期身份，不能使用 revision；世界实例身份取自
    /// 请求上下文的 [FrameFacts#worldInstanceId]，采样时间直接使用请求值。
    public static EvaluationKey from(
            CurveSampleRequest request,
            long snapshotId,
            long frameIndex,
            long factsIdentity,
            long sampleTableVersion) {
        Objects.requireNonNull(request, "request");

        return new EvaluationKey(
                snapshotId,
                request.context().contextId(),
                request.context().facts().worldInstanceId(),
                frameIndex,
                request.sampleTime(),
                request.rigId(),
                request.trackId(),
                factsIdentity,
                sampleTableVersion);
    }
}
