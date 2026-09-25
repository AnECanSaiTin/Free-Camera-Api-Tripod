package cn.anecansaitin.free_camera_api_tripod.api.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Clip;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationChannelRegistry;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/// 相机动画：编辑器与播放器共用的数据模型。
///
/// 由三部分组成：
/// - {@link Clip}：float 曲线集合（位置距离、旋转、FOV，以及未来的特效参数）
/// - {@link Path}：位置通道驱动的三维路径
/// - 扩展轨道：事件触发器、后处理特效等非曲线轨道，通过 {@link #addExtensionTrack} 接入
@NullMarked
public class CameraAnimation implements CameraAnimationc {
    /// 位置通道，取值为沿 {@link Path} 的弧长距离
    public static final String CHANNEL_POSITION = "position";
    public static final String CHANNEL_POSITION_X = "position.x";
    public static final String CHANNEL_POSITION_Y = "position.y";
    public static final String CHANNEL_POSITION_Z = "position.z";
    public static final String CHANNEL_ROTATION_X = "rotation.x";
    public static final String CHANNEL_ROTATION_Y = "rotation.y";
    public static final String CHANNEL_ROTATION_Z = "rotation.z";
    public static final String CHANNEL_FOV = "fov";

    /// 位置的运动模式。两者互斥：位置只能由路径给出，或由三个坐标通道直接给出。
    public enum MotionMode {
        /// 路径模式：位置 = 绑定的路径在「位置通道（弧长距离）」处的取值
        PATH,
        /// 直接坐标模式：位置 = position.x/y/z 三个通道在当前位置的取值
        COORDINATE
    }

    /// 路径模式下「位置通道」的取值口径。
    public enum DistanceMode {
        /// 绝对距离：通道取值是沿路径的弧长（单位：格）
        ABSOLUTE,
        /// 百分比：通道取值是 0~1 的路径进度，采样时再乘以路径总长
        PERCENT
    }

    private String name;
    private final Clip clip = new Clip();
    private Path path;
    private MotionMode motionMode = MotionMode.COORDINATE;
    private DistanceMode distanceMode = DistanceMode.ABSOLUTE;
    private final LinkedHashMap<String, CurveTrack> tracks = new LinkedHashMap<>();
    private final List<AnimationTrack> extensionTracks = new ArrayList<>();

    public CameraAnimation() {
        this("Camera");
    }

    public CameraAnimation(String name) {
        this.name = name;
        this.path = new Path("Path");
        addChannel(CHANNEL_POSITION);
        addChannel(CHANNEL_ROTATION_X);
        addChannel(CHANNEL_ROTATION_Y);
        addChannel(CHANNEL_ROTATION_Z);
        CurveTrack fov = addChannel(CHANNEL_FOV);
        // 位置与 FOV 各放一个初始键，保证新建动画即可直接预览
        tracks.get(CHANNEL_POSITION).addKey(0f, 0f);
        fov.addKey(0f, AnimationChannelRegistry.get(CHANNEL_FOV).defaultValue());
    }

    /// 添加或获取曲线通道，默认值与颜色取自 {@link AnimationChannelRegistry}
    public CurveTrack addChannel(String property) {
        CurveTrack existing = tracks.get(property);

        if (existing != null) {
            return existing;
        }

        AnimationChannelRegistry.Channel channel = AnimationChannelRegistry.get(property);
        Curve curve = new Curve();
        clip.addCurve(property, curve);
        CurveTrack track = new CurveTrack(property, curve, channel.label(), channel.color());
        tracks.put(property, track);
        return track;
    }

    public boolean removeChannel(String property) {
        if (tracks.remove(property) == null) {
            return false;
        }

        clip.removeCurve(property);
        return true;
    }

    public @Nullable CurveTrack track(String property) {
        return tracks.get(property);
    }

    /// 按 id 查找任意轨道（含扩展轨道）
    public @Nullable AnimationTrack trackById(String id) {
        CurveTrack curveTrack = tracks.get(id);

        if (curveTrack != null) {
            return curveTrack;
        }

        for (AnimationTrack track : extensionTracks) {
            if (track.id().equals(id)) {
                return track;
            }
        }

        return null;
    }

    /// 全部轨道（曲线轨道在前，扩展轨道在后），供时间轴按顺序展示
    @Override
    public List<AnimationTrack> tracks() {
        List<AnimationTrack> all = new ArrayList<>(tracks.size() + extensionTracks.size());
        all.addAll(tracks.values());
        all.addAll(extensionTracks);
        return all;
    }

    @Override
    public List<CurveTrack> curveTracks() {
        return List.copyOf(tracks.values());
    }

    /// 接入事件触发器、后处理特效等扩展轨道
    public void addExtensionTrack(AnimationTrack track) {
        extensionTracks.add(track);
    }

    public List<AnimationTrack> extensionTracks() {
        return Collections.unmodifiableList(extensionTracks);
    }

    @Override
    public float duration() {
        float duration = clip.duration();

        for (AnimationTrack track : extensionTracks) {
            duration = Math.max(duration, track.duration());
        }

        return duration;
    }

    public Clip clip() {
        return clip;
    }

    @Override
    public Path path() {
        return path;
    }

    public void path(Path path) {
        this.path = path;
    }

    @Override
    public MotionMode motionMode() {
        return motionMode;
    }

