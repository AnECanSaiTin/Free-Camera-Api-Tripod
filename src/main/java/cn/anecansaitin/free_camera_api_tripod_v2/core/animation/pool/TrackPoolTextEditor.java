package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.QuaternionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 轨道池属性面板的文本编辑入口（无 GUI）。
///
/// 数值字段接受十进制常量；空白表示关闭该属性。旋转文本接受三个逗号分隔的度数
/// `yaw,pitch,roll`，转换为 Euler 组合来源；布尔字段接受 `true` / `false` / 空白。
/// 解析失败抛 [IllegalArgumentException]，不提交候选。
@NullMarked
public final class TrackPoolTextEditor {

    private final TrackPoolEditorSession session;

    public TrackPoolTextEditor(TrackPoolEditorSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /// 修改一个数值属性字段。
    public TrackPoolEditResult setNumeric(RigId rigId, NumericField field, String text) {
        Objects.requireNonNull(field, "field");
        PropertyBinding<NumericSource> binding = numericBinding(text);
        return session.updateRigProperties(rigId, properties -> replaceNumeric(properties, field, binding));
    }

    /// 修改机位活动属性。
    public TrackPoolEditResult setActive(RigId rigId, String text) {
        PropertyBinding<BooleanSource> binding = booleanBinding(text);

        return session.updateRigProperties(rigId, properties -> new RigProperties(
                properties.position(), properties.rotation(), properties.fov(),
                new ActiveProperty(binding), properties.rect(), properties.obstacle()));
    }

    /// 修改避障启用属性。
    public TrackPoolEditResult setObstacleEnabled(RigId rigId, String text) {
        PropertyBinding<BooleanSource> binding = booleanBinding(text);

        return session.updateRigProperties(rigId, properties -> new RigProperties(
                properties.position(), properties.rotation(), properties.fov(), properties.active(),
                properties.rect(), new ObstacleProperty(binding, properties.obstacle().margin())));
    }

    /// 以 `yaw,pitch,roll` 度设置整体旋转；空白关闭旋转属性。
    public TrackPoolEditResult setRotationEuler(RigId rigId, String text) {
        if (text == null || text.isBlank()) {
            return session.updateRigProperties(rigId, properties -> new RigProperties(
                    properties.position(), RotationProperty.disabled(), properties.fov(),
                    properties.active(), properties.rect(), properties.obstacle()));
        }

        String[] parts = text.split(",", -1);

        if (parts.length != 3) {
            throw new IllegalArgumentException("Rotation text must be yaw,pitch,roll");
        }

        double yaw = parseFinite(parts[0].trim(), "yaw");
        double pitch = parseFinite(parts[1].trim(), "pitch");
        double roll = parseFinite(parts[2].trim(), "roll");
        QuaternionSource.EulerComposite source = new QuaternionSource.EulerComposite(
                new NumericSource.Constant(yaw),
                new NumericSource.Constant(pitch),
                new NumericSource.Constant(roll),
                QuaternionSource.RotationOrder.YXZ,
                QuaternionSource.AngleUnit.DEGREES);

        return session.updateRigProperties(rigId, properties -> new RigProperties(
                properties.position(), new RotationProperty(PropertyBinding.enabled(source)),
                properties.fov(), properties.active(), properties.rect(), properties.obstacle()));
    }

    /// 以 `x,y,z,w` 设置四元数旋转；空白关闭旋转属性。
    public TrackPoolEditResult setRotationQuaternion(RigId rigId, String text) {
        if (text == null || text.isBlank()) {
            return session.updateRigProperties(rigId, properties -> new RigProperties(
                    properties.position(), RotationProperty.disabled(), properties.fov(),
                    properties.active(), properties.rect(), properties.obstacle()));
        }

        String[] parts = text.split(",", -1);

        if (parts.length != 4) {
            throw new IllegalArgumentException("Quaternion text must be x,y,z,w");
        }

        double x = parseFinite(parts[0].trim(), "x");
        double y = parseFinite(parts[1].trim(), "y");
        double z = parseFinite(parts[2].trim(), "z");
        double w = parseFinite(parts[3].trim(), "w");
        var quaternion = new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion(x, y, z, w).normalized();

        return session.updateRigProperties(rigId, properties -> new RigProperties(
                properties.position(),
                new RotationProperty(PropertyBinding.enabled(
                        new QuaternionSource.Constant(quaternion))),
                properties.fov(), properties.active(), properties.rect(), properties.obstacle()));
    }

    private static RigProperties replaceNumeric(RigProperties properties,
                                                NumericField field,
                                                PropertyBinding<NumericSource> binding) {
        PositionProperty position = properties.position();
        ObstacleProperty obstacle = properties.obstacle();
        RectProperty rect = properties.rect();

        return switch (field) {
            case POSITION_X -> new RigProperties(
                    new PositionProperty(binding, position.y(), position.z()),
                    properties.rotation(), properties.fov(), properties.active(),
                    rect, obstacle);
            case POSITION_Y -> new RigProperties(
                    new PositionProperty(position.x(), binding, position.z()),
                    properties.rotation(), properties.fov(), properties.active(),
                    rect, obstacle);
            case POSITION_Z -> new RigProperties(
                    new PositionProperty(position.x(), position.y(), binding),
                    properties.rotation(), properties.fov(), properties.active(),
                    rect, obstacle);
            case FOV -> new RigProperties(position, properties.rotation(),
                    new FovProperty(binding), properties.active(), rect, obstacle);
            case RECT_X -> new RigProperties(position, properties.rotation(), properties.fov(),
                    properties.active(), replaceRect(rect, RectComponent.X, binding), obstacle);
            case RECT_Y -> new RigProperties(position, properties.rotation(), properties.fov(),
                    properties.active(), replaceRect(rect, RectComponent.Y, binding), obstacle);
            case RECT_WIDTH -> new RigProperties(position, properties.rotation(), properties.fov(),
                    properties.active(), replaceRect(rect, RectComponent.WIDTH, binding), obstacle);
            case RECT_HEIGHT -> new RigProperties(position, properties.rotation(), properties.fov(),
                    properties.active(), replaceRect(rect, RectComponent.HEIGHT, binding), obstacle);
            case OBSTACLE_MARGIN -> new RigProperties(position, properties.rotation(),
                    properties.fov(), properties.active(), rect,
                    new ObstacleProperty(obstacle.enabled(), binding));
        };
    }

    private static RectProperty replaceRect(RectProperty rect,
                                            RectComponent component,
                                            PropertyBinding<NumericSource> binding) {
        return switch (rect) {
            case RectProperty.Normalized normalized -> new RectProperty.Normalized(
                    component == RectComponent.X ? binding : normalized.x(),
                    component == RectComponent.Y ? binding : normalized.y(),
                    component == RectComponent.WIDTH ? binding : normalized.width(),
                    component == RectComponent.HEIGHT ? binding : normalized.height());
            case RectProperty.Anchored anchored -> new RectProperty.Anchored(
                    anchored.corner(),
                    component == RectComponent.X ? binding : anchored.x(),
                    component == RectComponent.Y ? binding : anchored.y(),
                    component == RectComponent.WIDTH ? binding : anchored.width(),
                    component == RectComponent.HEIGHT ? binding : anchored.height());
        };
    }

    private static PropertyBinding<NumericSource> numericBinding(String text) {
        if (text == null || text.isBlank()) {
            return PropertyBinding.disabled();
        }

        String trimmed = text.trim();

        if (trimmed.startsWith("=")) {
            String expression = trimmed.substring(1).trim();

            if (expression.isEmpty()) {
                throw new IllegalArgumentException("Formula text must not be empty");
            }

            return PropertyBinding.enabled(new NumericSource.Formula(expression));
        }

        if (trimmed.startsWith("#")) {
            String track = trimmed.substring(1).trim();

            if (track.isEmpty()) {
                throw new IllegalArgumentException("Track reference text must not be empty");
            }

            return PropertyBinding.enabled(new NumericSource.Track(
                    new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId(track)));
        }

        return PropertyBinding.enabled(new NumericSource.Constant(parseFinite(trimmed, "value")));
    }

    private static PropertyBinding<BooleanSource> booleanBinding(String text) {
        if (text == null || text.isBlank()) {
            return PropertyBinding.disabled();
        }

        return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true" -> PropertyBinding.enabled(new BooleanSource.Constant(true));
            case "false" -> PropertyBinding.enabled(new BooleanSource.Constant(false));
            default -> throw new IllegalArgumentException("Boolean text must be true, false or blank");
        };
    }

    private static double parseFinite(String text, String name) {
        try {
            double value = Double.parseDouble(text);

            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }

            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " is not a number: " + text);
        }
    }

    /// 可文本编辑的数值字段。
    public enum NumericField {
        POSITION_X,
        POSITION_Y,
        POSITION_Z,
        FOV,
        RECT_X,
        RECT_Y,
        RECT_WIDTH,
        RECT_HEIGHT,
        OBSTACLE_MARGIN
    }

    private enum RectComponent {
        X,
        Y,
        WIDTH,
        HEIGHT
    }
}
