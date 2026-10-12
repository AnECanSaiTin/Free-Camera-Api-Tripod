package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.KeyField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.*;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// 求值依赖图（编辑器用的可变版）：变量 / 函数 / 轨道三类节点之间"谁引用谁"的关系，用来在**写入期**拦住环。
///
/// 节点是 [Node.Var] / [Node.Func] / [Node.Track]，边 `u → v` 表示 **u 引用 v**（u 依赖 v）。
/// 图的职责只有两件：**记引用**、**判环**。它**不持有变量 / 函数 / 轨道的数据对象**，
/// 所以"把这条引用改成固定值 0"这类改写只能由编辑器做——图这边只把"谁引用了它"交回去。
///
/// ## 配套编辑器要求
///
/// 1.删除任意节点时先检查是否被引用。如有，则无法直接删除。可考虑加个"断开引用"的操作，将对应引用改为固定值0。
///   或者沿着引用链全部删除。该条确保了在加入新节点时，不需要去判断是否成环。
/// 2.添加、修改节点前需要先检查是否成环。
///
/// 对应的入口：
///
/// | 要求 | 方法 |
/// | --- | --- |
/// | 1 先查引用 | [#referrers]（直接） / [#dependents]（沿引用链） |
/// | 1 不能直接删 | [#removeNode]（被引用时返回 false） |
/// | 1 断开引用 | [#breakReferences]（断掉入边，把引用者交回编辑器改成固定值 0） |
/// | 1 沿引用链全删 | [#removeNodeCascade] |
/// | 2 增（不判环） | [#addNode] |
/// | 2 改（先判环，通过才改边） | [#applyTrackFormula] / [#applyVarFormula] / [#applyFuncFormula] |
/// | 试算（无副作用） | [#checkTrackFormula] / [#checkVarFormula] / [#checkFuncFormula] |
///
/// ## 轨道为什么按"引用计数"记账
///
/// 一条轨道上可以挂多条公式（每个关键帧的每个槽位一条），所以 **TRACK 节点的出边是整条轨道所有公式引用的并集**，
/// 而编辑器的改动粒度是"某一个槽位的公式"。[#applyTrackFormula] 因此不做整体替换，而是收下这个槽位的
/// **旧、新两条表达式**：旧公式的引用各退一格、新公式的引用各进一格，计数归零才删边（见 [#trackRefCounts]）。
/// 这样编辑器不必把整条轨道的公式收齐；换成"整体替换"的话，改一个槽位就会把别的槽位的边一起丢掉——
/// 边少了不会报错，只会让之后的判环漏判，正是这套设计最不该出的错。
///
/// 变量与函数没有这一层：一个变量只有一个取值来源、一个函数只有一个函数体，所以那两条是整体替换。
///
/// ## 边界（有意不做的事）
///
/// - **公式里写了当时还不存在的名字不成边**（与建图口径一致：只有真实存在的节点才配成为边的另一头）。
///   之后新建同名节点**不会**把那些引用补成边，所以"新建一个名字已被别的公式提到的节点"要由编辑器拦住。
/// - **改名不在这里**：改名会让节点身份变化，而图分不清一条入边来自 `TrackRef` 绑定还是公式文本
///   （轨道改名要把绑定改指向、变量 / 函数改名要让旧公式里的名字落空），所以改名走编辑器"数据改完 + 换一张图"。
/// - 图里本来就有环时（不该出现，读档兜底才可能有）：判环只拦**绕得回被改节点**的那些环，
///   与既有环无关的改动照旧放行。
@NullMarked
public class EvaluationGraph2 {
    private final MutableGraph<Node> graph;
    private final List<List<Node>> cycleInfo = new ArrayList<>();
    /// 每条轨道上「每个被引用到的节点」被几条公式提到过；TRACK 节点的出边由它现算。
    ///
    /// 只有轨道是多来源节点，所以只有轨道需要这张表。它由构造期按轨道上现成的公式 seed，
    /// 之后由 [#applyTrackFormula] 按槽位增减；[#breakReferences] 会把被断开的引用从这里抹掉，
    /// 免得之后改这条轨道上别的槽位时又把它算回一条边
    private final Map<Node, Map<Node, Integer>> trackRefCounts = new HashMap<>();

