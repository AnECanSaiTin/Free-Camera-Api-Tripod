package cn.anecansaitin.free_camera_api_tripod.api.animation;

import com.google.gson.JsonObject;
import org.jspecify.annotations.NullMarked;

/// 能把自己写成 JSON 对象的数据类型。
///
/// 序列化从 `AnimationCodec` 的一大段 `valueToJson` / `readValue` 挪到了**数据类自己身上**：
/// 字段名、缺省值、以及"认不认 `{"track": …}`"这类格式细节都跟着数据走，
/// `AnimationCodec` 只剩容器（顶层 JSON、轨道表、路径表）的活。
///
/// 实现一般还配一个 `static read(JsonObject)`，两者写在一起，格式只有一处。
@NullMarked
public interface JsonSerializable {
    /// 把自己写成一个 JSON 对象
    JsonObject write();
}
