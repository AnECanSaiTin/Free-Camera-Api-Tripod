package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一帧事实的准备请求（主设计 §2.2、§3.4、§7.2、§7.7）。
///
/// ## 显式输入
///
/// - `snapshot`：本帧要准备的快照；采样声明与机位空间配置从这里读，不能隐式使用当前 store；
/// - `animationTime`：本帧的**动画时间**（秒），有限且非负。它是播放时间，不是游戏刻：
///   同一游戏刻可以定位到 9 秒或 10 秒，两个时刻的生效注视状态不同（复核 R49-H1）；
/// - `dimension` / `worldInstanceId`：世界身份；同维度重进世界必须是新的世界实例身份，
///   解析与采样缓存都据此失效（主设计 §3.4）；
/// - `gameTick` / `partialTick`：采样时间元数据与实体空间插值；
/// - `worldTime`：世界日时刻与昼夜长度。公式内建量 `wt` 只由这里归一化一次，
///   通道与布局共用同一值；昼夜长度可由数据包修改，不能写死 24000；
/// - `plan`：宿主按动画时间、播放覆盖与实际依赖算出的[FrameFactsPlan]，
///   逐机位给出锚点 / 目标需求与生效注视状态。请求不再用一个 `Set<RigId>` 表示“需要这个机位”：
///   它无法区分“只需要锚点”与“锚点加目标”，也无法说明生效的覆盖结果；
/// - `session`：本帧共享执行会话；解析与采集的工作量从这里扣减（复核 R44-N1 的记账规则）。
///
/// ## 不进入文档
///
/// 本请求与返回的 [FrameFacts] 都是一次运行期绑定，不进入文档、历史、网络或撤销。返回的
/// 事实必须保留本请求 `snapshot` 的原引用；同 revision 的新快照不能冒充来源（R51-N1）。
///
/// @param snapshot        本帧快照
/// @param animationTime   动画播放时间（秒），有限且非负
/// @param dimension       当前维度
/// @param worldInstanceId 世界实例身份，非负
/// @param gameTick        采集时的游戏刻，非负
/// @param partialTick     帧内插值比例，`[0, 1)`
/// @param worldTime       世界日时刻与昼夜长度；`wt` 的入口事实
/// @param plan            本帧空间准备计划；机位必须存在于快照
/// @param session         本帧共享执行会话
@NullMarked
public record FrameFactsRequest(
        AnimationSnapshot snapshot,
        float animationTime,
        Identifier dimension,
        long worldInstanceId,
        long gameTick,
        float partialTick,
        WorldTime worldTime,
        FrameFactsPlan plan,
        EvaluationSession session) {

    public FrameFactsRequest {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(worldTime, "worldTime");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(session, "session");

        if (!Float.isFinite(animationTime) || animationTime < 0f) {
            throw new IllegalArgumentException(
                    "FrameFactsRequest animationTime must be finite and non-negative: " + animationTime);
        }

        if (worldInstanceId < 0) {
            throw new IllegalArgumentException(
                    "FrameFactsRequest worldInstanceId must be non-negative: " + worldInstanceId);
        }

        if (gameTick < 0) {
            throw new IllegalArgumentException(
                    "FrameFactsRequest gameTick must be non-negative: " + gameTick);
        }

        if (!Float.isFinite(partialTick) || partialTick < 0f || partialTick >= 1f) {
            throw new IllegalArgumentException(
                    "FrameFactsRequest partialTick must be finite and in [0, 1): " + partialTick);
        }

        // 未知 RigId 是调用方参数错误：计划只能覆盖本快照里存在的机位（复核 R49-H1）。
        for (RigId rigId : plan.rigIds()) {
            if (snapshot.document().rig(rigId).isEmpty()) {
                throw new IllegalArgumentException(
                        "FrameFactsRequest plan references a rig missing from the snapshot: " + rigId);
            }
        }
    }
}
