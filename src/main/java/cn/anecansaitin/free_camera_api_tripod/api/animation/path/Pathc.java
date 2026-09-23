package cn.anecansaitin.free_camera_api_tripod.api.animation.path;

import org.joml.Vector3f;

/// 路径的只读视图。
///
/// 播放器、路径渲染这类只取点的地方拿这个接口即可，增删节点与清空由
/// {@link Path} 提供。
public interface Pathc {
    String name();

    int size();

    /// 第 index 个节点的只读视图；越界抛出 {@link IndexOutOfBoundsException}
    PathNodec node(int index);

    /// 沿路径的总长度（单位：格）
    double totalLength();

    /// 取距起点 distance 格处的位置，写入 dest 并返回它；路径为空时抛出 {@link IllegalStateException}
    Vector3f evaluate(float distance, Vector3f dest);
}
