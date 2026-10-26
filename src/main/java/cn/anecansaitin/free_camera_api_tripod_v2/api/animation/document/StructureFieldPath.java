package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 从 DFU 结构错误与 JSON 输入中尽力恢复字段路径。
///
/// DFU 的 [com.mojang.serialization.DataResult.Error] 只保存消息，不暴露成员路径；因此不能
/// 假装所有结构错误都有精确位置。本类只做两件有证据的事：
///
/// 1. 对已知整数语义字段（`data_version`、`update_every_frames`、`resample_every_ticks`）
///    直接检查 JSON 值，找到第一个非整数或越界的位置；
/// 2. 对“未知类型”消息，按消息里的类型 ID 找到带该 `type` 的对象，返回其 `type` 路径；
/// 3. 其次才用消息中的字段名做**唯一的**键定位。
///
/// 找不到有证据的位置时返回 `null`；不把猜测的路径写进诊断。
@NullMarked
public final class StructureFieldPath {

    private static final Set<String> INTEGER_FIELDS = Set.of(
            "data_version", "update_every_frames", "resample_every_ticks");
    private static final List<String> KNOWN_FIELDS = List.of(
            "data_version", "rigs", "channels", "keys", "tracks", "samples", "overlays",
            "update_every_frames", "resample_every_ticks", "type", "name");
    private static final Pattern UNKNOWN_TYPE = Pattern.compile(
            "Unknown (anchor|track|sample) type:?\\s*([^\\s,;]+)");
    private static final Pattern QUOTED = Pattern.compile("['\"`]([A-Za-z0-9_.\\[\\]-]+)['\"`]");

    private StructureFieldPath() {
    }

    /// 从错误消息与输入恢复字段路径。
    ///
    /// @param message 结构错误消息
    /// @param input   原始输入；仅 JSON 树可定位
    /// @return 字段路径；无法证明时为空
    public static @Nullable String locate(String message, Object input) {
        if (!(input instanceof JsonElement json)) {
            return null;
        }

        String integerPath = findInvalidInteger(json);

        if (integerPath != null) {
            return integerPath;
        }

        Matcher unknownType = UNKNOWN_TYPE.matcher(message);

        if (unknownType.find()) {
            String type = unknownType.group(2);

            if (!type.isBlank()) {
                return findTypePath(json, type);
            }
        }

        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.contains("overlays")) {
            return findKeyPath(json, "overlays");
        }

        for (String field : KNOWN_FIELDS) {
            if (lower.contains(field.toLowerCase(Locale.ROOT))) {
                String path = findKeyPath(json, field);

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

            String path = findKeyPath(json, field);

            if (path != null) {
                return path;
            }
        }

        return null;
    }

    private static @Nullable String findInvalidInteger(JsonElement element) {
        return findInvalidInteger(element, "");
    }

    private static @Nullable String findInvalidInteger(JsonElement element, String prefix) {
        if (element instanceof JsonObject object) {
            for (var entry : object.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                String path = join(prefix, key);

                if (INTEGER_FIELDS.contains(key) && !isInteger(value)) {
                    return path;
                }

                String nested = findInvalidInteger(value, path);

                if (nested != null) {
                    return nested;
                }
            }

            return null;
        }

        if (element instanceof JsonArray array) {
            for (int index = 0; index < array.size(); index++) {
                String nested = findInvalidInteger(array.get(index), indexPrefix(prefix, index));

                if (nested != null) {
                    return nested;
                }
            }
        }

        return null;
    }

    private static boolean isInteger(JsonElement element) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return false;
        }

        try {
            BigDecimal value = primitive.getAsBigDecimal();
            BigDecimal normalized = value.stripTrailingZeros();

            if (normalized.scale() > 0) {
                return false;
            }

            return normalized.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0
                    && normalized.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static @Nullable String findTypePath(JsonElement element, String type) {
        return findTypePath(element, type, "");
    }

    private static @Nullable String findTypePath(JsonElement element, String type, String prefix) {
        if (element instanceof JsonObject object) {
            JsonElement typeElement = object.get("type");

            if (typeElement instanceof JsonPrimitive primitive
                    && primitive.isString()
                    && primitive.getAsString().equals(type)) {
                return join(prefix, "type");
            }

            for (var entry : object.entrySet()) {
                String nested = findTypePath(entry.getValue(), type, join(prefix, entry.getKey()));

                if (nested != null) {
                    return nested;
                }
            }

            return null;
        }

        if (element instanceof JsonArray array) {
            for (int index = 0; index < array.size(); index++) {
                String nested = findTypePath(array.get(index), type, indexPrefix(prefix, index));

                if (nested != null) {
                    return nested;
                }
            }
        }

        return null;
    }

    private static @Nullable String findKeyPath(JsonElement element, String key) {
        return findKeyPath(element, key, "");
    }

    private static @Nullable String findKeyPath(JsonElement element, String key, String prefix) {
        if (element instanceof JsonObject object) {
            if (object.has(key)) {
                return join(prefix, key);
            }

            for (var entry : object.entrySet()) {
                String nested = findKeyPath(entry.getValue(), key, join(prefix, entry.getKey()));

                if (nested != null) {
                    return nested;
                }
            }

            return null;
        }

        if (element instanceof JsonArray array) {
            for (int index = 0; index < array.size(); index++) {
                String nested = findKeyPath(array.get(index), key, indexPrefix(prefix, index));

                if (nested != null) {
                    return nested;
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
