package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/// 动画资源服务：资源定位、字节 / Ops 转换、Codec 调用与错误报告（主设计 §8.1、§8.2）。
///
/// 存储实现只负责资源定位与编解码，不解释文档子对象字段；载入结果仍须经过文档强制校验
/// 后才能发布。资源根、本地路径映射与越界 / 绝对路径安全校验由实现负责，
/// [ResourceId] 的非空白检查不等于路径安全已经实现。
@NullMarked
public interface AnimationResourceService {

    /// 载入资源；失败不返回部分文档。
    LoadResult load(ResourceId resource);

    /// 同步保存文档；使用临时文件与原子替换，失败不得留下部分正式文件。
    SaveResult save(ResourceId resource, CameraAnimation document);

    /// 异步保存；完成回调恰好一次，运行线程由实现决定。
    ///
    /// 自动恢复写入必须走独立恢复槽，不能把自动版本误报为用户正式保存。
    void saveAsync(ResourceId resource, CameraAnimation document, Consumer<SaveResult> completion);

    /// 查询单个资源元数据；不存在时为空。
    Optional<AnimationResourceEntry> entry(ResourceId resource);

    /// 当前可列出的资源元数据；顺序稳定。
    List<AnimationResourceEntry> entries();
}
