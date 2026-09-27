package cn.anecansaitin.free_camera_api_tripod.core.animation.io;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.WrapMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ConstantValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.FormulaValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.TrackValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;
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
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// 相机动画与路径的 JSON 编解码。
///
/// 顶层字段：{@code name}（动画名）、{@code motionMode}（运动模式）、{@code tracks}（轨道数组）、
/// {@code variables}（变量数组）、{@code path}（路径）。
/// 轨道数组按动画里的轨道顺序写出，两类轨道混排、用 {@code type} 区分：
/// - 曲线轨道（{@code type} 为 {@code free_camera_api_tripod:curve}）：{@code id}（相机属性名）、
///   {@code preMode}、{@code postMode} 与 {@code keys}；每个关键帧含时间、取值、两条曲柄的斜率与长度倍数、
///   插值模式。旧文件里的 {@code inTangent}/{@code outTangent}/{@code inWeight}/{@code outWeight}/{@code weightedMode}
///   仍可读入，长度按当时的线性系数换算（见 {@code legacyLength}）
/// - 扩展轨道：{@code id}（轨道标识）与 {@code keys}（字段由轨道自己定，见 {@link JsonTrack}）
///
/// **一个数值**有两种写法：固定值直接写成数字，公式写成 {@code {"expression": "…", "fallback": 1.0}}，
/// 轨道读数写成 {@code {"track": "fov"}}。关键帧的取值与曲柄、变量的取值来源都用它，
/// 于是文件里"这个数是不是动态的"一眼就能看出来，不必再去别处找公式表。
///
/// 变量数组里每项是 {@code name} 加一个 {@code source}（同一个数值写法）。
///
/// 路径是**纯几何**，节点坐标与切线就是三个数字的数组，不含公式：{@code position} /
/// {@code inTangent} / {@code outTangent}，另含 {@code pathMode} 与 {@code smooth}。
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
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_EXPRESSION = "expression";
    private static final String FIELD_FALLBACK = "fallback";
    private static final String FIELD_PRE_MODE = "preMode";
    private static final String FIELD_POST_MODE = "postMode";
    private static final String FIELD_KEYS = "keys";
    private static final String FIELD_FUNCTIONS = "functions";
    private static final String FIELD_PARAMETERS = "parameters";
    private static final String FIELD_BODY = "body";
    private static final String FIELD_TIME = "time";
    private static final String FIELD_VALUE = "value";
    /// 关键帧两侧的曲柄：斜率与长度倍数
    private static final String FIELD_IN_SLOPE = "inSlope";
    private static final String FIELD_OUT_SLOPE = "outSlope";
    private static final String FIELD_IN_LENGTH = "inLength";
    private static final String FIELD_OUT_LENGTH = "outLength";
    /// 路径节点的入/出切线；关键帧在旧格式里也用这两个名字存斜率
    private static final String FIELD_IN_TANGENT = "inTangent";
    private static final String FIELD_OUT_TANGENT = "outTangent";
    /// 旧格式的曲线权重与加权模式，只用于读档时换算成曲柄长度
    private static final String FIELD_LEGACY_IN_WEIGHT = "inWeight";
    private static final String FIELD_LEGACY_OUT_WEIGHT = "outWeight";
    private static final String FIELD_LEGACY_WEIGHTED_MODE = "weightedMode";
    /// 旧格式把权重线性换算成曲柄长度倍数，系数取它当时的取值
    private static final float LEGACY_WEIGHT_TO_LENGTH = 0.3f;
    private static final String FIELD_EVALUATE_MODE = "evaluateMode";
    private static final String FIELD_NODES = "nodes";
    private static final String FIELD_POSITION = "position";
    private static final String FIELD_PATH_MODE = "pathMode";
    private static final String FIELD_SMOOTH = "smooth";

    private static final String DEFAULT_ANIMATION_NAME = "Camera";
    private static final String DEFAULT_PATH_NAME = "Path";

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
        root.add(FIELD_FUNCTIONS, functionsToJson(animation));
        root.add(FIELD_PATH, pathToObject(animation.path()));
        return GSON.toJson(root);
    }

    /// 自定义函数：名字 + 参数名数组 + 函数体文本
    private static JsonArray functionsToJson(CameraAnimationc animation) {
        JsonArray functions = new JsonArray();

        for (CustomFunction function : animation.functions()) {
            JsonObject object = new JsonObject();
            object.addProperty(FIELD_NAME, function.name());
            JsonArray parameters = new JsonArray();

            for (String parameter : function.parameters()) {
                parameters.add(parameter);
            }

            object.add(FIELD_PARAMETERS, parameters);
            object.addProperty(FIELD_BODY, function.body());
            functions.add(object);
        }

        return functions;
    }

    private static JsonArray variablesToJson(CameraAnimationc animation) {
        JsonArray variables = new JsonArray();

        for (Variable variable : animation.variables()) {
            JsonObject object = new JsonObject();
            object.addProperty(FIELD_NAME, variable.name());
            object.add(FIELD_SOURCE, valueToJson(variable.source()));
            variables.add(object);
        }

        return variables;
    }

    /// 读自定义函数：名字 + 参数名数组 + 函数体
    private static void readFunctions(CameraAnimation animation, JsonArray array) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String name = stringValue(object, FIELD_NAME, "");

            if (name.isBlank()) {
                continue;
            }

            animation.addFunction(name, readParameters(object), stringValue(object, FIELD_BODY, ""));
        }
    }

    /// 函数的参数名数组；缺字段时给空表（零参数函数）
    private static List<String> readParameters(JsonObject object) {
        JsonElement parameters = object.get(FIELD_PARAMETERS);

        if (parameters == null || !parameters.isJsonArray()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();

        for (JsonElement parameter : parameters.getAsJsonArray()) {
            if (parameter.isJsonPrimitive()) {
                names.add(parameter.getAsString().strip());
            }
        }

        return names;
    }

    /// 一个数值：固定值写成数字，公式与轨道读数写成对象
    private static JsonElement valueToJson(ValueSource source) {
        return switch (source) {
            case ConstantValue constant -> new JsonPrimitive(constant.value());
            case FormulaValue formula -> {
                JsonObject object = new JsonObject();
                object.addProperty(FIELD_EXPRESSION, formula.expression());
                object.addProperty(FIELD_FALLBACK, formula.fallback());
                yield object;
            }
            case TrackValue track -> {
                JsonObject object = new JsonObject();
                object.addProperty(FIELD_TRACK, track.trackId());
                yield object;
            }
        };
    }

    /// 读一个数值：数字是固定值，对象里认 expression（公式）与 track（轨道读数）；
    /// 认不出来或字段缺失就用 fallback
    private static ValueSource readValue(@Nullable JsonElement element, float fallback) {
        if (element == null) {
            return new ConstantValue(fallback);
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return new ConstantValue(element.getAsFloat());
        }

        if (!element.isJsonObject()) {
            return new ConstantValue(fallback);
        }

        JsonObject object = element.getAsJsonObject();
        String track = stringValue(object, FIELD_TRACK, "");

        if (!track.isEmpty()) {
            return new TrackValue(track);
        }

        String expression = stringValue(object, FIELD_EXPRESSION, "");

        if (!expression.isBlank()) {
            return new FormulaValue(expression, floatValue(object, FIELD_FALLBACK, fallback));
        }

        return new ConstantValue(floatValue(object, FIELD_FALLBACK, fallback));
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

        JsonElement functions = root.get(FIELD_FUNCTIONS);

        if (functions != null && functions.isJsonArray()) {
            readFunctions(animation, functions.getAsJsonArray());
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
        object.add(FIELD_VALUE, valueToJson(key.valueSource()));
        object.add(FIELD_IN_SLOPE, valueToJson(key.inSlopeSource()));
        object.add(FIELD_OUT_SLOPE, valueToJson(key.outSlopeSource()));
        object.add(FIELD_IN_LENGTH, valueToJson(key.inLengthSource()));
        object.add(FIELD_OUT_LENGTH, valueToJson(key.outLengthSource()));
        object.addProperty(FIELD_EVALUATE_MODE, enumName(key.evaluateMode()));
        return object;
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
        return object;
    }

    /// 路径节点是纯几何：坐标与切线就写成三个数字的数组，没有公式可言
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
        Keyframe key = Keyframe.create(floatValue(object, FIELD_TIME, 0), 0)
                .evaluateMode(enumValue(EvaluateMode.class, object.get(FIELD_EVALUATE_MODE), EvaluateMode.LINEAR));
        key.valueSource(readValue(object.get(FIELD_VALUE), 0));
        // 斜率优先读新字段，缺失时回退旧格式的 inTangent / outTangent
        key.inSlopeSource(readValue(firstOf(object, FIELD_IN_SLOPE, FIELD_IN_TANGENT), 0));
        key.outSlopeSource(readValue(firstOf(object, FIELD_OUT_SLOPE, FIELD_OUT_TANGENT), 0));
        key.inLengthSource(readValue(object.get(FIELD_IN_LENGTH), legacyLength(object, true)));
        key.outLengthSource(readValue(object.get(FIELD_OUT_LENGTH), legacyLength(object, false)));
        return key;
    }

    /// 同一个量的新旧两个字段名，优先取新名字；两个都没有时返回 null
    private static @Nullable JsonElement firstOf(JsonObject object, String field, String legacy) {
        return object.has(field) ? object.get(field) : object.get(legacy);
    }

    /// 旧文件的曲柄长度：那时是「权重 + 加权模式」，加权模式没覆盖该方向时长度就是基准值 1，
    /// 覆盖到的按当时的线性系数换算成倍数
    private static float legacyLength(JsonObject object, boolean incoming) {
        String mode = stringValue(object, FIELD_LEGACY_WEIGHTED_MODE, "");
        boolean weighted = "BOTH".equals(mode) || (incoming ? "IN" : "OUT").equals(mode);

        if (!weighted) {
            return Keyframe.DEFAULT_LENGTH;
        }

        return floatValue(object, incoming ? FIELD_LEGACY_IN_WEIGHT : FIELD_LEGACY_OUT_WEIGHT, Keyframe.DEFAULT_LENGTH)
                * LEGACY_WEIGHT_TO_LENGTH;
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
                variable.source(readValue(object.get(FIELD_SOURCE), 0));
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
        PathNode node = new PathNode(new Vector3f());
        node.pathMode(enumValue(PathMode.class, object.get(FIELD_PATH_MODE), PathMode.LINEAR));
        // 缺字段时沿用节点自身的默认值（自动平滑默认开启），所以要在关掉它之前先读出来
        boolean smooth = booleanValue(object, FIELD_SMOOTH, node.smooth());
        // 写切线前先把自动平滑关掉：开启状态下写一侧会带着另一侧一起动，
        // 那样文件里存的对侧切线会被覆盖；开关在最后用 restoreSmooth 原样恢复，不再动切线
        node.restoreSmooth(false);
        Vector3f position = readVector(object.get(FIELD_POSITION));
        node.position(position.x, position.y, position.z);
        Vector3f inTangent = readVector(object.get(FIELD_IN_TANGENT));
        node.inTangent(inTangent.x, inTangent.y, inTangent.z);
        Vector3f outTangent = readVector(object.get(FIELD_OUT_TANGENT));
        node.outTangent(outTangent.x, outTangent.y, outTangent.z);
        node.restoreSmooth(smooth);
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
