package cn.anecansaitin.free_camera_api_tripod.api.animation.path;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.DynamicField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ExpressionContext;
import cn.anecansaitin.free_camera_api_tripod.util.SplineUtils;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
        return evaluate(distance, dest, null);
    }

    /// 带上下文的求值：节点上挂了公式的坐标 / 切线分量按公式算，其余用固定数值
    public Vector3f evaluate(float distance, Vector3f dest, @Nullable ExpressionContext context) {
        int size = nodes.size();

        switch (size) {
            case 0 -> throw new IllegalStateException("Cannot evaluate path: no path nodes available");
            case 1 -> {
                return dest.set(position(nodes.getFirst(), context));
            }
        }

        double length = Math.clamp(distance, 0, totalLength);
        int index = findFloorIndex(length);
        PathNode left = nodes.get(index);

        if (index == size - 1) {
            return dest.set(position(left, context));
        }

        PathNode right = nodes.get(index + 1);
        double preLength = index == 0 ? 0 : cumulativeLengths.getDouble(index - 1);
        // 线段长度为 0（或数据异常）时归一化参数会变成 0/0，位置随即变成 NaN，
        // 随后又会以 NaN 的形式进入相机与渲染；这里退化成取线段起点
        float segment = index < segmentLengths.size() ? segmentLengths.getFloat(index) : 0f;
        float delta = segment > 0 ? (float) ((length - preLength) / segment) : 0f;
        delta = Math.clamp(delta, 0f, 1f);

        return switch (left.pathMode()) {
            case LINEAR -> dest.set(position(left, context)).lerp(position(right, context), delta);
            case BEZIER -> SplineUtils.bezier(
                    position(left, context),
                    position(left, context).add(outTangent(left, context), new Vector3f()),
                    position(right, context).add(inTangent(right, context), new Vector3f()),
                    position(right, context),
                    delta,
                    dest
            );
            case CATMULL_ROM -> SplineUtils.catmullRom(
                    catmullRomP1(index, context),
                    position(left, context),
                    position(right, context),
                    catmullRomP4(index, context),
                    delta,
                    dest
            );
        };
    }

    public Vector3f evaluate(Vector3f dest, float progress) {
        return evaluate((float) (progress * totalLength), dest);
    }

    public Vector3f evaluate(Vector3f dest, float progress, @Nullable ExpressionContext context) {
        return evaluate((float) (progress * totalLength), dest, context);
    }

    /// 节点位置：挂了公式的分量按公式算，其余用固定值
    private static Vector3fc position(PathNodec node, @Nullable ExpressionContext context) {
        return resolve(node, node.position(), DynamicField.NODE_X, DynamicField.NODE_Y, DynamicField.NODE_Z, context);
    }

    private static Vector3fc inTangent(PathNodec node, @Nullable ExpressionContext context) {
        return resolve(node, node.inTangent(), DynamicField.NODE_IN_X, DynamicField.NODE_IN_Y, DynamicField.NODE_IN_Z, context);
    }

    private static Vector3fc outTangent(PathNodec node, @Nullable ExpressionContext context) {
        return resolve(node, node.outTangent(), DynamicField.NODE_OUT_X, DynamicField.NODE_OUT_Y, DynamicField.NODE_OUT_Z, context);
    }

    /// 按公式覆盖向量的三个分量；没有上下文或该向量一个分量都没挂公式时直接返回原向量，不产生临时对象
    private static Vector3fc resolve(PathNodec node, Vector3fc source, DynamicField x, DynamicField y, DynamicField z,
                                     @Nullable ExpressionContext context) {
        if (context == null || !(node instanceof PathNode pathNode)) {
            return source;
        }

        if (!pathNode.dynamic(x) && !pathNode.dynamic(y) && !pathNode.dynamic(z)) {
            return source;
        }

        return new Vector3f(
                component(pathNode, x, source.x(), context),
                component(pathNode, y, source.y(), context),
                component(pathNode, z, source.z(), context));
    }

    /// 单个分量：挂了公式按公式算，算不出来（公式非法或变量缺失）回退固定值
    private static float component(PathNode node, DynamicField field, float fallback, ExpressionContext context) {
        String expression = node.expression(field);

        if (expression == null) {
            return fallback;
        }

        float evaluated = context.evaluate(expression);
        return Float.isNaN(evaluated) ? fallback : evaluated;
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
                    // 弧长是静态度量：一律按固定数值算，不接求值上下文。
                    // 否则「路径总长」会随时间变化，百分比口径与「百分比 → 弧长」的换算就无从谈起
                    catmullRomP1(currentIndex, null),
                    pre.position(),
                    post.position(),
                    catmullRomP4(currentIndex, null)
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

    private Vector3fc catmullRomP1(int currentIndex, @Nullable ExpressionContext context) {
        if (currentIndex > 0) {
            PathNode node = nodes.get(currentIndex - 1);
            return node.pathMode() == PathMode.CATMULL_ROM ? position(node, context) : position(nodes.get(currentIndex), context);
        } else {
            return position(nodes.get(currentIndex), context);
        }
    }

    private Vector3fc catmullRomP4(int currentIndex, @Nullable ExpressionContext context) {
        if (currentIndex < size() - 2) {
            PathNode node = nodes.get(currentIndex + 2);
            return node.pathMode() == PathMode.CATMULL_ROM ? position(node, context) : position(nodes.get(currentIndex + 1), context);
        } else {
            return position(nodes.get(currentIndex + 1), context);
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
