package cn.anecansaitin.free_camera_api_tripod.api.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.ExpressionScope;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.SymbolTable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationChannelRegistry;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.RenamableTrack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 相机动画：编辑器与播放器共用的数据模型。
///
/// 由两部分组成：
/// - 曲线：位置距离、旋转、FOV，以及未来的特效参数。曲线**只存一份**，就在 {@link CurveTrack} 里；
///   按名取曲线走 {@link #curve(String)}，不必再从轨道表里过滤一遍
/// - {@link Path}：位置通道驱动的三维路径
///
/// 轨道表是曲线通道与扩展轨道（事件、特效、指令）共用的唯一有序表，顺序即时间轴上的显示顺序，
/// 也是序列化写出的顺序，因此拖拽排序对两类轨道一视同仁。
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
    private Path path;
    private MotionMode motionMode = MotionMode.COORDINATE;
    private DistanceMode distanceMode = DistanceMode.ABSOLUTE;
    private final LinkedHashMap<String, AnimationTrack> tracks = new LinkedHashMap<>();
    /// 动画级的名字定义：变量与自定义函数，以及它们之间的引用完整性。
    /// 表不认轨道，轨道改名 / 删除由这里显式通知它改指向（见 {@link SymbolTable}）
    private final SymbolTable symbols = new SymbolTable();

    public CameraAnimation() {
        this("Camera");
    }

    public CameraAnimation(String name) {
        this.name = name;
        this.path = new Path("Path");
        // 默认是直接坐标模式，位置就交给三个坐标通道——路径距离通道只在路径模式下才存在，
        // 反过来建的话时间轴会在坐标模式下列出一条「路径距离」，与实际口径对不上
        addChannel(CHANNEL_POSITION_X);
        addChannel(CHANNEL_POSITION_Y);
        addChannel(CHANNEL_POSITION_Z);
        addChannel(CHANNEL_ROTATION_X);
        addChannel(CHANNEL_ROTATION_Y);
        addChannel(CHANNEL_ROTATION_Z);
        addChannel(CHANNEL_FOV);
        // 坐标轴不放初始键：没有关键帧的轴不接管该轴，相机保持玩家所在的位置。
        // 补一个值为 0 的键会把相机直接搬到世界原点，那里往往没有加载区块，画面就只剩天空了
    }

    /// 添加或获取曲线通道，默认值与颜色取自 {@link AnimationChannelRegistry}
    public CurveTrack addChannel(String property) {
        AnimationTrack existing = tracks.get(property);

        if (existing instanceof CurveTrack curveTrack) {
            return curveTrack;
        }

        AnimationChannelRegistry.Channel channel = AnimationChannelRegistry.get(property);
        CurveTrack track = new CurveTrack(property, new Curve(), channel.label(), channel.color());
        tracks.put(property, track);
        return track;
    }

    /// 相机自身的属性通道名：播放逻辑按这些名字取值，所以它们不能改名、也不能当通用曲线轨道用
    private static final Set<String> CAMERA_CHANNELS = Set.of(CHANNEL_POSITION, CHANNEL_POSITION_X, CHANNEL_POSITION_Y,
            CHANNEL_POSITION_Z, CHANNEL_ROTATION_X, CHANNEL_ROTATION_Y, CHANNEL_ROTATION_Z, CHANNEL_FOV);

    /// 名字是否属于相机自身的属性通道
    public static boolean isCameraChannel(String id) {
        return CAMERA_CHANNELS.contains(id);
    }

    /// 新建一条通用曲线轨道：不对应任何相机属性，专门给变量读写用（变量绑定它就能取到这条曲线）。
    /// 名字取 `baseName` 加序号（界面传进来的「曲线」「Curve」这类词）标在动画内唯一；
    /// 这里不生成语言文本，调用方要把默认名做成本地化的自己拼好再传进来
    public @Nullable CurveTrack addCurveTrack(String baseName) {
        for (int i = 1; i < 1000; i++) {
            String id = baseName + i;

            if (!tracks.containsKey(id)) {
                return addChannel(id);
            }
        }

        return null;
    }

    /// 给通用曲线轨道改名。
    ///
    /// 轨道 id 与存档里的名字是同一个值，绑定这条轨道的变量要一起改指向，
    /// 否则变量会变成空指向、取值恒为 NaN。相机自身的属性通道不能改名。
    /// 顺序表用 LinkedHashMap 承载，直接换 key 会把轨道挪到末尾，因此这里重建整张表以保住位置
    public boolean renameCurveTrack(String id, String newId) {
        if (id.equals(newId) || isCameraChannel(id) || tracks.containsKey(newId)) {
            return false;
        }

        CurveTrack track = track(id);

        if (track == null) {
            return false;
        }

        // 键存在 Curve 对象里，换个 CurveTrack 外壳就能连数据一起带走
        AnimationChannelRegistry.Channel channel = AnimationChannelRegistry.get(newId);
        CurveTrack renamed = new CurveTrack(newId, track.curve(), channel.label(), channel.color());
        List<Map.Entry<String, AnimationTrack>> entries = new ArrayList<>(tracks.entrySet());
        tracks.clear();

        for (Map.Entry<String, AnimationTrack> entry : entries) {
            if (entry.getKey().equals(id)) {
                tracks.put(newId, renamed);
            } else {
                tracks.put(entry.getKey(), entry.getValue());
            }
        }

        // 绑定在这条轨道上的变量要跟着改指向，否则变量会变成空指向、取值恒为 NaN
        symbols.rebindTrack(id, newId);

        return true;
    }

    /// 移除曲线通道；扩展轨道不归通道管，请用 {@link #removeExtensionTrack(String)}
    public boolean removeChannel(String property) {
        if (!(tracks.get(property) instanceof CurveTrack)) {
            return false;
        }

        tracks.remove(property);
        // 绑定在这条轨道上的变量会变成空指向，一并解绑，免得变量的值莫名其妙恒为 0
        symbols.unbindTrack(property);
        return true;
    }

    /// 按 id 取曲线轨道的底层曲线；该 id 不是曲线轨道时返回 null
    @Override
    public @Nullable Curve curve(String id) {
        return tracks.get(id) instanceof CurveTrack curveTrack ? curveTrack.curve() : null;
    }

    /// 取曲线通道；同名的扩展轨道不会被当成通道返回
    public @Nullable CurveTrack track(String property) {
        return tracks.get(property) instanceof CurveTrack curveTrack ? curveTrack : null;
    }

    /// 按 id 查找任意轨道（曲线通道与扩展轨道共用一份顺序表）
    public @Nullable AnimationTrack trackById(String id) {
        return tracks.get(id);
    }

    /// 全部轨道，顺序即时间轴上的显示顺序
    @Override
    public List<AnimationTrack> tracks() {
        return List.copyOf(tracks.values());
    }

    @Override
    public List<CurveTrack> curveTracks() {
        List<CurveTrack> curves = new ArrayList<>();

        for (AnimationTrack track : tracks.values()) {
            if (track instanceof CurveTrack curveTrack) {
                curves.add(curveTrack);
            }
        }

        return List.copyOf(curves);
    }

    /// 接入事件触发器、后处理特效等扩展轨道，追加到顺序表末尾。
    /// id 已被占用时拒绝插入：曲线通道与扩展轨道共用同一份 id 空间
    public boolean addExtensionTrack(AnimationTrack track) {
        if (tracks.containsKey(track.id())) {
            return false;
        }

        tracks.put(track.id(), track);
        return true;
    }

    /// 按 id 移除一条扩展轨道；曲线通道请用 {@link #removeChannel(String)}
    public boolean removeExtensionTrack(String id) {
        AnimationTrack track = tracks.get(id);

        if (track == null || track instanceof CurveTrack) {
            return false;
        }

        tracks.remove(id);
        return true;
    }

    /// 给扩展轨道改名（时间轴上的显示名与存档里的 id 是同一个值）。
    ///
    /// 只有实现了 {@link RenamableTrack} 的轨道能改名；原 id 不存在、新 id 已占用或没有变化都返回 false。
    /// 顺序表用 LinkedHashMap 承载，直接换 key 会把轨道挪到末尾，因此这里重建整张表以保住位置
    public boolean renameExtensionTrack(String id, String newId) {
        AnimationTrack track = tracks.get(id);

        if (track == null || id.equals(newId) || tracks.containsKey(newId) || !(track instanceof RenamableTrack renamable)) {
            return false;
        }

        List<Map.Entry<String, AnimationTrack>> entries = new ArrayList<>(tracks.entrySet());
        tracks.clear();

        for (Map.Entry<String, AnimationTrack> entry : entries) {
            if (!entry.getKey().equals(id)) {
                tracks.put(entry.getKey(), entry.getValue());
                continue;
            }

            renamable.rename(newId);
            tracks.put(newId, entry.getValue());
        }

        return true;
    }

    /// 扩展轨道（非曲线轨道），顺序与 {@link #tracks()} 一致
    public List<AnimationTrack> extensionTracks() {
        List<AnimationTrack> extensions = new ArrayList<>();

        for (AnimationTrack track : tracks.values()) {
            if (!(track instanceof CurveTrack)) {
                extensions.add(track);
            }
        }

        return List.copyOf(extensions);
    }

    // region 符号表

    /// 符号表：动画里定义的变量与自定义函数。
    ///
    /// 两张表都归 {@link SymbolTable} 管，包括名字唯一性与"指向轨道的变量跟着轨道走"这类引用完整性，
    /// 这里只持有它。返回可写的实例：增删与改名归到这一处，调用方不必先转一次类型
    @Override
    public SymbolTable symbols() {
        return symbols;
    }

    /// 变量是否被它自己绑定的轨道引用，也就是自嵌套：该轨道上有关键帧挂了引用这个变量的公式。
    ///
    /// 这种写法不会成环也不会无限递归——变量读轨道时走的是静态曲线，公式在这一步被忽略，
    /// 用的是键上的固定数值（见 {@link ExpressionScope}）。但同一个键
    /// "作为相机属性播放"与"作为变量被引用"会得出不同的值，界面据此给出提示。
    ///
    /// 静态扫描在 {@link SymbolTable#references} 里，这里负责把它按到绑定的那条曲线的每个键上
    public boolean selfReferencing(Variable variable) {
        if (!(tracks.get(variable.trackId()) instanceof CurveTrack track)) {
            return false;
        }

        Curve curve = track.curve();

        for (int i = 0; i < curve.size(); i++) {
            if (SymbolTable.references(curve.key(i), variable.name())) {
                return true;
            }
        }

        return false;
    }

    // endregion

    @Override
    public float duration() {
        float duration = 0;

        for (AnimationTrack track : tracks.values()) {
            duration = Math.max(duration, track.duration());
        }

        return duration;
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

        int to = Math.clamp(from + offset, 0, order.size() - 1);

        if (to == from) {
            return false;
        }

        order.remove(from);
        order.add(to, id);

        LinkedHashMap<String, AnimationTrack> reordered = new LinkedHashMap<>();

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
        int target = Math.clamp(from + offset, 0, order.size() - block.size());

        if (target == from) {
            return false;
        }

        order.removeAll(block);
        order.addAll(target, block);

        LinkedHashMap<String, AnimationTrack> reordered = new LinkedHashMap<>();

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
            CurveTrack track = track(CHANNEL_POSITION);

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

        // 轨道表整体换掉；曲线跟着轨道走，没有第二份容器要同步
        tracks.clear();

        for (AnimationTrack track : other.tracks()) {
            tracks.put(track.id(), track);
        }

        // 符号表整体替换；变量与函数都是可变对象，装进来的是副本，避免两份动画共享同一个实例
        symbols.replaceFrom(other.symbols());
    }

    @Override
    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }
}
