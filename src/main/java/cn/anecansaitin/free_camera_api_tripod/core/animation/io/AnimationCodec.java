package cn.anecansaitin.free_camera_api_tripod.core.animation.io;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.WeightedMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.WrapMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.DynamicField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathNode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathNodec;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Pathc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.JsonTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.TrackType;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.TrackTypeRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/// 相机动画与路径的 JSON 编解码。
///
/// 顶层字段：{@code name}（动画名）、{@code motionMode}（运动模式）、{@code tracks}（轨道数组）、
/// {@code variables}（变量数组）、{@code path}（路径）。
/// 轨道数组按动画里的轨道顺序写出，两类轨道混排、用 {@code type} 区分：
/// - 曲线轨道（{@code type} 为 {@code free_camera_api_tripod:curve}）：{@code id}（相机属性名）、
///   {@code preMode}、{@code postMode} 与 {@code keys}；每个关键帧含时间、取值、入/出切线、入/出权重、插值模式与权重模式
/// - 扩展轨道：{@code id}（轨道标识）与 {@code keys}（字段由轨道自己定，见 {@link JsonTrack}）
///
/// 变量数组里每项含 {@code name}（表达式里引用的名字）、{@code track}（绑定的曲线轨道 id，空串表示不绑定）
/// 与 {@code value}（不绑定轨道时用的固定值）。
///
/// 挂了公式的数值字段统一写在 {@code expressions} 对象里，键是 {@link DynamicField} 的枚举名、值是公式文本；
/// 关键帧与路径节点各带一份，没有任何公式时不写出该字段。
///
/// 路径含 {@code name} 与 {@code nodes}，每个节点含位置、入/出切线、路径模式与平滑开关。
///
/// 反序列化一律宽松：字段缺失、类型错误、枚举名非法都退回默认值，只有整个 JSON 无法解析时才返回 null。
/// {@code tracks} 被视为权威集合，JSON 中未出现的曲线通道会在反序列化后被移除，保证结果与序列化内容一致。
/// 运动模式走 {@link CameraAnimation#motionMode()} 与 {@link CameraAnimation#restoreMotionMode}：
/// 读档只恢复标记，不再重建通道。
@NullMarked
public final class AnimationCodec {
    private static final String FIELD_NAME = "name";
    private static final String FIELD_MOTION_MODE = "motionMode";
    private static final String FIELD_DISTANCE_MODE = "distanceMode";
    private static final String FIELD_TRACKS = "tracks";
    private static final String FIELD_VARIABLES = "variables";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_ID = "id";
    private static final String FIELD_TRACK = "track";
    private static final String FIELD_EXPRESSIONS = "expressions";
    private static final String FIELD_PRE_MODE = "preMode";
    private static final String FIELD_POST_MODE = "postMode";
    private static final String FIELD_KEYS = "keys";
    private static final String FIELD_TIME = "time";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_IN_TANGENT = "inTangent";
    private static final String FIELD_OUT_TANGENT = "outTangent";
    private static final String FIELD_IN_WEIGHT = "inWeight";
    private static final String FIELD_OUT_WEIGHT = "outWeight";
    private static final String FIELD_EVALUATE_MODE = "evaluateMode";
    private static final String FIELD_WEIGHTED_MODE = "weightedMode";
    private static final String FIELD_NODES = "nodes";
    private static final String FIELD_POSITION = "position";
    private static final String FIELD_PATH_MODE = "pathMode";
    private static final String FIELD_SMOOTH = "smooth";

    private static final String DEFAULT_ANIMATION_NAME = "Camera";
    private static final String DEFAULT_PATH_NAME = "Path";
    /// 与 Keyframe 的默认权重保持一致
    private static final float DEFAULT_WEIGHT = 1f / 3f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AnimationCodec() {
    }

