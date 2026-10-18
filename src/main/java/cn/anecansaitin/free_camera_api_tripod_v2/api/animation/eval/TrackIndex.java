package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// 绑定快照的轨道索引：`TrackId` → 轨道及其所属机位（主设计 §3.4、§6.4 A3）。
///
/// eval 绑定时一次性建立，之后按 ID 查询不再线性扫描文档。索引必须：
///
/// - **先验证唯一性**：同一 `TrackId` 重复时不得静默覆盖，必须通过 [diagnostics] 报告；
/// - **携带所属机位**：被引用轨道的公式读取空间事实时，求值与缓存使用该机位上下文；
/// - **随快照重绑**：换档、撤销、预览候选变化时建立新索引，不串旧文档；
/// - 仍以 `Optional` 返回，具体分配次数不属于契约承诺。
@NullMarked
public interface TrackIndex {

    /// 本索引绑定的快照；求值上下文必须与它同源。
    AnimationSnapshot snapshot();

    /// 按 `TrackId` 查索引条目；不存在或重复冲突时返回空（冲突见 [diagnostics]）。
    Optional<Entry> find(TrackId trackId);

    /// 建立索引时发现的重复 ID / 非法引用诊断；无问题返回空列表。
    List<Diagnostic> diagnostics();

    /// 一个索引条目：轨道与所属机位。
    ///
    /// @param track      轨道（含机位通道、状态轨道与文档级轨道）
    /// @param ownerRigId 所属机位；文档级轨道 / 相机来源轨道为空
    record Entry(AnimationTrack track, @Nullable RigId ownerRigId) {
    }
}
