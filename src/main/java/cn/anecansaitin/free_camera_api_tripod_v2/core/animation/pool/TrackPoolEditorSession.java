package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.TrackPoolEditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// 轨道池编辑器会话：store、共享复制与删除影响面查询的唯一入口。
///
/// 不包含真实 UI 控件、输入法或渲染；只提供 UI 无关的会话操作，便于在纯 JVM 下测试。
@NullMarked
public final class TrackPoolEditorSession {

    private final TrackPoolDocumentStore store;
    private final TrackPoolRigCopier copier = new TrackPoolRigCopier();
    private final TrackPoolRigDeepCopier deepCopier = new TrackPoolRigDeepCopier();
    private final TrackPoolTrackDuplicator trackDuplicator = new TrackPoolTrackDuplicator();

    public TrackPoolEditorSession(TrackPoolDocumentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public TrackPoolSnapshot snapshot() {
        return store.snapshot();
    }

    public TrackPoolEditResult submit(TrackPoolEditOp op) {
        return store.submit(op, store.snapshot().revision());
    }

    public TrackPoolEditResult submit(TrackPoolEditOp op, Revision baseRevision) {
        return store.submit(op, baseRevision);
    }

    public boolean undo() {
        return store.undo();
    }

    public boolean redo() {
        return store.redo();
    }

    public void addListener(Consumer<TrackPoolSnapshot> listener) {
        store.addListener(listener);
    }

    public void removeListener(Consumer<TrackPoolSnapshot> listener) {
        store.removeListener(listener);
    }

    /// 轨道被哪些属性/来源绑定。
    public Set<TrackPoolDependencyGraph.Usage> deleteImpact(TrackId trackId) {
        return TrackPoolDependencyGraph.build(store.snapshot().document()).usages(trackId);
    }

    /// 轨道使用次数。
    public int trackUseCount(TrackId trackId) {
        return TrackPoolDependencyGraph.build(store.snapshot().document()).useCount(trackId);
    }

    /// 改文档名。
    public TrackPoolEditResult renameDocument(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Document name must not be blank");
        }

        return submit(new TrackPoolEditOp.ReplaceDocument(
                store.snapshot().document().withName(name)));
    }

    /// 以函数方式更新一台机位的属性绑定表。
    public TrackPoolEditResult updateRigProperties(
            RigId rigId, java.util.function.UnaryOperator<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties> update) {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(update, "update");
        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig rig = store.snapshot()
                .document().rig(rigId)
                .orElseThrow(() -> new IllegalArgumentException("Rig does not exist: " + rigId));
        var properties = Objects.requireNonNull(update.apply(rig.properties()), "updated properties");
        return submit(new TrackPoolEditOp.SetRigProperties(rigId, properties));
    }

    /// 加入一条轨道。
    public TrackPoolEditResult addTrack(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack track) {
        return submit(new TrackPoolEditOp.AddTrack(track));
    }

    /// 删除一条轨道；仍被引用时返回拒绝。
    public TrackPoolEditResult removeTrack(TrackId trackId) {
        return submit(new TrackPoolEditOp.RemoveTrack(trackId));
    }

    /// 改名；不支持能力的轨道返回拒绝。
    public TrackPoolEditResult renameTrack(TrackId trackId, String name) {
        return submit(new TrackPoolEditOp.RenameTrack(trackId, name));
    }

    /// 复制一条内建轨道并加入池；源轨道不变。
    public TrackPoolEditResult duplicateTrack(TrackId sourceId,
                                              TrackId newId,
                                              cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.IdGenerator ids) {
        var source = store.snapshot().document().track(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Track does not exist: " + sourceId));
        return submit(new TrackPoolEditOp.AddTrack(trackDuplicator.duplicate(source, newId, ids)));
    }

    /// 共享轨道复制机位，并作为一次原子编辑提交。
    ///
    /// @throws IllegalArgumentException 源机位缺失、新 RigId 冲突或名称非法
    public TrackPoolEditResult copyRigShared(RigId sourceId, RigId newId, String newName) {
        TrackPoolAnimation candidate = copier.copyShared(
                store.snapshot().document(), sourceId, newId, newName);
        return submit(new TrackPoolEditOp.ReplaceDocument(candidate));
    }

    /// 深复制机位引用轨道，并作为一次原子编辑提交。
    ///
    /// @throws IllegalArgumentException 源机位缺失、新 RigId 冲突或引用扩展轨道
    public TrackPoolEditResult copyRigDeep(RigId sourceId,
                                           RigId newId,
                                           String newName,
                                           cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.IdGenerator ids) {
        TrackPoolAnimation candidate = deepCopier.copyDeep(
                store.snapshot().document(), sourceId, newId, newName, ids);
        return submit(new TrackPoolEditOp.ReplaceDocument(candidate));
    }
}