    /// 整个动画 -> JSON：名称、运动模式、全部曲线通道与关键帧、路径与路径节点
    public static String animationToJson(CameraAnimationc animation) {
        JsonObject root = new JsonObject();

        if (animation == null) {
            return GSON.toJson(root);
        }

        root.addProperty(FIELD_NAME, animation.name() == null ? "" : animation.name());
        root.addProperty(FIELD_MOTION_MODE, animation.motionMode().name());
        root.addProperty(FIELD_DISTANCE_MODE, animation.distanceMode().name());

        // 两类轨道写在同一个数组里，顺序就是动画里的轨道顺序，读档时按 type 还原
        JsonArray tracks = new JsonArray();

        for (AnimationTrack track : animation.tracks()) {
            JsonObject object = trackToJson(track);

            if (object != null) {
                tracks.add(object);
            }
        }

        root.add(FIELD_TRACKS, tracks);
        root.add(FIELD_VARIABLES, variablesToJson(animation));
        root.add(FIELD_PATH, pathToObject(animation.path()));
        return GSON.toJson(root);
    }

    private static JsonArray variablesToJson(CameraAnimationc animation) {
        JsonArray variables = new JsonArray();

        for (Variable variable : animation.variables()) {
            JsonObject object = new JsonObject();
            object.addProperty(FIELD_NAME, variable.name());
            object.addProperty(FIELD_TRACK, variable.trackId());
            // value 是「不绑轨道」时用的固定值；绑了轨道时它不参与求值，
            // 但一并写出来，取消绑定后原来的数还在
            object.addProperty(FIELD_VALUE, variable.value());
            variables.add(object);
        }

        return variables;
    }

    /// 一条轨道：曲线轨道按曲线写，其余交给轨道自己；
    /// 既不是曲线轨道、又没实现 {@link JsonTrack} 的返回 null，该条不落盘、其余轨道照常写
    private static @Nullable JsonObject trackToJson(AnimationTrack track) {
        if (track instanceof CurveTrack curveTrack) {
            return curveTrackToJson(curveTrack);
        }

        if (!(track instanceof JsonTrack jsonTrack)) {
            return null;
        }

        JsonObject object = new JsonObject();
        object.addProperty(FIELD_TYPE, track.type().toString());
        object.addProperty(FIELD_ID, track.id());
        object.add(FIELD_KEYS, jsonTrack.writeKeys());
        return object;
    }

    /// JSON -> 动画；解析失败返回 null
    public static @Nullable CameraAnimation animationFromJson(String json) {
        JsonObject root = parseObject(json);

        if (root == null) {
            return null;
        }

        CameraAnimation animation = new CameraAnimation(stringValue(root, FIELD_NAME, DEFAULT_ANIMATION_NAME));
        JsonElement tracks = root.get(FIELD_TRACKS);

        if (tracks != null && tracks.isJsonArray()) {
            readTracks(animation, tracks.getAsJsonArray());
        }

        JsonElement variables = root.get(FIELD_VARIABLES);

        if (variables != null && variables.isJsonArray()) {
            readVariables(animation, variables.getAsJsonArray());
        }

        JsonElement path = root.get(FIELD_PATH);

        if (path != null && path.isJsonObject()) {
            animation.path(readPath(path.getAsJsonObject()));
        }

        animation.restoreMotionMode(motionModeValue(stringValue(root, FIELD_MOTION_MODE, "")));
        // 距离口径只改标记：键值在写出时已经是该口径，再走 distanceMode 会被换算一遍
        animation.restoreDistanceMode(distanceModeValue(stringValue(root, FIELD_DISTANCE_MODE, "")));
        return animation;
    }

    /// 只序列化路径
    public static String pathToJson(Pathc path) {
        return GSON.toJson(pathToObject(path));
    }

    /// JSON -> 路径；解析失败返回 null
    public static @Nullable Path pathFromJson(String json) {
        JsonObject root = parseObject(json);
        return root == null ? null : readPath(root);
    }

