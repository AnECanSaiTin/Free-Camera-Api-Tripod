package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;

/// 相机控制声明与仲裁（主设计 §7.5）。
///
/// 编辑器通过带生命周期的登记令牌声明接管；关闭会话或切换世界时释放令牌。多个声明按
/// 显式优先级和稳定登记顺序仲裁，只有获胜声明影响相机。没有编辑器覆盖声明时，由正式播放
/// 状态与 `cameraSource.at(t)` 决定接管。
@NullMarked
public interface CameraControlClaims {

    /// 登记一个接管声明；调用方负责在会话 / 世界生命周期结束时释放。
    ///
    /// @param priority 显式优先级，数值越大越优先；同优先级按登记顺序
    /// @param owner    不可变的声明者说明
    Claim register(int priority, LocalizedMessage owner);

    /// 释放令牌；重复释放或未知令牌由实现报告，不得影响其他声明。
    void release(Claim claim);

    /// 当前获胜声明；没有声明时为空。
    Optional<Claim> winner();

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
}
