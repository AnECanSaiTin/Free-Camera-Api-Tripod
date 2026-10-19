package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.PathNodeId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/// 路径节点：坐标 + 两侧切线 + 段插值方式（主设计 §3.3）。
///
/// ## 纯几何
///
/// 路径是**纯几何**：节点不参与表达式求值（随时间变化由曲线通道负责），
/// 因此这里只有不可变数值，没有值源。几何计算、渲染与播放取点读的是同一份坐标，
/// 与不变量 I6 在几何侧的对应物一致。
///
/// ## 切线只在贝塞尔模式下有意义
///
/// `LINEAR` 与 `CATMULL_ROM` 不使用 `inTangent` / `outTangent`：
/// 前者是直线段，后者的切线由相邻节点自动生成。为避免“数据里存着切线但没人读”的
/// 歧义，这两种模式下切线在构造时归一为 [Vec3#ZERO]，只有 `BEZIER` 允许非零。
///
/// ## 自动平滑是纯派生，不是副作用
///
/// `smooth` 打开时两侧切线互为反向。旧实现把这条规则藏在
/// `PathNode.inTangent(x, y, z)` 的**写入方法**里：就地改对象并顺手镜像另一侧，
/// 于是“改一下切线”会意外改两个字段，也无法在候选文档上预览。
///
/// 本契约把 `smooth` 变成数据，镜像由 [withInTangent] / [withOutTangent]
/// **显式**完成并返回派生对象——调用方看得见这件事会发生，也不需要可变对象。
///
/// ## 不可变
///
/// 坐标与切线用不可变 [Vec3]，**不用 JOML 可变向量**（主设计 §3.2）。
/// 需要与渲染或播放宿主交互时，在宿主边界转成可变缓冲区，转换点集中。
///
/// @param id         稳定身份；本路径内唯一，移动与重排都不改变它
/// @param position   坐标
/// @param inTangent  入侧切线；仅 `BEZIER` 可用，其余模式为 [Vec3#ZERO]
/// @param outTangent 出侧切线；仅 `BEZIER` 可用，其余模式为 [Vec3#ZERO]
/// @param mode       本节点到下一个节点之间的段插值方式
/// @param smooth     自动平滑：两侧切线互为反向
@NullMarked
public record PathNode(
        PathNodeId id,
        Vec3 position,
        Vec3 inTangent,
        Vec3 outTangent,
        PathMode mode,
        boolean smooth) {

    /// 结构编码：`{id, position, in_tangent, out_tangent, mode, smooth}`。
    ///
    /// 切线仅 `BEZIER` 使用；非贝塞尔模式解码后由构造器归一为零（不是错误）。
    public static final MapCodec<PathNode> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PathNodeId.CODEC.fieldOf("id").forGetter(PathNode::id),
            Vec3.CODEC.fieldOf("position").forGetter(PathNode::position),
            Vec3.CODEC.fieldOf("in_tangent").forGetter(PathNode::inTangent),
            Vec3.CODEC.fieldOf("out_tangent").forGetter(PathNode::outTangent),
            Codecs.enumByName(PathMode.class).fieldOf("mode").forGetter(PathNode::mode),
            Codec.BOOL.fieldOf("smooth").forGetter(PathNode::smooth)
    ).apply(instance, PathNode::new));

    /// 独立解析一个路径节点时使用的 Codec。
    public static final Codec<PathNode> CODEC = MAP_CODEC.codec();

    public PathNode {
        if (mode != PathMode.BEZIER) {
            // 非贝塞尔模式不使用切线；归一为零，避免“存着但没人读”的歧义数据
            inTangent = Vec3.ZERO;
            outTangent = Vec3.ZERO;
        } else if (smooth) {
            // 平滑打开时两侧互为反向；构造时就成立，不依赖后续写入维持
            outTangent = inTangent.negate();
        }
    }

    /// 新建线性节点：无切线，自动平滑开启。
    ///
    /// 自动平滑对 `LINEAR` 没有几何效果（切线未被使用），保留它只是为了
    /// “先摆形状、再切模式”时作者手感一致。
    public static PathNode at(PathNodeId id, Vec3 position) {
        return new PathNode(id, position, Vec3.ZERO, Vec3.ZERO, PathMode.LINEAR, true);
    }

    /// 新建贝塞尔节点：给定入侧切线，出侧按 `smooth` 推导。
    public static PathNode bezier(PathNodeId id, Vec3 position, Vec3 inTangent, boolean smooth) {
        return new PathNode(id, position, inTangent, smooth ? inTangent.negate() : Vec3.ZERO,
                PathMode.BEZIER, smooth);
    }

    /// 本节点是否使用切线（即是否为贝塞尔模式）。
    public boolean usesTangents() {
        return mode == PathMode.BEZIER;
    }

    public PathNode withPosition(Vec3 newPosition) {
        return new PathNode(id, newPosition, inTangent, outTangent, mode, smooth);
    }

    /// 换入侧切线。`smooth` 为真时出侧同步镜像——这是**显式**行为，不是副作用。
    public PathNode withInTangent(Vec3 tangent) {
        return new PathNode(id, position, tangent, smooth ? tangent.negate() : outTangent, mode, smooth);
    }

    /// 换出侧切线。`smooth` 为真时入侧同步镜像。
    public PathNode withOutTangent(Vec3 tangent) {
        return new PathNode(id, position, smooth ? tangent.negate() : inTangent, tangent, mode, smooth);
    }

    /// 换段插值方式。切到非贝塞尔模式会把切线归一为零（见类型说明）。
    public PathNode withMode(PathMode newMode) {
        return new PathNode(id, position, inTangent, outTangent, newMode, smooth);
    }

    /// 换自动平滑开关。
    public PathNode withSmooth(boolean newSmooth) {
        return new PathNode(id, position, inTangent, outTangent, mode, newSmooth);
    }

    /// 换身份，其余不变。只在**复制节点**时由 `IdGenerator` 配套使用。
    public PathNode withId(PathNodeId newId) {
        return new PathNode(newId, position, inTangent, outTangent, mode, smooth);
    }
}