package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.core.animation.track.CurveTrack;
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
public class CameraAnimation {
    /// 位置通道，取值为沿 {@link Path} 的弧长距离
    public static final String CHANNEL_POSITION = "position";
    public static final String CHANNEL_ROTATION_X = "rotation.x";
    public static final String CHANNEL_ROTATION_Y = "rotation.y";
    public static final String CHANNEL_ROTATION_Z = "rotation.z";
    public static final String CHANNEL_FOV = "fov";

    private String name;
    private final Clip clip = new Clip();
    private Path path;
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
    public List<AnimationTrack> tracks() {
        List<AnimationTrack> all = new ArrayList<>(tracks.size() + extensionTracks.size());
        all.addAll(tracks.values());
        all.addAll(extensionTracks);
        return all;
    }

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

    public Path path() {
        return path;
    }

    public void path(Path path) {
        this.path = path;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }
}
