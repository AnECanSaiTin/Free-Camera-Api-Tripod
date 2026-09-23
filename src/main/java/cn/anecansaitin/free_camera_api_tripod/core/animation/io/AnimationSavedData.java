package cn.anecansaitin.free_camera_api_tripod.core.animation.io;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/// 相机动画与路径的存档内存储，随游戏存档一起保存。
///
/// 数据由 {@link SavedDataType} 注册，落盘文件为存档 data 目录下的
/// {@code free_camera_api_tripod/animations.dat}；
/// 内容为两个复合标签 {@code animations} 与 {@code paths}，各自存放 名字 -> JSON 字符串。
///
/// 名字支持用 {@code /} 分隔的多层级，例如 {@code 分镜/开场}：列出时可以按文件夹逐级查看，
/// 层级本身不需要额外结构，由名字里的分隔符推导。空文件夹没有任何条目，
/// 因此建文件夹时会写入一个 {@link #FOLDER_MARKER} 占位键，让它在存档里留得下来。
///
/// 所有静态方法都先取当前单机存档的数据：多人游戏或未进入世界时安全地返回空列表 / null / 不做任何事。
@NullMarked
public final class AnimationSavedData extends SavedData {
    private static final String DATA_PATH = "animations";
    private static final String ANIMATIONS_KEY = "animations";
    private static final String PATHS_KEY = "paths";
    /// 层级分隔符
    private static final String SEPARATOR = "/";
    /// 空文件夹的占位键名；列出文件时会被跳过
    private static final String FOLDER_MARKER = ".folder";
    private static final Identifier DATA_ID = Identifier.fromNamespaceAndPath(FreeCameraApiTripod.MODID, DATA_PATH);

    private static final Codec<AnimationSavedData> CODEC = CompoundTag.CODEC.xmap(AnimationSavedData::new, AnimationSavedData::save);
    private static final SavedDataType<AnimationSavedData> TYPE = new SavedDataType<>(
            DATA_ID,
            level -> new AnimationSavedData(),
            level -> CODEC
    );

    private final CompoundTag animations = new CompoundTag();
    private final CompoundTag paths = new CompoundTag();

    private AnimationSavedData() {
    }

    private AnimationSavedData(CompoundTag tag) {
        copyInto(tag.getCompoundOrEmpty(ANIMATIONS_KEY), animations);
        copyInto(tag.getCompoundOrEmpty(PATHS_KEY), paths);
    }

    /// 当前单机存档的数据；没有存档（多人游戏或未进世界）时返回 null
    public static @Nullable AnimationSavedData get() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft == null) {
            return null;
        }

        IntegratedServer server = minecraft.getSingleplayerServer();

        if (server == null) {
            return null;
        }

        ServerLevel overworld = server.overworld();

        if (overworld == null) {
            return null;
        }

        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /// 某个文件夹下的直接子文件夹名；folder 为空串表示根，层级用 {@code /} 分隔
    public static List<String> listFolders(boolean path, String folder) {
        AnimationSavedData data = get();
        return data == null ? List.of() : children(path ? data.paths : data.animations, folder, true);
    }

    /// 某个文件夹下的直接条目名（不含层级前缀）
    public static List<String> listFiles(boolean path, String folder) {
        AnimationSavedData data = get();
        return data == null ? List.of() : children(path ? data.paths : data.animations, folder, false);
    }

    /// 指定全名（含层级）是否已存在条目
    public static boolean exists(boolean path, String fullName) {
        AnimationSavedData data = get();

        if (data == null || fullName == null) {
            return false;
        }

        return (path ? data.paths : data.animations).getString(fullName).isPresent();
    }

    /// 建立文件夹；存档数据里没有目录实体，这里写一个占位键把层级本身记下来
    public static void createFolder(boolean path, String fullName) {
        AnimationSavedData data = get();

        if (data == null || fullName == null || fullName.isEmpty()) {
            return;
        }

        (path ? data.paths : data.animations).putString(fullName + SEPARATOR + FOLDER_MARKER, "");
        data.setDirty();
    }

    /// 把文件夹与条目名拼成全名
    public static String join(String folder, String name) {
        return folder == null || folder.isEmpty() ? name : folder + SEPARATOR + name;
    }

    public static void saveAnimation(String name, String json) {
        AnimationSavedData data = get();

        if (data == null || name == null || json == null) {
            return;
        }

        data.animations.putString(name, json);
        data.setDirty();
    }

    public static void savePath(String name, String json) {
        AnimationSavedData data = get();

        if (data == null || name == null || json == null) {
            return;
        }

        data.paths.putString(name, json);
        data.setDirty();
    }

    public static @Nullable String loadAnimation(String name) {
        AnimationSavedData data = get();
        return data == null || name == null ? null : data.animations.getString(name).orElse(null);
    }

    public static @Nullable String loadPath(String name) {
        AnimationSavedData data = get();
        return data == null || name == null ? null : data.paths.getString(name).orElse(null);
    }

    /// 序列化为两个复合标签，编码时会由标签编解码器复制副本
    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put(ANIMATIONS_KEY, animations.copy());
        tag.put(PATHS_KEY, paths.copy());
        return tag;
    }

    /// 只复制字符串条目，非字符串条目视为损坏数据并跳过
    private static void copyInto(CompoundTag source, CompoundTag target) {
        for (String key : source.keySet()) {
            source.getString(key).ifPresent(value -> target.putString(key, value));
        }
    }

    /// 列出某个文件夹下的直接子项：folders 为 true 时取子文件夹，false 时取条目本身。
    /// 全名里第一段分隔符之前的部分就是子文件夹名；占位键不算条目。
    private static List<String> children(CompoundTag tag, String folder, boolean folders) {
        String prefix = folder == null || folder.isEmpty() ? "" : folder + SEPARATOR;
        Set<String> found = new TreeSet<>();

        for (String key : tag.keySet()) {
            if (!key.startsWith(prefix)) {
                continue;
            }

            String rest = key.substring(prefix.length());

            if (rest.isEmpty()) {
                continue;
            }

            int separator = rest.indexOf(SEPARATOR);

            if (folders) {
                if (separator > 0) {
                    found.add(rest.substring(0, separator));
                }
            } else if (separator < 0 && !FOLDER_MARKER.equals(rest)) {
                found.add(rest);
            }
        }

        return List.copyOf(found);
    }
}