    /// 图上的节点：**类别 + 名字**。
    ///
    /// 三类名字在语言层面本来就是三个命名空间（`a`、`a(x)`、轨道 id 各走各的），
    /// 所以同名不同类是两个节点——合成一个会让函数那边的依赖被吞掉，或把"变量绑一条与自己同名的轨道"判成假环。
    ///
    /// 轨道节点**不带关键帧索引**：键的索引会随插入 / 删除 / 拖动整体平移（`Curve.key(Keyframe)` 是二分插入，
    /// `Curve.removeKey` / `moveKey` 都会挪后面的键），拿它做身份等于每动一下键就要重编号一批节点与边，
    /// 漏一次就是图上少一条边、判环漏判；而且一个关键帧有五个槽位可以同时挂公式，
    /// 索引粒度也消不掉"一条轨道多条公式"的并集问题（那条由 [#trackRefCounts] 解决）
    public sealed interface Node {
        /// 变量：公式里按名字引用
        record Var(String name) implements Node {
        }

        /// 自定义函数：公式里按 `f(...)` 调用
        record Func(String name) implements Node {
        }

        /// 曲线轨道：被变量绑定（[TrackRef]），或作为关键帧公式的宿主；名字是轨道 id
        record Track(String id) implements Node {
        }
    }

    public record CheckResult(Type type, List<List<Node>> cycles) {
        public enum Type {
            SUCCESS, CYCLE, ERROR
        }

        public static final CheckResult SUCCESS = new CheckResult(Type.SUCCESS, Collections.emptyList());

        public static final CheckResult ERROR = new CheckResult(Type.ERROR, Collections.emptyList());

        public static CheckResult cycle(List<List<Node>> cycles) {
            return new CheckResult(Type.CYCLE, cycles);
        }
    }

    public EvaluationGraph2(List<Variable> variables, List<CustomFunction> functions, List<AnimationTrack> tracks) {
        // region 创建图
        graph = GraphBuilder
                .directed()
                .allowsSelfLoops(true)// 允许自环，由dfs来检测
                .expectedNodeCount(variables.size() + functions.size() + tracks.size())
                .build();
        // endregion
        // region 添加可作为目标的节点
        for (Variable variable : variables) {
            graph.addNode(new Node.Var(variable.name()));
        }

        for (CustomFunction function : functions) {
            graph.addNode(new Node.Func(function.name()));
        }

        for (AnimationTrack track : tracks) {
            graph.addNode(new Node.Track(track.id()));
        }
        // 存储为用于过滤掉未定义引用
        Set<Node> defined = Set.copyOf(graph.nodes());
        // endregion
        // region 依据依赖关系添加边
        for (AnimationTrack track : tracks) {
            Node nodeU = new Node.Track(track.id());
            Map<Node, Integer> counts = new LinkedHashMap<>();
            trackRefCounts.put(nodeU, counts);
            trackEdges(track, defined, counts);
            putAllEdges(nodeU, counts.keySet());
        }

        for (Variable variable : variables) {
            Node nodeU = new Node.Var(variable.name());
            putAllEdges(nodeU, variableRefs(variable, defined));
        }

        for (CustomFunction function : functions) {
            Node nodeU = new Node.Func(function.name());
            putAllEdges(nodeU, functionRefs(function, defined));
        }
        // endregion
        // 检测循环引用
        refreshCycles();
    }

    public List<List<Node>> cycleInfo() {
        return cycleInfo;
    }

    public boolean hasCycle() {
        return !cycleInfo.isEmpty();
    }

    // region 增

    /// 新增一个节点。
    ///
    /// **不判环**：新节点一条边也没有——没人引用得到它，它自己也不引用谁，闭合不了任何环。
    /// 这也正是"被引用的节点不能直接删"那条要求换来的便利（见类文档）。
    ///
    /// 图里的环信息不用重算：孤立节点进不了任何环
    ///
    /// @return 之前不在图里才返回 true
    public boolean addNode(Node node) {
        return graph.addNode(node);
    }

