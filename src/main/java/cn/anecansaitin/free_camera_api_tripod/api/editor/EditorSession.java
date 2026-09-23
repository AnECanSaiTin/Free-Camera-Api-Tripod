package cn.anecansaitin.free_camera_api_tripod.api.editor;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Pathc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 编辑器的一次会话。
///
/// 外部界面（例如附属 mod 用 Modern UI 搭的界面）通过它读取编辑器状态、触发编辑动作。
/// 这里只暴露只读模型与受控动作，不暴露主 mod 的内部实现，因此主 mod 内部重构不会波及外部界面。
///
/// 内置界面与外部界面后端拿到的是**同一个会话实例**，状态与编辑进度因此互通，
/// 中途更换界面不会丢失正在编辑的内容。
@NullMarked
public interface EditorSession {
    // region 只读状态

    /// 动画模型（只读）
    CameraAnimationc animation();

    /// 当前路径（只读）
    Pathc path();

    /// 动画总时长，单位秒
    float duration();

    /// 当前选中的轨道；未选中为 null
    @Nullable
    AnimationTrack selectedTrack();

    /// 当前选中的关键帧索引；未选中为 -1
    int selectedKeyIndex();

    /// 当前选中的路径点索引；未选中为 -1
    int selectedPathIndex();

    /// 是否处于路径模式（位置取自绑定的路径）；false 表示直接坐标模式
    boolean pathMode();

    // endregion

    // region 时间轴视图

    /// 时间轴缩放：每秒对应多少像素
    float pixelsPerSecond();

    /// 调整时间轴缩放；超出允许范围时由实现自行夹取
    void pixelsPerSecond(float pixelsPerSecond);

    /// 时间轴左端对应的时刻，单位秒
    float viewStartTime();

    /// 平移时间轴；早于 0 秒的位置由实现自行夹取
    void viewStartTime(float viewStartTime);

    /// 时间轴主刻度间隔，单位秒。界面据此画刻度，保证刻线间距不小于约 56 像素
    float majorTickStep();

    /// 主刻度上的时间文本
    String formatTick(float time, float step);

    /// 按吸附设置把时间对齐到网格；吸附关闭时只保证不为负
    float snapTime(float time);

    // endregion

    // region 播放

    /// 播放头当前时刻，单位秒
    float playheadTime();

    /// 是否正在播放
    boolean playing();

    /// 在播放与暂停之间切换
    void togglePlay();

    /// 停止播放并把播放头归零
    void stopPlayback();

    /// 把播放头移动到指定时刻
    void seek(float time);

    // endregion

    // region 选择与编辑

    /// 选中指定 id 的轨道
    void selectTrack(String trackId);

    /// 选中指定索引的关键帧
    void selectKey(int index);

    /// 选中路径上的第 index 个节点
    boolean selectPathNode(int index);

    /// 切换到路径模式
    void switchToPathMode();

    /// 切换到直接坐标模式
    void switchToCoordinateMode();

    /// 把当前相机姿态记录为一个路径点
    void recordPathNode();

    /// 在当前选中轨道的指定时刻插入关键帧，返回其索引；失败返回 -1
    int addKey(float time);

    /// 移动当前选中轨道上第 index 个关键帧到指定时刻，返回新的索引；失败返回 -1
    int moveKey(int index, float time);

    /// 删除当前选中轨道的第 index 个关键帧，返回是否删除成功
    boolean removeKey(int index);

    /// 删除当前选中的关键帧，返回是否删除成功
    boolean removeSelectedKey();

    // endregion

    // region 二次确认

    /// 待用户确认的操作提示（例如切换运动模式会删掉位置关键帧）；没有则为 null。
    ///
    /// 界面要在用户看得到的地方把它显示出来，并给出「确认」「取消」两个出口——
    /// 有些动作（如切换运动模式）只有确认之后才会真正执行，界面不呈现就等于点了没反应。
    @Nullable
    Component pendingConfirm();

    /// 确认并执行待确认的操作
    void confirmPending();

    /// 放弃待确认的操作
    void dismissPending();

    // endregion

    // region 提示与关闭

    /// 在编辑器状态栏显示一条提示
    void notify(Component message);

    /// 当前状态提示；没有则为 null
    @Nullable
    Component statusMessage();

    /// 关闭编辑器界面
    void close();

    // endregion
}
