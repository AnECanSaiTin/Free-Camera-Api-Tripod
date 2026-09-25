package cn.anecansaitin.free_camera_api_tripod.api.animation.path;

import cn.anecansaitin.free_camera_api_tripod.util.SplineUtils;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;

@NullMarked
public class Path implements Pathc {
    private String name;
    private final ArrayList<PathNode> nodes = new ArrayList<>();
    private final FloatArrayList segmentLengths = new FloatArrayList();
    private final DoubleArrayList cumulativeLengths = new DoubleArrayList();
    private double totalLength;
    /// 索引缓存
    private int lastIndex = 0;
    private boolean positive = true;

    public Path() {
        name = "Path";
    }

    public Path(String name) {
        this.name = name;
    }

    @Override
    public Vector3f evaluate(float distance, Vector3f dest) {
        int size = nodes.size();

        switch (size) {
            case 0 -> throw new IllegalStateException("Cannot evaluate path: no path nodes available");
            case 1 -> {
                return dest.set(nodes.getFirst().position());
            }
        }

        double length = Math.clamp(distance, 0, totalLength);
        int index = findFloorIndex(length);
        PathNode left = nodes.get(index);

        if (index == size - 1) {
            return dest.set(left.position());
        }

        PathNode right = nodes.get(index + 1);
        double preLength = index == 0 ? 0 : cumulativeLengths.getDouble(index - 1);
        // 线段长度为 0（或数据异常）时归一化参数会变成 0/0，位置随即变成 NaN，
        // 随后又会以 NaN 的形式进入相机与渲染；这里退化成取线段起点
        float segment = index < segmentLengths.size() ? segmentLengths.getFloat(index) : 0f;
        float delta = segment > 0 ? (float) ((length - preLength) / segment) : 0f;
        delta = Math.clamp(delta, 0f, 1f);

        return switch (left.pathMode()) {
            case LINEAR -> dest.set(left.position()).lerp(right.position(), delta);
            case BEZIER -> SplineUtils.bezier(
                    left.position(),
                    left.position().add(left.outTangent(), new Vector3f()),
                    right.position().add(right.inTangent(), new Vector3f()),
                    right.position(),
                    delta,
                    dest
            );
            case CATMULL_ROM -> SplineUtils.catmullRom(
                    catmullRomP1(index),
                    left.position(),
                    right.position(),
                    catmullRomP4(index),
                    delta,
                    dest
            );
        };
    }

    public Vector3f evaluate(Vector3f dest, float progress) {
        return evaluate((float) (progress * totalLength), dest);
    }

    public void node(PathNode node) {
        nodes.add(node);

        if (nodes.size() == 1) {
            return;
        }

        segmentLengths.add(0);
        cumulativeLengths.add(0);
        updateArcLengthTable(segmentLengths.size() - 1, segmentLengths.size());
    }

    @Override
    public PathNodec node(int index) {
        if (invalidNode(index)) {
            throw new IndexOutOfBoundsException("Invalid node index: " + index);
        }

        return nodes.get(index);
    }

    public void node(int index, PathNode node) {
        nodes.set(index, node);
        updateArcLengthTable(Math.max(0, index - 2), Math.min(segmentLengths.size(), index + 2));
    }

    public boolean updateNode(int index, NodeUpdater updater) {
        if (invalidNode(index)) {
            return false;
        }

        updater.update(nodes.get(index));
        updateArcLengthTable(Math.max(0, index - 2), Math.min(segmentLengths.size(), index + 2));
        return true;
    }

    public void insertNode(int index, PathNode node) {
        nodes.add(index, node);

        if (nodes.size() == 1) {
            return;
        }

        segmentLengths.add(index, 0);
        cumulativeLengths.add(index, 0);
        updateArcLengthTable(Math.max(0, index - 2), Math.min(segmentLengths.size(), index + 2));
    }

    public boolean removeNode(int index) {
        if (invalidNode(index)) {
            return false;
        }

        // 只剩一个节点时其下没有任何线段，不能再从弧长表里移除，否则会越界
        if (nodes.size() >= 2) {
            if (index == nodes.size() - 1) {
                segmentLengths.removeFloat(index - 1);
                cumulativeLengths.removeDouble(index - 1);
            } else {
                segmentLengths.removeFloat(index);
                cumulativeLengths.removeDouble(index);
            }
        }

        nodes.remove(index);

        if (nodes.size() < 2) {
            totalLength = 0;
            segmentLengths.clear();
            cumulativeLengths.clear();
            return true;
        }

        updateArcLengthTable(Math.max(0, index - 2), Math.min(segmentLengths.size(), index + 2));
        return true;
    }

    /// 调整节点顺序：把 from 处的节点移动到 to 处（用于手工排序路径点）。
    /// 越界或原地不动时返回 false。
    public boolean moveNode(int from, int to) {
        if (invalidNode(from) || to < 0 || to >= nodes.size() || from == to) {
            return false;
        }

        nodes.add(to, nodes.remove(from));
        // 顺序变了，每段的两端节点都可能不同，弧长表整体重建
        // todo 可以精确到具体的两个范围
        updateArcLengthTable();
        return true;
    }