    // endregion

    // region 查引用（删除之前先问一句）

    /// 节点在不在图里。
    ///
    /// 编辑器用它判断"要不要先 [#addNode]"、以及"是不是已经删干净了"
    public boolean contains(Node node) {
        return graph.nodes().contains(node);
    }

    /// 直接引用 [node] 的节点（"谁会因为删掉它而失去引用"）。
    ///
    /// 不在图里、或没人引用时返回空集——**不抛 Guava 的 IllegalArgumentException**
    public Set<Node> referrers(Node node) {
        if (!graph.nodes().contains(node)) {
            return Set.of();
        }

        return Set.copyOf(graph.predecessors(node));
    }

    /// 所有（直接或间接）引用 [node] 的节点——沿引用链反向走到的全部，**不含它自己**。
    ///
    /// [#removeNodeCascade] 会连带删掉的就是这些；编辑器拿它做"整条删除会删掉哪些"的确认框。
    /// 图里本来就有环时也能正常返回（访问过的节点不再重复入队）
    public Set<Node> dependents(Node node) {
        if (!graph.nodes().contains(node)) {
            return Set.of();
        }

        // 已走过的节点（含起点），与要交回去的引用者分开：自己绕回自己时不算"引用者"
        Set<Node> visited = new HashSet<>();
        Set<Node> found = new LinkedHashSet<>();
        Deque<Node> pending = new ArrayDeque<>();
        visited.add(node);
        pending.add(node);

        while (!pending.isEmpty()) {
            Node current = pending.poll();

            for (Node referrer : graph.predecessors(current)) {
                if (visited.add(referrer)) {
                    pending.add(referrer);
                }

                if (!referrer.equals(node)) {
                    found.add(referrer);
                }
            }
        }

        return found;
    }

    // endregion

    // region 删

    /// 删除节点。**被引用时不删**（返回 false），没有入边时连同它的出边一起从图里去掉。
    ///
    /// 出边可以随便删——那些是"它引用别人"，删掉它不会让谁的引用落空；入边不行，正是这里要拦的。
    /// 要删被引用的节点，先 [#breakReferences] 或走 [#removeNodeCascade]。
    ///
    /// 自引用（`V = V + 1` 这种环只可能来自构造期——[#applyVarFormula] 一类不会放它进来）也算被引用，
    /// 同样要先断引用或整条删
    ///
    /// 不在图里时也返回 false（不抛异常）
    public boolean removeNode(Node node) {
        if (!graph.nodes().contains(node) || !graph.predecessors(node).isEmpty()) {
            return false;
        }

        graph.removeNode(node);
        // 轨道被删掉时它的引用计数表一并清掉，免得留在表里等下一个同名轨道
        trackRefCounts.remove(node);
        refreshCycles();
        return true;
    }

    /// 断开所有指向 [node] 的引用：入边全部去掉，[node] 自己留在图里。
    ///
    /// 这一步只动图：**那些引用者在数据里的来源要由编辑器改成固定值 0**（图不持有数据对象）。
    /// 是轨道时，编辑器还应按槽位调 [#applyTrackFormula]（旧公式 → `null`）把计数一起校准——
    /// 断引用时只抹掉了这个引用本身，同一条公式里引用到的别的名字还留在计数里，
    /// 那会让判环偏保守（只会多报环，不会漏报）
    ///
    /// @return 被断开的引用者；不在图里时返回空集
    public Set<Node> breakReferences(Node node) {
        if (!graph.nodes().contains(node)) {
            return Set.of();
        }

        // 先快照再动图：Guava 的迭代器在结构变动后就失效了
        Set<Node> referrers = Set.copyOf(graph.predecessors(node));

        for (Node referrer : referrers) {
            graph.removeEdge(referrer, node);
        }

        for (Map<Node, Integer> counts : trackRefCounts.values()) {
            counts.remove(node);
        }

        refreshCycles();
        return referrers;
    }

