package cn.anecansaitin.free_camera_api_tripod.api.animation.track;

import com.google.gson.JsonArray;
import org.jspecify.annotations.NullMarked;

/// 能以 JSON 读写自身键的轨道，让扩展轨道也能进存档。
///
/// 与 {@link TickTrack} 一样是按能力拆出来的可选接口：实现它的轨道在保存时会被写进动画 JSON 的
/// {@code extensionTracks} 段，读档时先由类型工厂造出实例，再把键交回这里恢复；不实现它就不落盘。
///
/// 只有"键"是轨道专有的：{@code type} 与 {@code id} 由存档层负责，实现只需管好这一个数组的读写。
@NullMarked
public interface JsonTrack {
    /// 把当前键写成 JSON 数组
    JsonArray writeKeys();

    /// 用 JSON 数组里的键替换现有键；空数组表示清空
    void readKeys(JsonArray keys);
}
