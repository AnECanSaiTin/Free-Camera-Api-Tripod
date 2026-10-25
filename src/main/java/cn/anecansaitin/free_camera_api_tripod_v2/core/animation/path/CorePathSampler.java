package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.path;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.DistanceMode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathMode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathNode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 第一版路径采样：线性 / 贝塞尔 / Catmull-Rom 段与弧长参数化。
///
/// ## 口径
///
/// - 曲线段使用节点顺序；空路径是**错误**，单节点路径恒返回该节点；
/// - 弧长表按每段固定 `SUBDIVISIONS` 个等参数样本建立，查询时按累计距离线性插值；
/// - [DistanceMode.ABSOLUTE] 的值是弧长，单位格；[DistanceMode.PERCENT] 的值是 `0~1` 进度；
/// - 两端超出时夹到端点，不循环、不回绕；
/// - 结果包含位置与单位切线；零长度段无法单位化时切线为空。
///
/// 本类不读世界、不修改路径、不缓存跨帧可变状态；弧长表可按路径实例缓存于调用方，
/// 但表本身不进入文档。
@NullMarked
public final class CorePathSampler {

    /// 每段弧长表细分数。
    public static final int SUBDIVISIONS = 64;

    /// 路径为空或数值非法。
    public static final String EMPTY_PATH = "empty_path";

    /// 采样一个路径位置。
    ///
    /// @param path  路径
    /// @param mode  距离口径
    /// @param value 距离 / 百分比
    /// @return 采样成功或错误原因
    public Result sample(Path path, DistanceMode mode, double value) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(mode, "mode");

        if (!Double.isFinite(value)) {
            return new Result.Failure("path distance must be finite: " + value);
        }

        List<PathNode> nodes = path.nodes();

        if (nodes.isEmpty()) {
            return new Result.Failure(EMPTY_PATH);
        }

        if (nodes.size() == 1) {
            return new Result.Success(nodes.getFirst().position(), Vec3.ZERO, 0.0);
        }

        LengthTable table = table(path);
        double distance = mode == DistanceMode.PERCENT
                ? Math.max(0.0, Math.min(1.0, value)) * table.totalLength()
                : Math.max(0.0, Math.min(table.totalLength(), value));

        return table.sample(distance);
    }

    /// 路径总长度（按弧长表估算）。
    public double totalLength(Path path) {
        Objects.requireNonNull(path, "path");
        return path.nodes().size() < 2 ? 0.0 : table(path).totalLength();
    }

    private LengthTable table(Path path) {
        List<PathNode> nodes = path.nodes();
        List<Segment> segments = new ArrayList<>(nodes.size() - 1);
        double total = 0.0;

        for (int index = 0; index < nodes.size() - 1; index++) {
            PathNode start = nodes.get(index);
            PathNode end = nodes.get(index + 1);
            PathNode previous = index > 0 ? nodes.get(index - 1) : start;
            PathNode next = index + 2 < nodes.size() ? nodes.get(index + 2) : end;
            Segment segment = new Segment(start, end, previous, next, start.mode());
            double length = segment.approximateLength();
            segments.add(segment);
            total += length;
        }

        return new LengthTable(segments, total);
    }

    /// 采样结果。
    public sealed interface Result permits Result.Success, Result.Failure {
        /// 成功。
        ///
        /// @param position 位置
        /// @param tangent  单位切线；零长度段为 [Vec3#ZERO]
        /// @param distance 实际弧长
        record Success(Vec3 position, Vec3 tangent, double distance) implements Result {
            public Success {
                Objects.requireNonNull(position, "position");
                Objects.requireNonNull(tangent, "tangent");
            }
        }

        /// 失败。
        record Failure(String reason) implements Result {
            public Failure {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    private static final class LengthTable {
        private final List<Segment> segments;
        private final double totalLength;

        private LengthTable(List<Segment> segments, double totalLength) {
            this.segments = List.copyOf(segments);
            this.totalLength = totalLength;
        }

        private double totalLength() {
            return totalLength;
        }

        private Result sample(double distance) {
            if (totalLength == 0.0) {
                return new Result.Success(segments.getFirst().at(0.0), Vec3.ZERO, 0.0);
            }

            double remaining = distance;

            for (Segment segment : segments) {
                double length = segment.approximateLength();

                if (remaining <= length || segment == segments.getLast()) {
                    double local = length == 0.0 ? 0.0 : Math.min(1.0, remaining / length);
                    Vec3 position = segment.at(local);
                    Vec3 tangent = segment.tangent(local).normalize().orElse(Vec3.ZERO);
                    return new Result.Success(position, tangent, distance);
                }

                remaining -= length;
            }

            Segment last = segments.getLast();
            return new Result.Success(last.at(1.0), Vec3.ZERO, distance);
        }
    }

    private static final class Segment {
        private final PathNode start;
        private final PathNode end;
        private final PathNode previous;
        private final PathNode next;
        private final PathMode mode;

        private Segment(PathNode start, PathNode end, PathNode previous, PathNode next, PathMode mode) {
            this.start = start;
            this.end = end;
            this.previous = previous;
            this.next = next;
            this.mode = mode;
        }

        private double approximateLength() {
            Vec3 previousPoint = at(0.0);
            double length = 0.0;

            for (int index = 1; index <= SUBDIVISIONS; index++) {
                Vec3 point = at((double) index / SUBDIVISIONS);
                length += point.subtract(previousPoint).length();
                previousPoint = point;
            }

            return length;
        }

        private Vec3 at(double t) {
            return switch (mode) {
                case LINEAR -> start.position().lerp(end.position(), t);
                case BEZIER -> bezier(t);
                case CATMULL_ROM -> catmullRom(t);
            };
        }

        private Vec3 tangent(double t) {
            double epsilon = 1e-4;
            double from = Math.max(0.0, t - epsilon);
            double to = Math.min(1.0, t + epsilon);
            return at(to).subtract(at(from));
        }

        private Vec3 bezier(double t) {
            Vec3 p0 = start.position();
            Vec3 p1 = p0.add(start.outTangent());
            Vec3 p2 = end.position().add(end.inTangent());
            Vec3 p3 = end.position();
            double oneMinus = 1.0 - t;
            return p0.scale(oneMinus * oneMinus * oneMinus)
                    .add(p1.scale(3.0 * oneMinus * oneMinus * t))
                    .add(p2.scale(3.0 * oneMinus * t * t))
                    .add(p3.scale(t * t * t));
        }

        private Vec3 catmullRom(double t) {
            Vec3 p0 = previous.position();
            Vec3 p1 = start.position();
            Vec3 p2 = end.position();
            Vec3 p3 = next.position();
            double t2 = t * t;
            double t3 = t2 * t;

            return p1.scale(2.0)
                    .add(p2.subtract(p0).scale(t))
                    .add(p0.scale(2.0).subtract(p1.scale(5.0))
                            .add(p2.scale(4.0)).subtract(p3).scale(t2))
                    .add(p0.negate().add(p1.scale(3.0))
                            .subtract(p2.scale(3.0)).add(p3).scale(t3))
                    .scale(0.5);
        }
    }
}