    /// 删除节点以及所有引用者（"沿着引用链全部删除"）。
    ///
    /// @return 实际删掉的节点，**含 [node] 自己**；不在图里时返回空集。
    ///         编辑器按这份名单把对应的变量 / 函数 / 轨道一并删掉
    public Set<Node> removeNodeCascade(Node node) {
        if (!graph.nodes().contains(node)) {
            return Set.of();
        }

        // 先收集再删：收集要顺着引用链走，删到一半就没得走了
        Set<Node> removed = new LinkedHashSet<>();
        removed.add(node);
        removed.addAll(dependents(node));

        for (Node target : removed) {
            graph.removeNode(target);
            trackRefCounts.remove(target);
        }

        refreshCycles();
        return removed;
    }

    // endregion

    // region 改（先判环，通过才改边）

    /// 改：把某条轨道上**某一个槽位**的公式从 [oldExpression] 换成 [newExpression]。
    /// 给 `null` 或空白表示那一头没有公式（新增槽位就 `old` 给 null，摘掉公式就 `new` 给 null）。
    ///
    /// 一条轨道上可以挂多条公式，TRACK 节点的出边是它们引用的并集，所以这里按"旧公式退一格、新公式进一格"
    /// 记账而不是整体替换（理由见类文档）。编辑器只需交出这个槽位的旧、新两个字符串
    ///
    /// **先判环、通过才改边**：会成环时整条不做，把环交回（[CheckResult.Type#CYCLE]）。
    /// 节点不在图里时会顺手加进来（与 [#checkTrackFormula] 的口径一致）
    public CheckResult applyTrackFormula(String trackId, @Nullable String oldExpression, @Nullable String newExpression) {
        Node node = new Node.Track(trackId);
        Set<Node> defined = definedWith(node);
        // 试算：先在副本上退旧、进新，成环就原样返回，一个字节都不改
        Map<Node, Integer> trial = new LinkedHashMap<>(trackRefCounts.getOrDefault(node, Map.of()));

        if (oldExpression != null) {
            uncountRefs(trial, expressionRefs(oldExpression, Set.of(), defined));
        }

        if (newExpression != null) {
            countRefs(trial, expressionRefs(newExpression, Set.of(), defined));
        }

        List<List<Node>> result = walker(node, trial.keySet());

        if (!result.isEmpty()) {
            return CheckResult.cycle(result);
        }

        trackRefCounts.put(node, trial);
        commitRefs(node, trial.keySet());
        refreshCycles();
        return CheckResult.SUCCESS;
    }

    /// 改：把某个变量的取值来源整体换掉（[ValueSource] 只有一条，所以这里是整体替换）。
    ///
    /// **先判环、通过才改边**：会成环时整条不做。节点不在图里时会顺手加进来（新建变量 → 直接挂来源，一步到位）
    public CheckResult applyVarFormula(String varName, ValueSource source) {
        Node node = new Node.Var(varName);
        Set<Node> refs = variableRefs(source, definedWith(node));
        List<List<Node>> result = walker(node, refs);

        if (!result.isEmpty()) {
            return CheckResult.cycle(result);
        }

        commitRefs(node, refs);
        refreshCycles();
        return CheckResult.SUCCESS;
    }

    /// 改：把某个函数的函数体与形参整体换掉（函数体只有一条，所以这里是整体替换）。
    ///
    /// [parameters] 是这次编辑的形参表：形参名在函数体里不算引用，所以它是参数的一部分，不能省。
    ///
    /// **先判环、通过才改边**：会成环时整条不做。节点不在图里时会顺手加进来
    public CheckResult applyFuncFormula(String funcName, Set<String> parameters, String expression) {
        Node node = new Node.Func(funcName);
        Set<Node> refs = expressionRefs(expression, parameters, definedWith(node));
        List<List<Node>> result = walker(node, refs);

        if (!result.isEmpty()) {
            return CheckResult.cycle(result);
        }

        commitRefs(node, refs);
        refreshCycles();
        return CheckResult.SUCCESS;
    }

