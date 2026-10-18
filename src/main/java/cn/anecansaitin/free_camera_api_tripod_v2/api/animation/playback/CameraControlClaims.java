package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;

/// 相机控制声明与仲裁（主设计 §7.5）。
///
/// 编辑器通过带生命周期的登记令牌声明接管；关闭会话或切换世界时释放令牌。多个声明按
/// 显式优先级和稳定登记顺序仲裁，只有获胜声明影响相机。没有编辑器覆盖声明时，由正式播放
/// 状态与 `cameraSource.at(t)` 决定接管。
///
/// ## 姿态协议
///
/// 接管声明必须提供 [PoseProvider]：宿主每帧在**同一时刻**读取获胜声明与其姿态，使用
/// [winningPose] 一次取得，不能先 [winner] 再晚些查询姿态，以免仲裁或释放穿插导致错配。
/// 姿态是自由视角相机覆盖，不是文档采样结果；释放令牌后该声明立即失效，宿主不得再调用其
/// provider，也不得继续使用释放前缓存的姿态。仲裁实现留到 `core`，本接口只固定读取协议。
@NullMarked
public interface CameraControlClaims {

    /// 登记一个接管声明；调用方负责在会话 / 世界生命周期结束时释放。
    ///
    /// @param priority    显式优先级，数值越大越优先；同优先级按登记顺序
    /// @param owner       不可变的声明者说明
    /// @param poseProvider 获胜时提供自由视角姿态；不得返回 null
    Claim register(int priority, LocalizedMessage owner, PoseProvider poseProvider);

    /// 释放令牌；重复释放或未知令牌由实现报告，不得影响其他声明。
    ///
    /// 释放后该声明的 provider 不得再被调用；[winningPose] 必须重新仲裁，不能复用其姿态。
    void release(Claim claim);

    /// 当前获胜声明；没有声明时为空。
    ///
    /// 仅用于查询仲裁结果；需要姿态时必须用 [winningPose] 取得同一帧的声明 + 姿态。
    Optional<Claim> winner();

    /// 同一帧的获胜声明与姿态；没有声明时为空。
    ///
    /// 实现须先确定获胜者，再对**该获胜者**的 provider 调用一次并同时返回。按捕获边界
    /// 线性化：捕获期间发生的释放或更高优先级登记影响**下一次**捕获，本次仍返回已捕获结果；
    /// 释放后不得发起新调用、不得复用旧缓存，已开始的本次捕获结果按帧协议最多应用一次。
    /// provider 不得触发文档发布或嵌套捕获；异常由宿主转为该帧准备失败，不能吞掉后回退玩家相机。
    Optional<ClaimedPose> winningPose();

    /// 自由视角姿态提供器；每帧至多调用一次，不得发布文档或嵌套捕获。
    @NullMarked
    @FunctionalInterface
    interface PoseProvider {

        /// 当前自由视角姿态。
        WorldPose pose();
    }

    /// 登记令牌。
    ///
    /// @param id       登记标识，非负
    /// @param priority 显式优先级
    /// @param owner    不可变的声明者说明
    record Claim(long id, int priority, LocalizedMessage owner) {

        public Claim {
            if (id < 0) {
                throw new IllegalArgumentException("CameraControlClaims.Claim id must be non-negative: " + id);
            }

            Objects.requireNonNull(owner, "owner");
        }
    }

    /// 同帧捕获的获胜声明与姿态。
    ///
    /// @param claim 获胜声明
    /// @param pose  该声明在捕获时提供的自由视角姿态
    record ClaimedPose(Claim claim, WorldPose pose) {

        public ClaimedPose {
            Objects.requireNonNull(claim, "claim");
            Objects.requireNonNull(pose, "pose");
        }
    }
}
