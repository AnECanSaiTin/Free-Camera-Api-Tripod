package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.PathNodeId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 路径：有序的节点集合，位置通道在路径模式下按它取点（主设计 §3.1、§3.3）。
///
/// ## 纯数据
///
/// **本类型不含几何算法**。弧长表、参数化查询、切线求解都在 `core` 的路径实现里，
/// 与曲线插值同理（不变量 I6：几何、渲染与播放取点读的是同一份坐标）。
/// 这样 `Path` 保持为纯数据，序列化与相等性不受算法版本影响，
/// 惰性缓存也不会写进数据对象——旧实现把索引缓存与总长缓存在 `Path` 实例字段上，
/// 使“文档对象”同时成了“求值状态持有者”。
///
/// ## 节点顺序与身份
///
/// 节点顺序由集合顺序表达，**不由 ID 表达**：插入、删除、重排都保留其余节点的 ID。
/// 旧实现用节点下标做选中身份，插入或删除会让选中漂移、撤销后指向错节点；
/// 本契约要求用 [PathNodeId] 引用节点。
///
/// 节点 ID 在**本路径内**唯一（主设计 §3.1：各 ID 类型在各自文档作用域内唯一）。
///
/// ## 空路径合法
///
/// 空路径是合法数据（`MotionMode.PATH` 但一个节点都没有），求值时报“没有节点可用”，
/// 而不是让 `path` 可空。把“空”和“缺失”合成两种不同的空状态只会让诊断含糊。
///
/// @param name  用户可见名称
/// @param nodes 有序节点集合；已防御性复制
@NullMarked
public record Path(String name, List<PathNode> nodes) {

    /// 空路径：没有名称、没有节点。
    public static final Path EMPTY = new Path("", List.of());

    public Path {
        // 防御性复制：不保留调用方容器（主设计 §3.2）
        nodes = List.copyOf(nodes);
    }

    /// 单节点路径。
    public static Path of(String name, PathNode node) {
        return new Path(name, List.of(node));
    }

    /// 无节点路径。
    public static Path named(String name) {
        return new Path(name, List.of());
    }

    public int size() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /// 第 index 个节点；越界返回空。**仅用于当前快照的展示**：
    /// 跨版本编辑必须用 [byId]（主设计 §5.3：界面索引不得跨版本执行编辑）。
    public @Nullable PathNode nodeAt(int index) {
        return index >= 0 && index < nodes.size() ? nodes.get(index) : null;
    }

    /// 按稳定 ID 找节点；不存在返回空。
    public @Nullable PathNode byId(PathNodeId id) {
        for (PathNode node : nodes) {
            if (node.id().equals(id)) {
                return node;
            }
        }

        return null;
    }

    /// 节点 ID 在当前快照中的下标；不存在返回 -1。
    ///
    /// 供界面把选中项映射到当前列表行；结果**不得**跨版本使用。
    public int indexOf(PathNodeId id) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(id)) {
                return i;
            }
        }

        return -1;
    }

    public Path withName(String newName) {
        return new Path(newName, nodes);
    }

    /// 换节点集合。顺序即几何顺序；ID 唯一性由 L2 校验负责。
    public Path withNodes(List<PathNode> newNodes) {
        return new Path(name, newNodes);
    }
}
