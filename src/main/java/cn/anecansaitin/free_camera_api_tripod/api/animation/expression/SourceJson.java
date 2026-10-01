package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/// 数值来源的 JSON 读写：包内共用，不进公开 API。
///
/// 磁盘格式（一字不改）：
/// - 固定值：`1.5`
/// - 公式：`{"expression": "t * 2", "fallback": 1.5}`
/// - 轨道读数：`{"track": "fov"}`——**只有变量读得到**，关键帧字段上出现它属于非法通路
@NullMarked
final class SourceJson {
    static final String FIELD_EXPRESSION = "expression";
    static final String FIELD_FALLBACK = "fallback";
    static final String FIELD_TRACK = "track";

    /// 走 mod 的日志器（slf4j）：非法通路只在这一处报，落到游戏日志里用户才看得见。
    ///
    /// 这也是 `expression` 层唯一的对外依赖——它因此不再是"纯 JDK 类型"，
    /// 分层说明见设计文档第 2 节
    private static final Logger LOGGER = LogUtils.getLogger();

    private SourceJson() {
    }

    /// 一个数值来源 -> JSON：固定值写成数字，公式与轨道读数写成对象
    static JsonElement write(ValueSource source) {
        return switch (source) {
            case Constant constant -> new JsonPrimitive(constant.value());
            case Formula formula -> {
                JsonObject object = new JsonObject();
                object.addProperty(FIELD_EXPRESSION, formula.expression());
                object.addProperty(FIELD_FALLBACK, formula.fallback());
                yield object;
            }
            case TrackRef track -> {
                JsonObject object = new JsonObject();
                object.addProperty(FIELD_TRACK, track.trackId());
                yield object;
            }
        };
    }

    /// 关键帧槽位的来源：认数字与 `expression`，**不认 `track`**
    static NumberSource readNumber(@Nullable JsonElement element, float fallback) {
        if (element == null || !element.isJsonObject()) {
            return new Constant(numberOr(element, fallback));
        }

        JsonObject object = element.getAsJsonObject();

        // 关键帧字段认轨道是一条历史遗留的非法通路：类型上已经堵死，格式上按回退值降级并记一条日志
        if (object.has(FIELD_TRACK)) {
            LOGGER.warn("A keyframe field referenced a curve track (\"track\": {}); "
                            + "track reads are variable-only, so the field fell back to its constant value",
                    stringValue(object, FIELD_TRACK, ""));
            return new Constant(floatValue(object, FIELD_FALLBACK, fallback));
        }

        return formulaOrConstant(object, fallback);
    }

    /// 变量的来源：在关键帧那一套之上多认 `track`
    static ValueSource readValue(@Nullable JsonElement element, float fallback) {
        if (element == null || !element.isJsonObject()) {
            return new Constant(numberOr(element, fallback));
        }

        JsonObject object = element.getAsJsonObject();
        String track = stringValue(object, FIELD_TRACK, "");

        if (!track.isEmpty()) {
            return new TrackRef(track);
        }

        return formulaOrConstant(object, fallback);
    }

    /// 对象里既没有轨道、也不是公式时：按 `fallback` 字段降级成固定值
    private static NumberSource formulaOrConstant(JsonObject object, float fallback) {
        String expression = stringValue(object, FIELD_EXPRESSION, "");

        if (!expression.isBlank()) {
            return new Formula(expression, floatValue(object, FIELD_FALLBACK, fallback));
        }

        return new Constant(floatValue(object, FIELD_FALLBACK, fallback));
    }

    private static float numberOr(@Nullable JsonElement element, float fallback) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsFloat();
        }

        return fallback;
    }

    static float floatValue(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }

        return element.getAsFloat();
    }

    static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return fallback;
        }

        return element.getAsString();
    }
}
