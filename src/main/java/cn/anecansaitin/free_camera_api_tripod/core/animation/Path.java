package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.util.BezierUtils;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

@NullMarked
public class Path {
    private final ArrayList<PathNode> nodes = new ArrayList<>();
    private final FloatArrayList arcLengthTable = new FloatArrayList();
    private double totalLength;
    /// 索引缓存
    private int lastIndex = 0;
    private boolean positive = true;

    /// 临时变量缓存
    private final Vector3f cache1 = new Vector3f();
    private final Vector3f cache2 = new Vector3f();

    public Vector3f evaluate(float progress, Vector3f dest) {
        double length = totalLength * Math.clamp(progress, 0, 1);

        for (int i = 0; i < arcLengthTable.size(); i++) {
            float f = arcLengthTable.getFloat(i);
            length -= f;

            if (length > 0) {
                continue;
            }

            return switch (nodes.get(i).pathMode()) {
                case LINEAR -> {
                    Vector3f p = nodes.get(i).position();
                    Vector3f n = nodes.get(i + 1).position();
                    p.lerp(n, (float) ((f + length) / f), dest);
                    yield dest;
                }
                case BEZIER -> {
                    PathNode pre = nodes.get(i);
                    PathNode post = nodes.get(i + 1);
                    Vector3f p1 = pre.position();
                    Vector3f c1 = cache1.set(p1).add(pre.outTangent());
                    Vector3f p2 = post.position();
                    Vector3f c2 = cache2.set(p2).add(post.inTangent());

                    yield BezierUtils.bezier(p1, c1, c2, p2, (float) ((f + length) / f), dest);
                }
            };
        }

        throw new RuntimeException();
    }

    public void node(PathNode node) {
        nodes.add(node);
        updateArcLengthTable(nodes.size() - 1);
    }

    public @Nullable PathNode node(int index) {
        if (!validNode(index)) {
            return null;
        }

        return nodes.get(index);
    }

    public void node(int index, PathNode node) {
        nodes.set(index, node);
        updateArcLengthTable(index);
    }

    public void insertNode(int index, PathNode node) {
        nodes.add(index, node);
        updateArcLengthTable(index);
    }

    public void removeNode(int index) {
        nodes.remove(index);
        updateArcLengthTable(index);
    }

    private void updateArcLengthTable() {
        arcLengthTable.clear();

        if (nodes.size() < 2) {
            return;
        }

        for (int i = 0; i < nodes.size() - 1; i++) {
            float l = calculateLength(nodes.get(i), nodes.get(i + 1));
            arcLengthTable.add(l);
            totalLength += l;
        }
    }

    private void updateArcLengthTable(int nodeIndex) {
        int i = nodes.size() - arcLengthTable.size();

        switch (i) {
            case 0 -> arcLengthTable.removeFloat(nodeIndex);
            case 1 -> {}
            case 2 -> arcLengthTable.add(0);
            default -> throw new IllegalStateException("Unexpected node-curve count mismatch: nodes=" + nodes.size() + ", curves=" + arcLengthTable.size());
        }
        // 前
        if (validSegment(nodeIndex - 1)) {
            PathNode pre = nodes.get(nodeIndex - 1);
            PathNode post = nodes.get(nodeIndex);
            float length = calculateLength(pre, post);
            totalLength = totalLength + length - arcLengthTable.set(nodeIndex - 1, length);
        }
        // 后
        else if (validSegment(nodeIndex)) {
            PathNode pre = nodes.get(nodeIndex);
            PathNode post = nodes.get(nodeIndex + 1);
            float length = calculateLength(pre, post);
            totalLength = totalLength + length - arcLengthTable.set(nodeIndex, length);
        }
    }

    private float calculateLength(PathNode pre, PathNode post) {
        return switch (pre.pathMode()) {
            case LINEAR -> pre.position().distance(post.position());
            case BEZIER -> BezierUtils.bezierLength(
                    pre.position(),
                    pre.position().add(pre.outTangent(), cache1),
                    post.position().add(post.inTangent(), cache2),
                    post.position()
            );
        };
    }

    private boolean validNode(int nodeIndex) {
        return nodeIndex >= 0 && nodeIndex < nodes.size();
    }

    private boolean validSegment(int segmentIndex) {
        return segmentIndex >= 0 && segmentIndex < nodes.size() - 1;
    }
}
