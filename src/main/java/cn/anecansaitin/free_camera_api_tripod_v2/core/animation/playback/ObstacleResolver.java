package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 避障位置修正端口。
///
/// 帧准备在完成参考系与注视之后，把“碰撞起点 / 期望位置 / 保留距离”交给宿主实现；
/// 宿主使用实际世界碰撞查询返回修正后的位置，或明确报告不可用。本接口不读世界、不做
/// 插值，也不把失败伪装成未启用避障。
@NullMarked
@FunctionalInterface
public interface ObstacleResolver {

    /// 修正一路机位的位置。
    ///
    /// @param request 请求
    /// @return 修正成功或不可用
    Result resolve(Request request);

    /// 一次避障请求。
    ///
    /// @param rigId    机位身份，用于诊断
    /// @param origin   碰撞起点：局部机位为解析锚点，世界机位为本帧基础相机位置
    /// @param expected 期望位置
    /// @param margin   保留距离，单位格；有限且非负
    record Request(RigId rigId, Vec3 origin, Vec3 expected, double margin) {

        public Request {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(expected, "expected");

            if (!Double.isFinite(margin) || margin < 0.0) {
                throw new IllegalArgumentException(
                        "Obstacle margin must be finite and non-negative: " + margin);
            }
        }
    }

    /// 修正结果。
    sealed interface Result permits Result.Adjusted, Result.Unavailable {

        /// 修正后的位置。
        ///
        /// @param position 新位置；必须有限
        record Adjusted(Vec3 position) implements Result {
            public Adjusted {
                Objects.requireNonNull(position, "position");
            }
        }

        /// 宿主无法执行避障。
        ///
        /// @param reason 不可用原因
        record Unavailable(String reason) implements Result {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
