package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 诊断的作用域：显式区分“候选 / 已发布文档”与“文件 / 解码资源”
/// （主设计 §4.1、§8.2；§6.5 C4 裁决）。
///
/// 不能用一个可空基版本同时表达两种作用域：
///
/// - [Document]：诊断针对某份文档的某个发布版本，允许对象 / 字段定位与修复；
///   异步结果、换档后相同 ID 的场景必须靠版本判断是否过期，不能定位到新对象。
/// - [File]：诊断针对文件 / 解码资源，不属于任何已发布文档；不允许对象定位与修复，
///   避免把文件诊断伪装成可提交候选诊断。
@NullMarked
public sealed interface DiagnosticScope permits DiagnosticScope.Document, DiagnosticScope.File {

    /// 候选 / 已发布文档作用域。
    ///
    /// @param revision 诊断适用的发布版本
    record Document(Revision revision) implements DiagnosticScope {
        public Document {
            Objects.requireNonNull(revision, "revision");
        }
    }

    /// 文件 / 解码资源作用域。
    ///
    /// @param resource 相对资源身份，非空
    record File(ResourceId resource) implements DiagnosticScope {
        public File {
            Objects.requireNonNull(resource, "resource");
        }
    }
}