    /// 把 [node] 的出边整体换成 [refs]：先清掉旧的，再连新的。节点不在图里就顺手加进来。
    ///
    /// [refs] 里的节点一定都在图里（见 [#expressionRefs] 与 [#variableRefs] 的 `defined` 过滤），
    /// 所以不会连出指向图外的边
    private void commitRefs(Node node, Set<Node> refs) {
        graph.addNode(node);

        for (Node successor : Set.copyOf(graph.successors(node))) {
            graph.removeEdge(node, successor);
        }

        putAllEdges(node, refs);
    }

    // endregion

    // region 预检查（无副作用）

    /// 预检查轨道表达式是否成环。
    ///
    /// 无副作用。
    ///
    /// 判的是"把这条轨道整组公式换成这一条"：轨道上没有别的公式时与"只加这一个槽位"是同一件事。
    /// 轨道上已经挂着别的公式时要按并集判，用 [#applyTrackFormula]（它先判环再改边）
    ///
    /// @param trackId    轨道 ID
    /// @param expression 表达式
    /// @return 所有闭合的环
    public CheckResult checkTrackFormula(String trackId, String expression) {
        if (expression.isBlank()) {
            return CheckResult.SUCCESS;
        }

        Node node = new Node.Track(trackId);
        Set<Node> refs = expressionRefs(expression, Set.of(), definedWith(node));
        List<List<Node>> result = walker(node, refs);
        return result.isEmpty() ? CheckResult.SUCCESS : CheckResult.cycle(result);
    }

    /// 预检查变量表达式是否成环。
    ///
    /// 无副作用。
    ///
    /// @param varName 变量名
    /// @param source  值来源
    /// @return 所有闭合的环
    public CheckResult checkVarFormula(String varName, ValueSource source) {
        Node node = new Node.Var(varName);
        Set<Node> refs = variableRefs(source, definedWith(node));
        List<List<Node>> result = walker(node, refs);
        return result.isEmpty() ? CheckResult.SUCCESS : CheckResult.cycle(result);
    }

    /// 预检查函数表达式是否成环。
    ///
    /// 无副作用。
    ///
    /// @param funcName   函数名
    /// @param parameters 函数形参
    /// @param expression 方法体
    /// @return 所有闭合的环
    public CheckResult checkFuncFormula(String funcName, Set<String> parameters, String expression) {
        if (expression.isBlank()) {
            return CheckResult.SUCCESS;
        }

        Node node = new Node.Func(funcName);
        Set<Node> refs = expressionRefs(expression, parameters, definedWith(node));
        List<List<Node>> result = walker(node, refs);
        return result.isEmpty() ? CheckResult.SUCCESS : CheckResult.cycle(result);
    }

    // endregion

    // region 建图与判环的内部实现

    private boolean hasNoPredecessors(Node node) {
        if (!graph.nodes().contains(node)) {
            return true;
        }

        return graph.predecessors(node).isEmpty();
    }