    /// 解析 JSON 文本为对象，非对象或语法错误时返回 null
    private static @Nullable JsonObject parseObject(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static JsonObject curveTrackToJson(CurveTrack track) {
        JsonObject object = new JsonObject();
        Curve curve = track.curve();
        object.addProperty(FIELD_TYPE, TrackTypeRegistry.CURVE.toString());
        object.addProperty(FIELD_ID, track.id() == null ? "" : track.id());
        object.addProperty(FIELD_PRE_MODE, enumName(curve.preMode));
        object.addProperty(FIELD_POST_MODE, enumName(curve.postMode));
        JsonArray keys = new JsonArray();

        for (int i = 0; i < curve.size(); i++) {
            Keyframe key = curve.key(i);

            if (key != null) {
                keys.add(keyToJson(key));
            }
        }

        object.add(FIELD_KEYS, keys);
        return object;
    }

    private static JsonObject keyToJson(Keyframe key) {
        JsonObject object = new JsonObject();
        object.addProperty(FIELD_TIME, key.time());
        object.addProperty(FIELD_VALUE, key.value());
        object.addProperty(FIELD_IN_TANGENT, key.inTangent());
        object.addProperty(FIELD_OUT_TANGENT, key.outTangent());
        object.addProperty(FIELD_IN_WEIGHT, key.inWeight());
        object.addProperty(FIELD_OUT_WEIGHT, key.outWeight());
        object.addProperty(FIELD_EVALUATE_MODE, enumName(key.evaluateMode()));
        object.addProperty(FIELD_WEIGHTED_MODE, enumName(key.weightedMode()));

        // 没挂公式就不写这个字段，文件里只有真正用到的动态字段
        if (!key.expressions().isEmpty()) {
            object.add(FIELD_EXPRESSIONS, expressionsToJson(key.expressions()));
        }

        return object;
    }

    private static JsonObject expressionsToJson(Map<DynamicField, String> expressions) {
        JsonObject object = new JsonObject();

        for (Map.Entry<DynamicField, String> entry : expressions.entrySet()) {
            object.addProperty(entry.getKey().name(), entry.getValue());
        }

        return object;
    }

    /// 读一份公式表：枚举名不认识、值不是字符串的条目一律跳过，不影响同一条目里的其余公式
    private static void readExpressions(@Nullable JsonElement element, BiConsumer<DynamicField, String> apply) {
        if (element == null || !element.isJsonObject()) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            DynamicField field = dynamicField(entry.getKey());
            JsonElement value = entry.getValue();

            if (field == null || value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                continue;
            }

            apply.accept(field, value.getAsString());
        }
    }

    private static @Nullable DynamicField dynamicField(String name) {
        for (DynamicField field : DynamicField.values()) {
            if (field.name().equals(name)) {
                return field;
            }
        }

        return null;
    }

    private static JsonObject pathToObject(@Nullable Pathc path) {
        JsonObject object = new JsonObject();

        if (path == null) {
            return object;
        }

        object.addProperty(FIELD_NAME, path.name() == null ? DEFAULT_PATH_NAME : path.name());
        JsonArray nodes = new JsonArray();

        for (int i = 0; i < path.size(); i++) {
            nodes.add(nodeToJson(path.node(i)));
        }

        object.add(FIELD_NODES, nodes);
        return object;
    }

    private static JsonObject nodeToJson(PathNodec node) {
        JsonObject object = new JsonObject();
        object.add(FIELD_POSITION, vectorToJson(node.position()));
        object.add(FIELD_IN_TANGENT, vectorToJson(node.inTangent()));
        object.add(FIELD_OUT_TANGENT, vectorToJson(node.outTangent()));
        object.addProperty(FIELD_PATH_MODE, enumName(node.pathMode()));
        object.addProperty(FIELD_SMOOTH, node.smooth());

        Map<DynamicField, String> expressions = node.expressions();

        if (!expressions.isEmpty()) {
            object.add(FIELD_EXPRESSIONS, expressionsToJson(expressions));
        }

        return object;
    }