    /// 切换运动模式。两种模式互斥，切换时会把另一种模式的通道整段移除：
    /// 切到直接坐标模式会丢掉「位置通道（弧长距离）」的关键帧，切到路径模式会丢掉 position.x/y/z 的关键帧。
    /// 路径本身的数据不受影响，随时可以再绑回来。
    ///
    /// 切换只重建通道，**不自动补任何关键帧**：位置关键帧要由作者自己录，避免凭空多出关键帧。
    public void motionMode(MotionMode mode) {
        if (this.motionMode == mode) {
            return;
        }

        this.motionMode = mode;

        if (mode == MotionMode.COORDINATE) {
            removeChannel(CHANNEL_POSITION);
            addChannel(CHANNEL_POSITION_X);
            addChannel(CHANNEL_POSITION_Y);
            addChannel(CHANNEL_POSITION_Z);
            return;
        }

        removeChannel(CHANNEL_POSITION_X);
        removeChannel(CHANNEL_POSITION_Y);
        removeChannel(CHANNEL_POSITION_Z);
        addChannel(CHANNEL_POSITION);
    }

    /// 把某条轨道在轨道顺序里上下移动 offset 位，供时间轴拖拽排序使用。
    ///
    /// 顺序就是 {@link #tracks} 这个 LinkedHashMap 的顺序，序列化时也按它写出，
    /// 因此调整顺序同样会反映到保存的文件里。返回是否真的发生了移动。
    public boolean moveTrack(String id, int offset) {
        List<String> order = new ArrayList<>(tracks.keySet());
        int from = order.indexOf(id);

        if (from < 0) {
            return false;
        }

        int to = Math.clamp(order.size() - 1, 0, from + offset);

        if (to == from) {
            return false;
        }

        order.remove(from);
        order.add(to, id);

        LinkedHashMap<String, CurveTrack> reordered = new LinkedHashMap<>();

        for (String key : order) {
            reordered.put(key, tracks.get(key));
        }

        tracks.clear();
        tracks.putAll(reordered);
        return true;
    }

    /// 把一组轨道当作整体在顺序里上 / 下移动一格，用于拖动分组（折叠轴）排序。
    /// 已经顶到顺序的首端或末端时返回 false。
    public boolean moveTracks(List<String> ids, int offset) {
        List<String> order = new ArrayList<>(tracks.keySet());
        List<String> block = new ArrayList<>(order.stream().filter(ids::contains).toList());

        if (block.isEmpty()) {
            return false;
        }

        int from = order.indexOf(block.getFirst());
        int target = Math.clamp(order.size() - block.size(), 0, from + offset);

        if (target == from) {
            return false;
        }

        order.removeAll(block);
        order.addAll(target, block);

        LinkedHashMap<String, CurveTrack> reordered = new LinkedHashMap<>();

        for (String key : order) {
            reordered.put(key, tracks.get(key));
        }

        tracks.clear();
        tracks.putAll(reordered);
        return true;
    }

    /// 只改标记、不动通道：供反序列化使用。
    /// 读档时 JSON 里的通道集合本来就是该模式对应的那一套，再走 {@link #motionMode} 会把通道重建一遍。
    public void restoreMotionMode(MotionMode mode) {
        this.motionMode = mode;
    }

    @Override
    public DistanceMode distanceMode() {
        return distanceMode;
    }

    /// 只改口径标记、不换算已有键：供反序列化使用（文件里的键值本来就是该口径）
    public void restoreDistanceMode(DistanceMode mode) {
        this.distanceMode = mode;
    }

    /// 切换路径距离的取值口径，并把已有键换算到新口径，避免切换后动画整体跑偏。
    /// 绝对距离（格）与百分比（0~1）之间用路径总长换算；总长为 0 或键为空时不换算。
    public void distanceMode(DistanceMode mode) {
        if (this.distanceMode == mode) {
            return;
        }

        double total = path.totalLength();

        if (total > 0) {
            CurveTrack track = tracks.get(CHANNEL_POSITION);

            if (track != null) {
                Curve curve = track.curve();
                boolean toPercent = mode == DistanceMode.PERCENT;

                for (int i = 0; i < curve.size(); i++) {
                    Keyframe key = curve.key(i);

                    if (key == null) {
                        continue;
                    }

                    key.value((float) (toPercent ? key.value() / total : key.value() * total));
                }
            }
        }

        this.distanceMode = mode;
    }

    /// 把「位置通道」的取值换算成沿路径的弧长（绝对距离）
    @Override
    public float distanceToLength(float value) {
        return distanceMode == DistanceMode.PERCENT ? (float) (value * path.totalLength()) : value;
    }

    /// 用另一份动画的数据整体替换自身内容。
    ///
    /// 动画实例被播放器与编辑器各处持有，读档只能原地更新，不能换对象，因此这里逐个通道搬运。
    public void copyFrom(CameraAnimation other) {
        this.name = other.name;
        this.motionMode = other.motionMode;
        this.distanceMode = other.distanceMode;
        this.path = other.path;

        for (String property : new ArrayList<>(tracks.keySet())) {
            removeChannel(property);
        }

        for (CurveTrack track : other.curveTracks()) {
            tracks.put(track.id(), track);
            clip.addCurve(track.id(), track.curve());
        }
    }

    @Override
    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }
}