    /// 反向dfs查找依赖环。
    ///
    /// 该方法假设当前图中无环，因此只需将node的所有上游与refs集合比较，即可判断是否成环。
    ///
    /// 传进来的 [refs] 是**这次改动之后节点的全部出边另一头**：一条经过 [node] 的新环，
    /// 一定有一条边是**走进** [node] 的（`候选 → … → node`），也一定有一条边是从 [node] 走出去的，
    /// 那条边的另一头就在 [refs] 里，所以从 [node] 反向走一定碰得到它；碰到时栈里存的就是 `候选 → … → node` 那一段。
    /// 因此**不需要把 [refs] 真的加进图**：加不加都不影响"谁能走到 node"，
    /// 而 [node] 自己的出边也永远不会出现在一条以 [node] 结尾的路径上——这也让它天然是只读的
    ///
    /// 一条环报一条，**报全**：[visited] 只增不减，每个能走回 [node] 的节点只入栈一次，
    /// 所以同一个环不会从不同路径重复报出来；[refs] 里每个能走回 [node] 的节点各报一条
    ///
    /// 无出边就一定无环；命中 [refs] 后不再往下走，免得同一个环被报成"更长的绕法"
    ///
    /// 报出来的格式与 [#dsfResolve] 一致：有序的闭合路径，第一个节点与最后一个节点相同
    ///
    /// @param node 被改动的节点（轨道 / 变量 / 函数）
    /// @param refs 这次改动之后它引用到的节点
    /// @return 所有闭合的环；空表 = 放行
    private List<List<Node>> walker(Node node, Set<Node> refs) {
        // 无后继，不会成环
        if (refs.isEmpty()) {
            return Collections.emptyList();
        }

        // 后继存在自己，自引用
        if (refs.contains(node)) {
            return List.of(List.of(node, node));
        }

        // 节点没有前驱，不会成环
        if (hasNoPredecessors(node)) {
            return Collections.emptyList();
        }

        ArrayList<List<Node>> result = new ArrayList<>();
        // 已被访问过的节点
        HashSet<Node> visited = new HashSet<>();
        // 当前访问路径上的节点，栈顶 = 当前节点
        ArrayDeque<Node> pathStack = new ArrayDeque<>();
        // 栈帧
        Deque<Frame> frames = new ArrayDeque<>();
        visited.add(node);
        pathStack.push(node);
        frames.push(new Frame(node, graph.predecessors(node).iterator()));

        while (!frames.isEmpty()) {
            Frame frame = frames.peek();

            if (!frame.remaining.hasNext()) {
                frames.pop();
                pathStack.pop();
                continue;
            }

            Node next = frame.remaining.next();

            // 能走回 node：next → … → node 这一圈闭合
            if (refs.contains(next)) {
                ArrayList<Node> cycle = new ArrayList<>();
                cycle.add(next);
                cycle.addAll(pathStack);
                cycle.add(next);
                result.add(cycle);
                continue;
            }

            if (visited.add(next)) {
                pathStack.push(next);
                frames.push(new Frame(next, graph.predecessors(next).iterator()));
            }
        }

        return result;
    }

    /// 图上的节点，外加输入的节点。
    ///
    /// 用于判断"成环"的辅助方法：变量 / 函数还不存在于表里时（还没建好就先试算），
    /// 它自己也算"存在的节点"，`V = V + 1`、`f(x) = f(x)` 这类自引用才判得出来。
    /// 建图时别的节点对它的引用已经被当成"未定义的名字"滤掉了，这一点与旧的 [EvaluationGraph] 同粒度
    private Set<Node> definedWith(Node node) {
        Set<Node> defined = new HashSet<>(graph.nodes());
        defined.add(node);
        return defined;
    }

    private void putAllEdges(Node nodeU, Set<Node> nodeVs) {
        for (Node nodeV : nodeVs) {
            graph.putEdge(nodeU, nodeV);
        }
    }

    /// 每个改图的方法末尾都要调它。
    ///
    /// [cycleInfo] 是缓存：边一变它就可能过期，而 [Node] 的判环与报环都读它。
    /// 图很小（变量与函数都是几十上百的量级），整体重算即可，不做增量
    private void refreshCycles() {
        cycleInfo.clear();
        dsfResolve(cycleInfo);
    }

    /// 轨道引用到的节点：该轨道所有键、所有槽位上的公式引用的并集，**按公式计次数**。
    ///
    /// 计次数是为了 [#applyTrackFormula] 能精确地"退掉一条公式的引用"：
    /// 两个槽位都写了 `V` 时，换掉其中一个不能让边 `轨道 → V` 消失
    private void trackEdges(AnimationTrack track, Set<Node> defined, Map<Node, Integer> counts) {
        if (!(track instanceof CurveTrack curveTrack)) {
            return;
        }

        for (int i = 0; i < curveTrack.keyCount(); i++) {
            Keyframe key = curveTrack.key(i);

            if (key == null) {
                continue;
            }

            for (KeyField field : KeyField.values) {
                if (key.source(field) instanceof Formula formula) {
                    countRefs(counts, expressionRefs(formula.expression(), Set.of(), defined));
                }
            }
        }
    }

    private Set<Node> variableRefs(Variable variable, Set<Node> defined) {
        return variableRefs(variable.source(), defined);
    }

