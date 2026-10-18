package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 注视姿态捕获请求（主设计 §4.7）。
///
/// 工具在冻结上下文上求关闭注视前的有效姿态，再生成同时间的完整 XYZ 旋转常量键与注视 Off
/// 键；不增加运行期平滑、继承或世界历史。请求 / 预览覆盖固定为 Target 时必须先清除，不能
/// 仅写文档 Off 后把未关闭结果报告为成功。
///
/// [mode] 区分当前配置与“t 已有 Off 时显式使用紧邻左侧 Target”；[override] 必须携带实际
/// 生效的请求 / 预览覆盖，工具据此识别 `Replace` / `Disabled`，不能只根据文档 Aim 轨道
/// 猜测合法性。基版本必须与冻结上下文的快照版本一致。
///
/// @param rigId        目标机位
/// @param time         捕获时间，秒；有限且非负
/// @param mode         时间选择模式
/// @param override     请求 / 预览的实际注视覆盖；`Replace` 必须先清除
/// @param baseRevision 捕获所基于的发布序号，必须等于 `context.snapshot().revision()`
/// @param context      冻结的求值上下文（快照 + 事实 + 索引）
@NullMarked
public record AimCaptureRequest(
        RigId rigId,
        float time,
        AimCaptureMode mode,
        AimOverride override,
        Revision baseRevision,
        EvaluationContext context) {

    public AimCaptureRequest {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(override, "override");
        Objects.requireNonNull(baseRevision, "baseRevision");
        Objects.requireNonNull(context, "context");

        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException(
                    "AimCaptureRequest time must be finite and non-negative: " + time);
        }

        if (!baseRevision.equals(context.snapshot().revision())) {
            throw new IllegalArgumentException(
                    "AimCaptureRequest baseRevision must match context.snapshot().revision()");
        }
    }
}
