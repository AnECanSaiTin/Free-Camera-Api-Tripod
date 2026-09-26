package cn.anecansaitin.free_camera_api_tripod.core.animation.track;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationChannelRegistry;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.JsonTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.RenamableTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.TickTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.TrackType;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.TrackTypeRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 命令轨道：把「某一时刻执行一条指令」做成关键帧，播放经过该时刻时以玩家身份执行。
///
/// 它与曲线通道的差别，正好说明了扩展轨道为什么需要 {@link TrackType#factory()}：
/// 曲线轨道的身份是相机属性名（{@code position.x}、{@code fov}…），名称 / 颜色 / 默认值都由
/// {@link AnimationChannelRegistry} 提供，轨道本身由 {@code CameraAnimation.addChannel} 按需创建，
/// 类型层根本没有可插手的余地；命令轨道则要由使用者命名、键里存的是字符串，
/// 只有类型工厂才能凭空造出一条空的出来。
///
/// 键按时间升序保存，播放时由 {@link #advance} 扫描区间触发，同一次播放里每个键只触发一次。
/// 指令以玩家身份在客户端发出，权限照旧由服务端判定。
@NullMarked
public class CommandTrack implements AnimationTrack, TickTrack, JsonTrack, RenamableTrack {
    /// 轨道类型：id 为 {@code free_camera_api_tripod:command}，工厂即「按 id 造一条空轨道」
    public static final TrackType TYPE = new TrackType(
            Identifier.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "command"),
            Component.translatable("free_camera_api_tripod.track_type.command"),
            0xFFFF8A5C,
            CommandTrack::new);
    /// 默认轨道 id：一个动画通常只有一条命令轨道，需要多条时建轨道时另起名字
    public static final String DEFAULT_ID = "command";
    /// 存档里每条键的字段名
    private static final String FIELD_TIME = "time";
    private static final String FIELD_COMMAND = "command";

    /// 一条指令键：时间 + 要执行的指令（保存时不带前导斜杠）
    public record Key(float time, String command) implements TrackKey {
    }

    /// 轨道标识，同时也是时间轴上的显示名，可以用 {@link #rename(String)} 改
    private String id;
    /// 按时间升序保存
    private final List<Key> keys = new ArrayList<>();

    public CommandTrack(String id) {
        this.id = id;
    }

    // region 键

    /// 在指定时间插入一条指令；该时间已有关键帧时改写它的指令，返回关键帧索引，时间非法返回 -1
    public int addKey(float time, String command) {
        if (!Float.isFinite(time) || time < 0) {
            return -1;
        }

        int existing = indexOf(time);

        if (existing >= 0) {
            keys.set(existing, new Key(time, normalize(command)));
            return existing;
        }

        int insert = insertionPoint(time);
        keys.add(insert, new Key(time, normalize(command)));
        return insert;
    }

    /// 当前指令轨道的插入语义：先放一条空指令，指令文本随后由 {@link #command(int, String)} 写入
    @Override
    public int addKey(float time) {
        return addKey(time, "");
    }

    @Override
    public boolean removeKey(int index) {
        if (index < 0 || index >= keys.size()) {
            return false;
        }

        keys.remove(index);
        return true;
    }

    /// 把关键帧移到新的时间；目标时间已有别的关键帧时不动，返回 -1
    @Override
    public int moveKey(int index, float newTime) {
        if (index < 0 || index >= keys.size() || !Float.isFinite(newTime) || newTime < 0) {
            return -1;
        }

        Key key = keys.get(index);

        if (key.time() == newTime) {
            return -1;
        }

        int existing = indexOf(newTime);

        if (existing >= 0 && existing != index) {
            return -1;
        }

        keys.remove(index);
        return addKey(newTime, key.command());
    }

    /// 第 index 条键的指令文本；索引越界返回 null
    public @Nullable String command(int index) {
        return index < 0 || index >= keys.size() ? null : keys.get(index).command();
    }

    /// 改写某条键的指令；索引越界返回 false
    public boolean command(int index, String command) {
        if (index < 0 || index >= keys.size()) {
            return false;
        }

        keys.set(index, new Key(keys.get(index).time(), normalize(command)));
        return true;
    }

    /// 已存在同时间的关键帧时返回它的索引，没有则返回 -1。
    /// 关键帧数量通常在几十条量级，线性查找足够，也省掉维护第二份索引
    private int indexOf(float time) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).time() == time) {
                return i;
            }
        }

        return -1;
    }

    /// 保持升序的插入位置
    private int insertionPoint(float time) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).time() > time) {
                return i;
            }
        }

        return keys.size();
    }

    // endregion

    // region AnimationTrack

    @Override
    public Identifier type() {
        return TYPE.id();
    }

    @Override
    public String id() {
        return id;
    }

    /// 显示名就是轨道标识：一个动画里可以插入多条命令轨道，靠名字区分它们；
    /// 颜色仍按类型取，类型信息不至于丢
    @Override
    public Component label() {
        return Component.literal(id);
    }

    @Override
    public int color() {
        return TrackTypeRegistry.color(TYPE.id());
    }

    /// 重命名：改的就是标识本身，时间轴显示名与存档里的 id 一起变
    @Override
    public void rename(String id) {
        this.id = id;
    }

    @Override
    public float duration() {
        return keys.isEmpty() ? 0f : keys.getLast().time();
    }

    @Override
    public int keyCount() {
        return keys.size();
    }

    /// 时间轴统一按时间摆放关键帧，这里只回传带时间的键；指令文本用 {@link #command(int)} 取
    @Override
    public @Nullable TrackKey key(int index) {
        return index < 0 || index >= keys.size() ? null : keys.get(index);
    }

    // endregion

    // region TickTrack

    /// 播放头从 fromTime 走到 toTime：按时间先后执行落在 (fromTime, toTime] 里的指令。
    /// 空指令（只有关键帧还没写内容）跳过
    @Override
    public void advance(float fromTime, float toTime) {
        if (!(toTime > fromTime)) {
            return;
        }

        for (Key key : keys) {
            if (key.time() > fromTime && key.time() <= toTime && !key.command().isEmpty()) {
                execute(key.command());
            }
        }
    }

    /// 以玩家身份在客户端发出一条指令；没有玩家（标题界面等）时静默跳过
    public static void execute(String command) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        player.connection.sendCommand(normalize(command));
    }

    /// 统一成不带前导斜杠的形式：编辑器里 {@code /time set day} 与 {@code time set day} 两种写法都常见
    private static String normalize(String command) {
        String trimmed = command.strip();

        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).strip();
        }

        return trimmed;
    }

    // endregion

    // region JsonTrack

    /// 键写成 {@code {"time": 1.5, "command": "time set day"}} 组成的数组
    @Override
    public JsonArray writeKeys() {
        JsonArray array = new JsonArray();

        for (Key key : keys) {
            JsonObject object = new JsonObject();
            object.addProperty(FIELD_TIME, key.time());
            object.addProperty(FIELD_COMMAND, key.command());
            array.add(object);
        }

        return array;
    }

    /// 读回键：缺时间或不是对象的条目跳过；缺指令按空指令处理（相当于"键建好了还没写内容"）
    @Override
    public void readKeys(JsonArray array) {
        keys.clear();

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            JsonElement time = object.get(FIELD_TIME);

            if (time == null || !time.isJsonPrimitive() || !time.getAsJsonPrimitive().isNumber()) {
                continue;
            }

            JsonElement command = object.get(FIELD_COMMAND);
            addKey(time.getAsFloat(), command != null && command.isJsonPrimitive() ? command.getAsString() : "");
        }
    }

    // endregion
}