    /// 变量的取值来源引用到的节点：绑了轨道就是那条轨道，挂公式就是公式里的变量与函数，固定值谁也引用不到
    private Set<Node> variableRefs(ValueSource source, Set<Node> defined) {
        switch (source) {
            case TrackRef trackRef -> {
                Node track = new Node.Track(trackRef.trackId());

                if (defined.contains(track)) {
                    return Set.of(track);
                }

                return Set.of();
            }
            case Formula formula -> {
                return expressionRefs(formula.expression(), Set.of(), defined);
            }
            default -> {
                return Set.of();
            }
        }
    }

    private Set<Node> functionRefs(CustomFunction function, Set<Node> defined) {
        return expressionRefs(function.body(), new HashSet<>(function.parameters()), defined);
    }

    /// 一段公式引用到的节点：变量取裸名字、函数取调用名，两头都只保留**图上真实存在**的节点
    /// （写了个还没定义的名字不算引用，否则会连出一条指向图外的边）。
    /// 内置量根本不会来：[Expression.Names] 把它们分在单独的桶里
    private Set<Node> expressionRefs(String source, Set<String> parameters, Set<Node> defined) {
        Expression.Names names = Expression.names(source, parameters);
        Set<Node> dest = new LinkedHashSet<>();

        for (String variable : names.variables()) {
            Node node = new Node.Var(variable);

            if (defined.contains(node)) {
                dest.add(node);
            }
        }

        for (String function : names.functions()) {
            Node node = new Node.Func(function);

            if (defined.contains(node)) {
                dest.add(node);
            }
        }

        return dest;
    }

    private static void countRefs(Map<Node, Integer> counts, Set<Node> refs) {
        for (Node ref : refs) {
            counts.merge(ref, 1, Integer::sum);
        }
    }

    /// 退掉一条公式的引用：计数减一，归零就把这项删掉（边也跟着消失）
    private static void uncountRefs(Map<Node, Integer> counts, Set<Node> refs) {
        for (Node ref : refs) {
            counts.computeIfPresent(ref, (ignored, count) -> count > 1 ? count - 1 : null);
        }
    }

    /// 深度优先探测循环引用，并输出循环路径。
    ///
    /// @param dest 存储循环路径的列表容器
    private void dsfResolve(List<List<Node>> dest) {
        // 已被访问过的节点
        Set<Node> visited = new HashSet<>();
        // 当前访问路径上的节点
        Set<Node> path = new HashSet<>();
        // 按照访问顺序记录访问路径，用于打印循环引用
        Deque<Node> pathStack = new ArrayDeque<>();
        // 显式栈：每帧 = 一个正在下探的节点 + 它还没走完的后继
        Deque<Frame> frames = new ArrayDeque<>();

        for (Node root : graph.nodes()) {
            if (visited.contains(root)) {
                continue;
            }

            visited.add(root);
            path.add(root);
            pathStack.push(root);
            frames.push(new Frame(root, graph.successors(root).iterator()));

            while (!frames.isEmpty()) {
                Frame frame = frames.peek();

                if (!frame.remaining.hasNext()) {
                    // 这一支走完了
                    frames.removeLast();
                    path.remove(frame.node);
                    pathStack.pop();
                    continue;
                }

                Node next = frame.remaining.next();

                // 栈内存在相同节点，说明成环
                if (path.contains(next)) {
                    ArrayList<Node> cycle = new ArrayList<>();
                    dest.add(cycle);
                    cycle.add(next);

                    for (Node node : pathStack) {
                        if (node.equals(next)) {
                            cycle.add(node);
                            break;
                        }

                        cycle.add(node);
                    }

                    Collections.reverse(cycle);
                    continue;
                }

                if (!visited.contains(next)) {
                    visited.add(next);
                    path.add(next);
                    pathStack.push(next);
                    frames.push(new Frame(next, graph.successors(next).iterator()));
                }
            }
        }
    }

    /// 栈帧
    /// 栈帧：正在下探的节点，以及它**还没走完的那些邻居**。
    ///
    /// 不叫 successors：本结构被两处复用，[#walker] 沿 predecessors（反向）走、
    /// [#dsfResolve] 沿 successors（正向）走，名字必须与方向无关
    private record Frame(Node node, Iterator<Node> remaining) {
    }

    // endregion
}
