package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.util.BezierUtils;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

@NullMarked
public class Path {
    private final ArrayList<PathNode> nodes = new ArrayList<>();
    private final FloatArrayList segmentLengths = new FloatArrayList();
    private final DoubleArrayList cumulativeLengths = new DoubleArrayList();
    private double totalLength;
    /// 索引缓存
    private int lastIndex = 0;
    private boolean positive = true;

    public Vector3f evaluate(float progress, Vector3f dest) {
        int size = nodes.size();

        switch (size) {
            case 0 -> throw new IllegalStateException("Cannot evaluate path: no path nodes available");
            case 1 -> {
                return dest.set(nodes.getFirst().position());
            }
        }

        double length = totalLength * Math.clamp(progress, 0, 1);
        int index = findFloorIndex(length);
        PathNode left = nodes.get(index);

        if (index == size - 1) {
            return dest.set(left.position());
        }

        PathNode right = nodes.get(index + 1);
        double preLength = index == 0 ? 0 : cumulativeLengths.getDouble(index - 1);
        float delta = (float) ((length - preLength) / segmentLengths.getFloat(index));

        return switch (left.pathMode()) {
            case LINEAR -> dest.set(left.position()).lerp(right.position(), delta);
            case BEZIER -> BezierUtils.bezier(
                    left.position(),
                    left.position().add(left.outTangent(), new Vector3f()),
                    right.position().add(right.inTangent(), new Vector3f()),
                    right.position(),
                    delta,
                    dest
            );
        };
    }

    public void node(PathNode node) {
        nodes.add(node);
        segmentLengths.add(0);
        cumulativeLengths.add(0);
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
        segmentLengths.add(index, 0);
        cumulativeLengths.add(index, 0);
        updateArcLengthTable(index);
    }

    public void removeNode(int index) {
        nodes.remove(index);
        segmentLengths.removeFloat(index);
        cumulativeLengths.removeDouble(index);
        updateArcLengthTable(index);
    }

    private void updateArcLengthTable() {
        segmentLengths.clear();
        cumulativeLengths.clear();

        if (nodes.size() < 2) {
            return;
        }

        double totalLength = 0;

        for (int i = 0; i < nodes.size() - 1; i++) {
            float l = calculateLength(nodes.get(i), nodes.get(i + 1));
            segmentLengths.add(l);
            totalLength += l;
            cumulativeLengths.add(totalLength);
        }
    }

    private void updateArcLengthTable(int nodeIndex) {
        if (nodes.size() < 2) {
            totalLength = 0;
            cumulativeLengths.clear();
            segmentLengths.clear();
            return;
        }

        // 前
        if (validSegment(nodeIndex - 1)) {
            PathNode pre = nodes.get(nodeIndex - 1);
            PathNode post = nodes.get(nodeIndex);
            float length = calculateLength(pre, post);
            segmentLengths.set(nodeIndex - 1, length);
        }
        // 后
        if (validSegment(nodeIndex)) {
            PathNode pre = nodes.get(nodeIndex);
            PathNode post = nodes.get(nodeIndex + 1);
            float length = calculateLength(pre, post);
            segmentLengths.set(nodeIndex, length);
        }

        if (nodeIndex == 0 || nodeIndex == 1) {
            cumulativeLengths.set(0, segmentLengths.getFloat(0));
        }

        for (int j = Math.max(2, nodeIndex - 1); j < nodes.size(); j++) {
            double pre = cumulativeLengths.getDouble(j - 2);
            float current = segmentLengths.getFloat(j - 1);
            cumulativeLengths.set(j - 1, pre + current);
        }

        totalLength = cumulativeLengths.getDouble(nodes.size() - 2);
    }

    private float calculateLength(PathNode pre, PathNode post) {
        return switch (pre.pathMode()) {
            case LINEAR -> pre.position().distance(post.position());
            case BEZIER -> BezierUtils.bezierLength(
                    pre.position(),
                    pre.position().add(pre.outTangent(), new Vector3f()),
                    post.position().add(post.inTangent(), new Vector3f()),
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

    private int findFloorIndex(double length) {
        int size = cumulativeLengths.size();
        int maxFloor = size - 1;

        if (lastIndex >= 0 && lastIndex < maxFloor) {
            double left = lastIndex == 0 ? 0 : cumulativeLengths.getDouble(lastIndex - 1);
            double right = cumulativeLengths.getDouble(lastIndex);

            if (left <= length && length < right) {
                return lastIndex;
            }

            if (positive) {
                if (validSegment(lastIndex + 1)) {
                    right = cumulativeLengths.getDouble(lastIndex + 1);

                    if (length < right) {
                        return ++lastIndex;
                    }
                }
            } else {
                if (validSegment(lastIndex - 1)) {
                    right = cumulativeLengths.getDouble(lastIndex - 1);

                    if (length < right) {
                        return --lastIndex;
                    }
                }
            }
        }

        int i = binarySearch(length);
        i = i < 0 ? -i - 1 : i;
        positive = i >= lastIndex;
        return lastIndex = i;
    }

    private int binarySearch(double length) {
        int low = 0;
        int high = cumulativeLengths.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            double midVal = cumulativeLengths.getDouble(mid);

            if (midVal < length) {
                low = mid + 1;
            } else if (midVal > length) {
                high = mid - 1;
            } else {
                return mid;
            }
        }

        return -(low + 1);
    }

    public int size() {
        return nodes.size();
    }
}
