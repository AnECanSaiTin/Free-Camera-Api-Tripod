package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.path;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.DistanceMode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.PathNodeId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathNode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 路径采样：端点、线长、百分比、贝塞尔 / Catmull-Rom 端点与空路径失败。
class CorePathSamplerTest {

    private final CorePathSampler sampler = new CorePathSampler();

    @Test
    void singleNodeReturnsItsPosition() {
        Path path = Path.of("single", node("a", 1, 2, 3));

        CorePathSampler.Result.Success result = success(sampler.sample(path, DistanceMode.ABSOLUTE, 5));

        assertEquals(new Vec3(1, 2, 3), result.position());
        assertEquals(0.0, result.distance(), 1e-9);
    }

    @Test
    void linearPathUsesArcLengthAndPercent() {
        Path path = linear();

        assertEquals(10.0, sampler.totalLength(path), 1e-6);
        assertEquals(new Vec3(5, 0, 0),
                success(sampler.sample(path, DistanceMode.ABSOLUTE, 5)).position());
        assertEquals(new Vec3(5, 0, 0),
                success(sampler.sample(path, DistanceMode.PERCENT, 0.5)).position());
        assertEquals(new Vec3(0, 0, 0),
                success(sampler.sample(path, DistanceMode.ABSOLUTE, -3)).position());
        assertEquals(new Vec3(10, 0, 0),
                success(sampler.sample(path, DistanceMode.ABSOLUTE, 99)).position());
    }

    @Test
    void bezierSegmentHitsEndpoints() {
        PathNode first = PathNode.bezier(new PathNodeId("a"), new Vec3(0, 0, 0),
                new Vec3(1, 0, 0), false);
        PathNode second = PathNode.at(new PathNodeId("b"), new Vec3(10, 0, 0));
        Path path = new Path("bezier", List.of(first, second));

        assertEquals(new Vec3(0, 0, 0),
                success(sampler.sample(path, DistanceMode.PERCENT, 0)).position());
        assertEquals(new Vec3(10, 0, 0),
                success(sampler.sample(path, DistanceMode.PERCENT, 1)).position());
    }

    @Test
    void catmullRomSegmentHitsEndpoints() {
        PathNode first = PathNode.at(new PathNodeId("a"), new Vec3(0, 0, 0))
                .withMode(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathMode.CATMULL_ROM);
        PathNode second = PathNode.at(new PathNodeId("b"), new Vec3(4, 0, 0))
                .withMode(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathMode.CATMULL_ROM);
        PathNode third = PathNode.at(new PathNodeId("c"), new Vec3(8, 4, 0))
                .withMode(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathMode.CATMULL_ROM);
        Path path = new Path("catmull", List.of(first, second, third));

        assertTrue(sampler.totalLength(path) > 0);
        assertEquals(new Vec3(0, 0, 0),
                success(sampler.sample(path, DistanceMode.PERCENT, 0)).position());
        assertEquals(new Vec3(8, 4, 0),
                success(sampler.sample(path, DistanceMode.PERCENT, 1)).position());
    }

    @Test
    void emptyPathFails() {
        CorePathSampler.Result result = sampler.sample(new Path("empty", List.of()),
                DistanceMode.ABSOLUTE, 0);

        assertTrue(result instanceof CorePathSampler.Result.Failure);
        assertEquals(CorePathSampler.EMPTY_PATH,
                ((CorePathSampler.Result.Failure) result).reason());
    }

    @Test
    void nonFiniteDistanceFails() {
        assertTrue(sampler.sample(linear(), DistanceMode.ABSOLUTE, Double.NaN)
                instanceof CorePathSampler.Result.Failure);
    }

    private static CorePathSampler.Result.Success success(CorePathSampler.Result result) {
        assertTrue(result instanceof CorePathSampler.Result.Success, () -> "unexpected: " + result);
        return (CorePathSampler.Result.Success) result;
    }

    private static Path linear() {
        return new Path("linear", List.of(
                node("a", 0, 0, 0),
                node("b", 10, 0, 0)));
    }

    private static PathNode node(String id, double x, double y, double z) {
        return PathNode.at(new PathNodeId(id), new Vec3(x, y, z));
    }
}