    private static JsonArray vectorToJson(Vector3fc vector) {
        JsonArray array = new JsonArray();
        array.add(vector.x());
        array.add(vector.y());
        array.add(vector.z());
        return array;
    }

    /// 轨道数组：按 {@code type} 分派——curve 走通道与曲线，其余交给注册表里的工厂造实例后自己读键。
    /// 曲线通道以本数组为权威集合，JSON 里没出现的通道读完即移除，避免动画构造时的默认通道残留
    private static void readTracks(CameraAnimation animation, JsonArray tracks) {
        Set<String> curves = new LinkedHashSet<>();

        for (JsonElement element : tracks) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            Identifier typeId = Identifier.tryParse(stringValue(object, FIELD_TYPE, ""));
            String id = stringValue(object, FIELD_ID, "");

            if (typeId == null || id.isEmpty()) {
                continue;
            }

            if (typeId.equals(TrackTypeRegistry.CURVE)) {
                curves.add(id);
                readCurve(animation.addChannel(id).curve(), object);
                continue;
            }

            readExtensionTrack(animation, typeId, id, object);
        }

        for (CurveTrack existing : animation.curveTracks()) {
            if (!curves.contains(existing.id())) {
                animation.removeChannel(existing.id());
            }
        }
    }

    /// 一条扩展轨道：按 {@code type} 查类型、用工厂造实例，再由它自己读回键。
    ///
    /// 类型未注册、没有工厂（例如外部 mod 注册的类型在当前环境没加载）、id 与已有轨道重复、
    /// 或类型不实现 {@link JsonTrack} 的条目一律跳过，不影响同文件里的其余轨道
    private static void readExtensionTrack(CameraAnimation animation, Identifier typeId, String id, JsonObject object) {
        TrackType type = TrackTypeRegistry.get(typeId);

        if (type == null || type.factory() == null || animation.trackById(id) != null) {
            return;
        }

        AnimationTrack track = type.factory().create(id);

        if (!(track instanceof JsonTrack jsonTrack)) {
            return;
        }

        JsonElement keys = object.get(FIELD_KEYS);

        if (keys != null && keys.isJsonArray()) {
            jsonTrack.readKeys(keys.getAsJsonArray());
        }

        animation.addExtensionTrack(track);
    }

    private static void readCurve(Curve curve, JsonObject object) {
        curve.preMode = enumValue(WrapMode.class, object.get(FIELD_PRE_MODE), WrapMode.CLAMP);
        curve.postMode = enumValue(WrapMode.class, object.get(FIELD_POST_MODE), WrapMode.CLAMP);

        // 清空动画构造时写入的默认关键帧
        while (curve.size() > 0) {
            curve.removeKey(0);
        }

        JsonElement keys = object.get(FIELD_KEYS);

        if (keys == null || !keys.isJsonArray()) {
            return;
        }

        for (JsonElement element : keys.getAsJsonArray()) {
            if (element.isJsonObject()) {
                curve.key(readKey(element.getAsJsonObject()));
            }
        }
    }

    private static Keyframe readKey(JsonObject object) {
        Keyframe key = Keyframe.create(floatValue(object, FIELD_TIME, 0), floatValue(object, FIELD_VALUE, 0))
                .inTangent(floatValue(object, FIELD_IN_TANGENT, 0))
                .outTangent(floatValue(object, FIELD_OUT_TANGENT, 0))
                .inWeight(floatValue(object, FIELD_IN_WEIGHT, DEFAULT_WEIGHT))
                .outWeight(floatValue(object, FIELD_OUT_WEIGHT, DEFAULT_WEIGHT))
                .evaluateMode(enumValue(EvaluateMode.class, object.get(FIELD_EVALUATE_MODE), EvaluateMode.LINEAR))
                .weightedMode(enumValue(WeightedMode.class, object.get(FIELD_WEIGHTED_MODE), WeightedMode.NONE));
        readExpressions(object.get(FIELD_EXPRESSIONS), key::expression);
        return key;
    }

    /// 变量数组：名字重复或为空的条目跳过（变量的值靠名字引用，重名没有意义）
    private static void readVariables(CameraAnimation animation, JsonArray variables) {
        for (JsonElement element : variables) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            Variable variable = animation.addVariable(stringValue(object, FIELD_NAME, ""));

            if (variable != null) {
                variable.trackId(stringValue(object, FIELD_TRACK, ""));
                variable.value(floatValue(object, FIELD_VALUE, 0));
            }
        }
    }

    private static Path readPath(JsonObject object) {
        Path path = new Path(stringValue(object, FIELD_NAME, DEFAULT_PATH_NAME));
        JsonElement nodes = object.get(FIELD_NODES);

        if (nodes == null || !nodes.isJsonArray()) {
            return path;
        }

        for (JsonElement element : nodes.getAsJsonArray()) {
            if (element.isJsonObject()) {
                path.node(readNode(element.getAsJsonObject()));
            }
        }

        return path;
    }

    private static PathNode readNode(JsonObject object) {
        PathNode node = new PathNode(readVector(object.get(FIELD_POSITION)));
        node.pathMode(enumValue(PathMode.class, object.get(FIELD_PATH_MODE), PathMode.LINEAR));
        // 缺字段时沿用节点自身的默认值（自动平滑默认开启），所以要在关掉它之前先读出来
        boolean smooth = booleanValue(object, FIELD_SMOOTH, node.smooth());
        // 写切线前先把自动平滑关掉：开启状态下写一侧会带着另一侧一起动，
        // 那样文件里存的对侧切线会被覆盖；开关在最后用 restoreSmooth 原样恢复，不再动切线
        node.restoreSmooth(false);
        Vector3f inTangent = readVector(object.get(FIELD_IN_TANGENT));
        node.inTangent(inTangent.x, inTangent.y, inTangent.z);
        Vector3f outTangent = readVector(object.get(FIELD_OUT_TANGENT));
        node.outTangent(outTangent.x, outTangent.y, outTangent.z);
        node.restoreSmooth(smooth);
        readExpressions(object.get(FIELD_EXPRESSIONS), node::expression);
        return node;
    }

    private static Vector3f readVector(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return new Vector3f();
        }

        JsonArray array = element.getAsJsonArray();
        return new Vector3f(numberValue(array, 0), numberValue(array, 1), numberValue(array, 2));
    }

    private static float numberValue(JsonArray array, int index) {
        if (index >= array.size()) {
            return 0;
        }

        JsonElement element = array.get(index);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return 0;
        }

        return element.getAsFloat();
    }

    private static float floatValue(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }

        return element.getAsFloat();
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }

        return element.getAsBoolean();
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return fallback;
        }

        return element.getAsString();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, @Nullable JsonElement element, E fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return fallback;
        }

        String name = element.getAsString();

        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }

        return fallback;
    }

    private static String enumName(@Nullable Enum<?> value) {
        return value == null ? "" : value.name();
    }

    /// 解析运动模式；枚举名非法或字段缺失时回退 {@link CameraAnimation.MotionMode#PATH}
    private static CameraAnimation.MotionMode motionModeValue(String name) {
        for (CameraAnimation.MotionMode mode : CameraAnimation.MotionMode.values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }

        return CameraAnimation.MotionMode.PATH;
    }

    /// 解析距离口径；枚举名非法或字段缺失时回退绝对距离
    private static CameraAnimation.DistanceMode distanceModeValue(String name) {
        for (CameraAnimation.DistanceMode mode : CameraAnimation.DistanceMode.values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }

        return CameraAnimation.DistanceMode.ABSOLUTE;
    }
}
