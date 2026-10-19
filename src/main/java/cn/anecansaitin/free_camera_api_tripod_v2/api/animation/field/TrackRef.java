package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NullMarked;

/// 轨道读数：取一条曲线轨道在当前位置上的值。
///
/// **不实现 `NumberSource`**，所以它在类型上就进不了关键帧字段（主设计 §6.1）。
/// 它只出现在变量与自定义函数里，供“变量读曲线”使用。
///
/// ## 机位上下文
///
/// `TrackId` 在整个文档内唯一（主设计 §3.1），因此本引用**不需要**“机位 + 轨道”二元键。
/// 但求值仍要知道机位上下文：
///
/// - 被引用轨道所属机位决定它读哪一份值；
/// - 若该轨道上的公式读取了空间事实，求值与缓存必须带 `RigId`（主设计 §6.1）。
///
/// 没有机位上下文的入口（例如全局符号面板试算）必须**显式指定机位**，或者如实报告
/// “缺少机位上下文”，不能悄悄挑一个机位求值。
///
/// @param trackId 被读取的曲线轨道 ID
@NullMarked
public record TrackRef(TrackId trackId) implements ValueSource {

    /// 结构编码：`{"type": "track_ref", "track_id": "..."}` 的 `track_id` 部分。
    public static final MapCodec<TrackRef> MAP_CODEC =
            TrackId.CODEC.fieldOf("track_id").xmap(TrackRef::new, TrackRef::trackId);

    /// 独立解析本分支时使用的 Codec。
    public static final Codec<TrackRef> CODEC = MAP_CODEC.codec();

    @Override
    public String toString() {
        return "track(" + trackId.value() + ")";
    }
}
