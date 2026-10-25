package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationDocumentStore;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.SessionSwitch;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceEntry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 把资源载入结果接入文档发布边界。
///
/// [LoadResult.Loaded] 只表示结构解码成功，不能直接当作已发布文档。本类执行：
/// 结构结果 → 统一文档校验（由文档 store 的 validator 完成）→ `switchDocument`。
/// `LoadResult.Rejected` 直接保留资源诊断；换档失败返回文档诊断，不修改 store。
@NullMarked
public final class CoreAnimationLoader {

    private final AnimationDocumentStore store;

    /// @param store 唯一发布入口；载入成功后仍须通过 `switchDocument`
    public CoreAnimationLoader(AnimationDocumentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /// 从资源服务载入并发布。
    ///
    /// 这是 A1 的端到端入口：资源服务只负责结构解码，统一校验与换档仍由 store 完成。
    public DocumentLoadOutcome load(AnimationResourceService resources,
                                    ResourceId resource,
                                    Revision expectedRevision,
                                    SessionSwitch reason) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(resource, "resource");
        return load(resources.load(resource), expectedRevision, reason);
    }

    /// 将一个载入结果发布到 store。
    ///
    /// @param loaded            资源服务的载入结果
    /// @param expectedRevision  调用方读取的当前发布序号
    /// @param reason            换档原因（LOAD / RESTORE / NETWORK 等）
    /// @return 已发布快照或带诊断的拒绝；不会返回部分文档
    public DocumentLoadOutcome load(LoadResult loaded, Revision expectedRevision, SessionSwitch reason) {
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        Objects.requireNonNull(reason, "reason");

        if (loaded instanceof LoadResult.Rejected rejected) {
            return new DocumentLoadOutcome.Rejected(rejected.report().diagnostics());
        }

        LoadResult.Loaded accepted = (LoadResult.Loaded) loaded;
        EditResult result = store.switchDocument(accepted.document(), expectedRevision, reason);

        return switch (result) {
            case EditResult.Applied applied -> new DocumentLoadOutcome.Published(
                    applied.snapshot(), accepted.entry(), applied.diagnostics());
            case EditResult.Rejected rejected -> new DocumentLoadOutcome.Rejected(rejected.diagnostics());
        };
    }

    /// 载入 / 发布结果。
    public sealed interface DocumentLoadOutcome {

        /// 已通过结构解码、统一校验并成功换档。
        ///
        /// @param snapshot    新发布快照
        /// @param entry       资源元数据
        /// @param diagnostics 非错误级校验诊断
        record Published(AnimationSnapshot snapshot,
                         AnimationResourceEntry entry,
                         List<Diagnostic> diagnostics) implements DocumentLoadOutcome {

            public Published {
                Objects.requireNonNull(snapshot, "snapshot");
                Objects.requireNonNull(entry, "entry");
                diagnostics = List.copyOf(diagnostics);
            }
        }

        /// 解码拒绝或换档拒绝；文档、历史与序号均未改变。
        record Rejected(List<Diagnostic> diagnostics) implements DocumentLoadOutcome {

            public Rejected {
                diagnostics = List.copyOf(diagnostics);

                if (diagnostics.isEmpty()) {
                    throw new IllegalArgumentException(
                            "DocumentLoadOutcome.Rejected requires at least one diagnostic");
                }
            }
        }
    }
}