    private void updateArcLengthTable() {
        segmentLengths.clear();
        cumulativeLengths.clear();

        if (nodes.size() < 2) {
            return;
        }

        double totalLength = 0;

        for (int i = 0; i < nodes.size() - 1; i++) {
            float l = calculateLength(i);
            segmentLengths.add(l);
            totalLength += l;
            cumulativeLengths.add(totalLength);
        }
    }

    private void updateArcLengthTable(int begin, int end) {
        // 目前所有调用该方法的函数都是按照4个受影响曲线考虑，对于直线与贝塞尔曲线虽多计算两段，开销较小可忽略
        if (begin < 0) {
            throw new IllegalArgumentException("Invalid range: begin must be greater than or equal to 0");
        }

        if (end > segmentLengths.size()) {
            throw new IllegalArgumentException("Invalid range: end must be less than or equal to the size of the path");
        }

        if (begin >= end) {
            return;
        }

        for (int i = begin; i < end; i++) {
            segmentLengths.set(i, calculateLength(i));
        }

        if (begin == 0) {
            cumulativeLengths.set(0, segmentLengths.getFloat(0));
        }

        for (int i = Math.max(begin, 1); i < cumulativeLengths.size(); i++) {
            cumulativeLengths.set(i, cumulativeLengths.getDouble(i - 1) + segmentLengths.getFloat(i));
        }

        totalLength = cumulativeLengths.getDouble(cumulativeLengths.size() - 1);
    }

    private float calculateLength(int currentIndex) {
        PathNode pre = nodes.get(currentIndex);
        PathNode post = nodes.get(currentIndex + 1);

        return switch (pre.pathMode()) {
            case LINEAR -> pre.position().distance(post.position());
            case BEZIER -> SplineUtils.bezierLength(
                    pre.position(),
                    pre.position().add(pre.outTangent(), new Vector3f()),
                    post.position().add(post.inTangent(), new Vector3f()),
                    post.position()
            );
            case CATMULL_ROM -> SplineUtils.catmullRomLength(
                    catmullRomP1(currentIndex),
                    pre.position(),
                    post.position(),
                    catmullRomP4(currentIndex)
            );
        };
    }

    private boolean invalidNode(int nodeIndex) {
        return nodeIndex < 0 || nodeIndex >= nodes.size();
    }

    private boolean validSegment(int segmentIndex) {
        return segmentIndex >= 0 && segmentIndex < nodes.size() - 1;
    }

    private int findFloorIndex(double length) {
        int size = cumulativeLengths.size();

        // 弧长表为空时没有可用的分段，交给调用方按首节点处理
        if (size == 0) {
            return 0;
        }

        int maxFloor = size - 1;

        if (lastIndex >= 0 && lastIndex < maxFloor) {
            double left = lastIndex == 0 ? 0 : cumulativeLengths.getDouble(lastIndex - 1);
            double right = cumulativeLengths.getDouble(lastIndex);

            if (left <= length && length < right) {
                return lastIndex;
            }

            if (positive) {
                if (validSegment(lastIndex + 1)) {
                    left = right;
                    right = cumulativeLengths.getDouble(lastIndex + 1);

                    if (left <= length && length < right) {
                        return ++lastIndex;
                    }
                }
            } else {
                if (validSegment(lastIndex - 1)) {
                    right = left;
                    left = cumulativeLengths.getDouble(lastIndex - 1);

                    if (left <= length && length < right) {
                        return --lastIndex;
                    }
                }
            }
        }

        int i = binarySearch(length);
        i = i < 0 ? -i - 1 : i;
        i = Math.clamp(i, 0, maxFloor);
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

    private Vector3fc catmullRomP1(int currentIndex) {
        if (currentIndex > 0) {
            PathNode node = nodes.get(currentIndex - 1);
            return node.pathMode() == PathMode.CATMULL_ROM ? node.position() : nodes.get(currentIndex).position();
        } else {
            return nodes.get(currentIndex).position();
        }
    }

    private Vector3fc catmullRomP4(int currentIndex) {
        if (currentIndex < size() - 2) {
            PathNode node = nodes.get(currentIndex + 2);
            return node.pathMode() == PathMode.CATMULL_ROM ? node.position() : nodes.get(currentIndex + 1).position();
        } else {
            return nodes.get(currentIndex + 1).position();
        }
    }

    @Override
    public double totalLength() {
        return totalLength;
    }


    @Override
    public double nodeDistance(int index) {
        if (index <= 0) {
            return 0;
        }

        if (index >= nodes.size()) {
            return totalLength;
        }

        return cumulativeLengths.getDouble(index - 1);
    }

    @Override
    public int size() {
        return nodes.size();
    }

    @Override
    public String name() {
        return name;
    }

    public Path name(String name) {
        this.name = name;
        return this;
    }

    public Path clear() {
        nodes.clear();
        cumulativeLengths.clear();
        segmentLengths.clear();
        totalLength = 0;
        lastIndex = 0;
        return this;
    }

    @FunctionalInterface
    public interface NodeUpdater {
        void update(PathNode node);
    }
}
