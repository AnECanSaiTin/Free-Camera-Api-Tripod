package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// 曲线轨道：一条 [Curve] 加它的身份与显示名（主设计 §3.1、§6.1）。
///
/// ## 两种用途
///
/// - **相机通道**（`channel` 非空）：驱动相机的某个量。相机只认识
///   `CameraChannel` 里固定的那几个语义键，因此这些轨道由框架创建，
///   语义键在**机位内唯一**（同一机位不允许两条轨道驱动同一个量）。
/// - **通用曲线**（`channel` 为空）：不驱动任何相机量，专门给变量读写用
///   （变量绑它就能取到这条曲线的值，见 `TrackRef`）。
///
/// “是不是相机通道”由 `channel` 是否为空回答，不靠名称前缀或 ID 猜测。
/// 语义键与 [TrackId] 分离之后，相机轨道也可以有作者改过的显示名与独立 ID，
/// 序列化按 `channel` 写出语义键，不靠 ID 反推。
///
/// ## 显示名与外观
///
/// 通道的标签与颜色默认由**通道描述注册表**派生（主设计 §8.1）；
/// 作者可编辑的轨道名称或外观覆盖**显式存储**在本 record 的 `name` 里。
/// 这样同一份 JSON 在不同注册表状态下不会得到不同的显示名——
/// 旧实现把标签与颜色取自全局注册表，属于隐藏的全局依赖。
///
/// ## 不可变
///
/// `curve` 是不可变值，替换曲线走 `withCurve`（返回派生对象）。
/// 本 record **不发放** `moveKey` / `key(Keyframe)` 一类原地方法：
/// 旧实现里 `Curve.key(Keyframe)` 在时间命中时会用传入对象覆盖已有关键帧，
/// 而移动关键帧会销毁传入对象（勘察报告 `curve/Curve.java:139,173`），
/// 那正是编辑层必须改走“构造完整候选 → 一次提交”的原因（主设计 §4.1）。
///
/// @param id      稳定身份，整份文档内唯一
/// @param name    用户可见名称
/// @param channel 驱动的相机通道；通用曲线为空
/// @param curve   曲线数据
@NullMarked
public record CurveTrack(TrackId id, String name, @Nullable CameraChannel channel, Curve curve)
        implements AnimationTrack {

    /// 相机通道轨道的类型标识。
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath("free_camera_api_tripod", "curve");

    public CurveTrack {
        if (name.isBlank()) {
            throw new IllegalArgumentException("CurveTrack name must not be blank");
        }
    }

    /// 按相机通道建轨道：ID 用语义键，名称用通道语义键。
    ///
    /// 这是**新建默认文档**的写法：默认文档里通道的 ID 就是语义键，
    /// 便于人工阅读与旧文件对照；作者后续改名只改 `name`，ID 保持稳定。
    public static CurveTrack ofChannel(CameraChannel channel) {
        return new CurveTrack(new TrackId(channel.key()), channel.key(), channel, Curve.EMPTY);
    }

    /// 按相机通道建轨道，但用独立 ID 与显式名称（作者新增通道时的形态）。
    public static CurveTrack ofChannel(TrackId id, String name, CameraChannel channel, Curve curve) {
        return new CurveTrack(id, name, channel, curve);
    }

    /// 新建一条通用曲线轨道：不驱动任何相机量，供变量读取。
    public static CurveTrack ofCurve(TrackId id, String name, Curve curve) {
        return new CurveTrack(id, name, null, curve);
    }

    @Override
    public Identifier type() {
        return TYPE;
    }

    /// 是否是相机通道轨道。为 `false` 时这条轨道只服务变量读数。
    public boolean isCameraChannel() {
        return channel != null;
    }

    /// 驱动的相机通道；通用曲线为空。
    public Optional<CameraChannel> channelOptional() {
        return Optional.ofNullable(channel);
    }

    @Override
    public float duration() {
        return curve.span();
    }

    @Override
    public int keyCount() {
        return curve.size();
    }

    /// 换曲线，其余不变。构造器会重新校验严格递增与时间唯一。
    public CurveTrack withCurve(Curve newCurve) {
        return new CurveTrack(id, name, channel, newCurve);
    }

    /// 换显示名，**ID 不变**——改名不会让绑定它的变量变成空指向。
    public CurveTrack withName(String newName) {
        return new CurveTrack(id, newName, channel, curve);
    }

    /// 换身份，其余不变。只在复制轨道时由 `IdGenerator` 配套使用。
    public CurveTrack withId(TrackId newId) {
        return new CurveTrack(newId, name, channel, curve);
    }
}
