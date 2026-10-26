package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 从 NBT 结构错误与输入中尽力恢复字段路径。
///
/// NBT Codec 的错误只保存消息，不暴露成员路径；本类只做有证据的定位：
///
/// 1. 已知整数语义字段（`data_version`、`update_every_frames`、`resample_every_ticks`）
///    直接检查 NBT 值的类型 / data_version 取值；
/// 2. 对“未知类型”消息，按消息里的类型 ID 找到带该 `type` 的 Compound；
/// 3. 其次才用消息中的已知字段名做唯一键定位。
///
/// 找不到有证据的位置时返回 `null`；不把猜测路径写进诊断。NBT 与 JSON 的路径文本共用同一
/// `a.b[0]` 口径。
final class NbtStructureFieldPath {

    private static final Set<String> INTEGER_FIELDS = Set.of(
            "data_version", "update_every_frames", "resample_every_ticks");
    private static final List<String> KNOWN_FIELDS = List.of(
            "data_version", "rigs", "channels", "keys", "tracks", "samples", "overlays",
            "update_every_frames", "resample_every_ticks", "type", "name");
    private static final Pattern UNKNOWN_TYPE = Pattern.compile(
            "Unknown (anchor|track|sample) type:?\\s*([^\\s,;]+)");
    private static final Pattern QUOTED = Pattern.compile("['\"`]([A-Za-z0-9_.\\[\\]-]+)['\"`]");

    private NbtStructureFieldPath() {
    }

    /// 从错误消息与 NBT 根恢复字段路径。
    ///
    /// @return 字段路径；无法证明时为空
    static @Nullable String locate(String message, CompoundTag root) {
        String integerPath = findInvalidInteger(root, "");
        String lower = message.toLowerCase(Locale.ROOT);

        if (integerPath != null) {
            return integerPath;
        }

        Matcher unknownType = UNKNOWN_TYPE.matcher(message);

        if (unknownType.find()) {
            String type = unknownType.group(2);

            if (!type.isBlank()) {
                String path = findTypePath(root, type, "");

                if (path != null) {
                    return path;
                }
            }
        }

        for (String field : KNOWN_FIELDS) {
            if (lower.contains(field.toLowerCase(Locale.ROOT))) {
                String path = findKeyPath(root, field);

                if (path != null) {
                    return path;
                }
            }
        }

        Matcher quoted = QUOTED.matcher(message);

        while (quoted.find()) {
            String field = quoted.group(1);

            if (field.contains(".") || field.contains("[")) {
                return field;
            }

            String path = findKeyPath(root, field);

            if (path != null) {
                return path;
            }
        }

        return null;
    }

    /// NBT 数值标签的整数语义与 `Codec.INT` 对齐：Int/Long/Short/Byte/Float/Double
    /// 只要数学上为整数且在 `int` 范围内都合法，不要求必须是 `IntTag`。
    private static boolean isIntegerValue(Tag value, String key) {
        if (!(value instanceof NumericTag numeric)) {
            return false;
        }

        double number = numeric.doubleValue();

        if (!Double.isFinite(number)
                || number != Math.rint(number)
                || number < Integer.MIN_VALUE
                || number > Integer.MAX_VALUE) {
            return false;
        }

        return !"data_version".equals(key)
                || (int) number == CameraAnimation.CURRENT_DATA_VERSION;
    }

    private static @Nullable String findInvalidInteger(CompoundTag root, String prefix) {
        for (String key : root.keySet()) {
            Tag value = root.get(key);
            String path = join(prefix, key);

            if (INTEGER_FIELDS.contains(key) && !isIntegerValue(value, key)) {
                return path;
            }

            if (value instanceof CompoundTag compound) {
                String nested = findInvalidInteger(compound, path);

                if (nested != null) {
                    return nested;
                }
            } else if (value instanceof ListTag list) {
                for (int index = 0; index < list.size(); index++) {
                    Tag element = list.get(index);

                    if (element instanceof CompoundTag compound) {
                        String nested = findInvalidInteger(compound, indexPrefix(path, index));

                        if (nested != null) {
                            return nested;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static @Nullable String findTypePath(CompoundTag root,
                                                 String type,
                                                 String prefix) {
        for (String key : root.keySet()) {
            Tag value = root.get(key);
            String path = join(prefix, key);

            if ("type".equals(key) && value instanceof StringTag string
                    && string.value().equals(type)) {
                return path;
            }

            if (value instanceof CompoundTag compound) {
                String nested = findTypePath(compound, type, path);

                if (nested != null) {
                    return nested;
                }
            } else if (value instanceof ListTag list) {
                for (int index = 0; index < list.size(); index++) {
                    Tag element = list.get(index);

                    if (element instanceof CompoundTag compound) {
                        String nested = findTypePath(compound, type, indexPrefix(path, index));

                        if (nested != null) {
                            return nested;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static @Nullable String findKeyPath(CompoundTag root, String key) {
        if (root.contains(key)) {
            return key;
        }

        for (String childKey : root.keySet()) {
            Tag value = root.get(childKey);

            if (value instanceof CompoundTag compound) {
                String nested = findKeyPath(compound, key);

                if (nested != null) {
                    return join(childKey, nested);
                }
            } else if (value instanceof ListTag list) {
                for (int index = 0; index < list.size(); index++) {
                    Tag element = list.get(index);

                    if (element instanceof CompoundTag compound) {
                        String nested = findKeyPath(compound, key);

                        if (nested != null) {
                            return indexPrefix(childKey, index) + "." + nested;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static String join(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }

    private static String indexPrefix(String prefix, int index) {
        return prefix + "[" + index + "]";
    }
}
