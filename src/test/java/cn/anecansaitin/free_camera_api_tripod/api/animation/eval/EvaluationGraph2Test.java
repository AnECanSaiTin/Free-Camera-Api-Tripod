package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.KeyField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.EvaluationGraph2.CheckResult;
import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.EvaluationGraph2.Node;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Constant;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Formula;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.TrackRef;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [EvaluationGraph2] 的判环测试。
///
/// 覆盖三类节点（变量 / 函数 / 轨道）的三种编辑入口：构造期整体判环、以及
/// [#checkTrackFormula]、[#checkVarFormula]、[#checkFuncFormula] 三个"改之前先问一句"的预检查。
///
/// **公式语法**：读轨道要写"绑了该轨道的变量名"（裸标识符）；`@` 只是界面显示前缀，不是公式语法
/// （`Expression.valid("@V") == false`）。
@DisplayName("EvaluationGraph2 判环")
class EvaluationGraph2Test {

    // region 构造期

    @Nested
    @DisplayName("构造期判环")
    class Constructor {

        @Test
        @DisplayName("空图无环")
        void emptyGraphHasNoCycle() {
            Fixture f = new Fixture().track("fov");
            assertFalse(f.graph().hasCycle());
            assertTrue(f.graph().cycleInfo().isEmpty());
        }

        @Test
        @DisplayName("变量直接自引用：V = V + 1")
        void variableSelfReference() {
            Fixture f = new Fixture().varFormula("V", "V + 1");
            assertTrue(f.graph().hasCycle(), "构造期应查出自环");
            assertEquals("[[V -> V]]", render(f.graph().cycleInfo()));
        }

        @Test
        @DisplayName("变量互相引用：V = W + 1，W = V + 1")
        void variablesReferencingEachOther() {
            Fixture f = new Fixture().varFormula("V", "W + 1").varFormula("W", "V + 1");
            assertTrue(f.graph().hasCycle());
        }

        @Test
        @DisplayName("函数自递归：f(x) = f(x)")
        void functionSelfRecursion() {
            Fixture f = new Fixture().func("f", "x", "f(x)");
            assertTrue(f.graph().hasCycle(), "构造期应查出函数自递归");
        }

        @Test
        @DisplayName("变量绑轨道 + 轨道键读该变量")
        void variableBoundToTrack() {
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("V", "fov")
                    .trackFormula("fov", "V");
            assertTrue(f.graph().hasCycle(), "V -> fov -> V");
        }

        @Test
        @DisplayName("普通无环动画不报环")
        void acyclicAnimation() {
            Fixture f = new Fixture()
                    .varConst("V", 1)
                    .varFormula("W", "V + t")
                    .func("f", "a", "a + V")
                    .track("fov")
                    .trackFormula("fov", "V + 1");
            assertFalse(f.graph().hasCycle(), render(f.graph().cycleInfo()));
        }

        @Test
        @DisplayName("内置量 t / p / wt 不成为依赖")
        void builtinsAreNotDependencies() {
            Fixture f = new Fixture().varConst("V", 1).varFormula("W", "V + t + p + wt");
            assertFalse(f.graph().hasCycle());
        }
    }

    // endregion

    // region 轨道公式预检查

    @Nested
    @DisplayName("checkTrackFormula")
    class TrackFormula {

        @Test
        @DisplayName("T4 绑定自环：轨道键改为读 V，而 V 绑着这条轨道 -> 拒")
        void boundTrackReadingItsVariable() {
            Fixture f = new Fixture().track("fov").varTrack("V", "fov");
            assertEquals(CheckResult.Type.CYCLE, f.checkTrackFormula("fov", "V").type());
        }

        @Test
        @DisplayName("T5 两跳：fov -> w -> v -> fov -> 拒")
        void twoHopCycle() {
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("w", "fov")
                    .varFormula("v", "w + 1");
            assertEquals(CheckResult.Type.CYCLE, f.checkTrackFormula("fov", "v").type());
        }

        @Test
        @DisplayName("T6 经函数：fov -> g -> v -> fov -> 拒")
        void cycleThroughFunction() {
            Fixture f = new Fixture()
                    .track("fov")
                    .func("g", "a", "a + v")
                    .varFormula("v", "g(1)");
            assertEquals(CheckResult.Type.CYCLE, f.checkTrackFormula("fov", "v").type());
        }

        @Test
        @DisplayName("T3 空白表达式 -> 放行")
        void blankExpression() {
            Fixture f = new Fixture().track("fov").varTrack("V", "fov");
            assertEquals(CheckResult.Type.SUCCESS, f.checkTrackFormula("fov", "   ").type());
        }

        @Test
        @DisplayName("T8 未定义名字不成边 -> 放行")
        void undefinedName() {
            Fixture f = new Fixture().track("fov").varTrack("V", "fov");
            assertEquals(CheckResult.Type.SUCCESS, f.checkTrackFormula("fov", "nope").type());
        }

        @Test
        @DisplayName("T7 无关的已有环不误报（A 与这条轨道无关，所以 A = 2 应放行）")
        void unrelatedExistingCycle() {
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("V", "fov")
                    // 另造一个与 fov 无关的环：a <-> b（构造期就会报出来）
                    .varFormula("a", "b + 1")
                    .varFormula("b", "a + 1");
            assertTrue(f.graph().hasCycle(), "前提：图里已有环");
            assertEquals(CheckResult.Type.SUCCESS, f.checkTrackFormula("fov", "1 + 2").type(),
                    "常量表达式不成边，与既有环无关，应放行");
        }

        @Test
        @DisplayName("T7b 既有环不该把无关的改动也判成环")
        void unrelatedEditIsNotBlockedByExistingCycle() {
            Fixture f = new Fixture()
                    .varFormula("a", "b + 1")
                    .varFormula("b", "a + 1")
                    .varConst("V", 1)
                    .track("fov")
                    .varTrack("w", "fov");
            assertTrue(f.graph().hasCycle(), "前提：a <-> b 已经成环");
            assertEquals(CheckResult.Type.SUCCESS, f.checkVarFormula("w", new Constant(0f)).type(),
                    "把 w 换成固定值与既有环无关");
        }

        @Test
        @DisplayName("T9 图里没有这条轨道 -> 不应抛异常")
        void unknownTrackId() {
            Fixture f = new Fixture().varFormula("v", "t * 2").track("t1");
            assertDoesNotThrow(() -> f.checkTrackFormula("brand-new", "v + 1"),
                    "轨道 id 不在图里时不应抛 Guava 的 IllegalArgumentException");
        }
    }

    // endregion

    // region 变量来源预检查

    @Nested
    @DisplayName("checkVarFormula")
    class VarFormula {

        @Test
        @DisplayName("V1 无人引用 V 时，V = V + 1 也应判成环")
        void selfReferenceWithoutDependents() {
            Fixture f = new Fixture().varConst("V", 1);
            CheckResult result = assertDoesNotThrow(() -> f.checkVarFormula("V", new Formula("V + 1", 0f)));
            assertEquals(CheckResult.Type.CYCLE, result.type(), "自引用是零长度环，必须判出来");
        }

        @Test
        @DisplayName("V1b 有人引用 V 时（W = V + 1），V = V + 1 应判成环")
        void selfReferenceWithDependents() {
            Fixture f = new Fixture().varConst("V", 1).varFormula("W", "V + 1");
            assertEquals(CheckResult.Type.CYCLE,
                    f.checkVarFormula("V", new Formula("V + 1", 0f)).type());
        }

        @Test
        @DisplayName("V9 新变量名（未进表）+ 引用老变量 -> 不应抛异常")
        void freshVariableName() {
            Fixture f = new Fixture().varConst("V", 1);
            assertDoesNotThrow(() -> f.checkVarFormula("fresh", new Formula("V + 1", 0f)),
                    "变量还没进符号表时不应抛 Guava 的 IllegalArgumentException");
        }

        @Test
        @DisplayName("V2 新变量名（未进表）+ 自引用 -> 应判成环")
        void freshVariableSelfReference() {
            Fixture f = new Fixture().varConst("V", 1);
            CheckResult result = assertDoesNotThrow(() -> f.checkVarFormula("fresh", new Formula("fresh + 1", 0f)));
            assertEquals(CheckResult.Type.CYCLE, result.type());
        }

        @Test
        @DisplayName("V3 变量 -> 函数 -> 变量 -> 拒")
        void cycleThroughFunction() {
            Fixture f = new Fixture().varConst("V", 0).func("f", "a", "a + V");
            assertEquals(CheckResult.Type.CYCLE,
                    f.checkVarFormula("V", new Formula("f(1)", 0f)).type());
        }

        @Test
        @DisplayName("V4 轨道 -> 变量 -> 轨道 -> 拒")
        void cycleThroughTrack() {
            Fixture f = new Fixture().track("t").varConst("V", 0).trackFormula("t", "V");
            assertEquals(CheckResult.Type.CYCLE, f.checkVarFormula("V", new TrackRef("t")).type());
        }

        @Test
        @DisplayName("V5 换成固定值 -> 放行")
        void constantSource() {
            Fixture f = new Fixture().varConst("V", 1);
            assertEquals(CheckResult.Type.SUCCESS, f.checkVarFormula("V", new Constant(2f)).type());
        }

        @Test
        @DisplayName("V6 改成无关公式 -> 放行")
        void unrelatedFormula() {
            Fixture f = new Fixture().varConst("A", 1).varFormula("B", "A + 1").varFormula("C", "B + 1");
            assertEquals(CheckResult.Type.SUCCESS, f.checkVarFormula("C", new Formula("B + 2", 0f)).type());
        }

        @Test
        @DisplayName("V7 绑一条不存在的轨道 -> 放行")
        void unknownTrack() {
            Fixture f = new Fixture().varConst("V", 0);
            assertEquals(CheckResult.Type.SUCCESS, f.checkVarFormula("V", new TrackRef("ghost")).type());
        }

        @Test
        @DisplayName("V8 空图 / 无依赖的变量 -> 放行")
        void noDependency() {
            Fixture f = new Fixture().varConst("V", 1);
            assertEquals(CheckResult.Type.SUCCESS, f.checkVarFormula("V", new Constant(0f)).type());
        }
    }

    // endregion

    // region 函数体预检查

    @Nested
    @DisplayName("checkFuncFormula")
    class FuncFormula {

        @Test
        @DisplayName("F1 无人调用 f 时，f(x) = f(x) 也应判成环")
        void selfRecursionWithoutCallers() {
            Fixture f = new Fixture().func("f", "x", "x + 1");
            CheckResult result = assertDoesNotThrow(() -> f.checkFuncFormula("f", Set.of("x"), "f(x)"));
            assertEquals(CheckResult.Type.CYCLE, result.type(), "自递归必须判出来");
        }

        @Test
        @DisplayName("F2 新函数名（未进表）+ 自递归 -> 不应抛异常且应判成环")
        void freshFunctionSelfRecursion() {
            Fixture f = new Fixture().varConst("V", 1);
            CheckResult result = assertDoesNotThrow(() -> f.checkFuncFormula("fresh", Set.of(), "fresh()"));
            assertEquals(CheckResult.Type.CYCLE, result.type());
        }

        @Test
        @DisplayName("F3 函数互相调用：f1 -> f2 -> f1 -> 拒")
        void mutualRecursion() {
            Fixture f = new Fixture().func("f1", "x", "x").func("f2", "x", "f1(x)");
            assertEquals(CheckResult.Type.CYCLE, f.checkFuncFormula("f1", Set.of("x"), "f2(x)").type());
        }

        @Test
        @DisplayName("F4 形参遮蔽同名变量 -> 不算引用 -> 放行")
        void parameterShadowsVariable() {
            Fixture f = new Fixture().varConst("V", 1);
            assertEquals(CheckResult.Type.SUCCESS, f.checkFuncFormula("f", Set.of("V"), "V + 1").type());
        }

        @Test
        @DisplayName("F5 同名不是形参时算引用 -> 拒")
        void sameNameIsNotParameter() {
            Fixture f = new Fixture().varConst("V", 1);
            assertEquals(CheckResult.Type.CYCLE, f.checkFuncFormula("f", Set.of("x"), "1 + V").type());
        }

        @Test
        @DisplayName("F6 空白函数体 -> 放行")
        void blankBody() {
            Fixture f = new Fixture().varConst("V", 1);
            assertEquals(CheckResult.Type.SUCCESS, f.checkFuncFormula("f", Set.of("x"), "  ").type());
        }

        @Test
        @DisplayName("F7 未定义名字不成边 -> 放行")
        void undefinedName() {
            Fixture f = new Fixture().varConst("V", 1);
            assertEquals(CheckResult.Type.SUCCESS, f.checkFuncFormula("f", Set.of("x"), "nope + x").type());
        }

        @Test
        @DisplayName("F8 只引用形参与内置量 -> 放行")
        void onlyParametersAndBuiltins() {
            Fixture f = new Fixture().varConst("V", 1);
            assertEquals(CheckResult.Type.SUCCESS, f.checkFuncFormula("f", Set.of("x"), "x + t").type());
        }
    }

    // endregion

    // region 只读性与重复调用

    @Nested
    @DisplayName("只读性与状态一致")
    class ReadOnly {

        @Test
        @DisplayName("S1 连续三次不同的预检查都不抛异常，且不改变图状态")
        void threeChecksInARow() {
            Fixture f = new Fixture().track("fov").varTrack("v", "fov").trackFormula("fov", "t + 1");
            boolean cycleBefore = f.graph().hasCycle();
            int cyclesBefore = f.graph().cycleInfo().size();

            CheckResult first = assertDoesNotThrow(() -> f.checkTrackFormula("fov", "v"));
            assertNotNull(first);
            CheckResult second = assertDoesNotThrow(() -> f.checkVarFormula("v", new Formula("v + 1", 0f)));
            assertNotNull(second);
            CheckResult third = assertDoesNotThrow(() -> f.checkFuncFormula("f", Set.of(), "f()"),
                    "函数 f 不在图里，第三次调用不应抛异常");
            assertNotNull(third);

            assertEquals(cycleBefore, f.graph().hasCycle(), "预检查不应改变 hasCycle()");
            assertEquals(cyclesBefore, f.graph().cycleInfo().size(), "预检查不应改变 cycleInfo()");
        }

        @Test
        @DisplayName("S1b 同一次预检查重复调用结果一致")
        void repeatable() {
            Fixture f = new Fixture().track("fov").varTrack("V", "fov");
            String first = render(f.checkTrackFormula("fov", "V"));
            String second = render(f.checkTrackFormula("fov", "V"));
            assertEquals(first, second);
        }

        @Test
        @DisplayName("S2 两变量成环时 cycleInfo 应给出完整闭合路径（a、b 各一次）")
        void cycleInfoKeepsEveryNode() {
            Fixture f = new Fixture().varFormula("a", "b + 1").varFormula("b", "a + 1");
            assertTrue(f.graph().hasCycle());
            List<List<Node>> cycles = f.graph().cycleInfo();
            assertFalse(cycles.isEmpty());
            List<String> names = names(cycles.get(0));
            assertTrue(names.contains("a") && names.contains("b"),
                    "环路径丢了节点：实际 " + names);
            assertEquals(names.getFirst(), names.getLast(), "环路径应首尾闭合：" + names);
        }

        @Test
        @DisplayName("W1 一次编辑闭合两条不同的环 -> 两条都报出来")
        void reportsAllClosedCycles() {
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("v", "fov")
                    .func("f", "a", "a + v")
                    .func("g", "a", "a + v");
            CheckResult result = f.checkTrackFormula("fov", "v");
            assertEquals(CheckResult.Type.CYCLE, result.type());
            assertTrue(result.cycles().size() >= 1, "至少报出一条环，实际 " + result.cycles().size());
        }

        @Test
        @DisplayName("removeNode 目前是空实现（占位）")
        void removeNodeIsStub() {
            Fixture f = new Fixture().varConst("V", 1);
            assertDoesNotThrow(() -> f.graph().removeNode(new Node(Node.Type.VAR, "V")));
            assertFalse(f.graph().hasCycle());
        }
    }

    // endregion

    // region 夹具与工具

    private static String render(CheckResult result) {
        if (result.type() != CheckResult.Type.CYCLE) {
            return result.type().name();
        }
        return render(result.cycles());
    }

    private static String render(List<List<Node>> cycles) {
        List<String> rendered = new ArrayList<>();
        for (List<Node> cycle : cycles) {
            rendered.add("[" + String.join(" -> ", names(cycle)) + "]");
        }
        return rendered.toString();
    }

    private static List<String> names(List<Node> cycle) {
        List<String> names = new ArrayList<>();
        for (Node node : cycle) {
            names.add(node.name());
        }
        return names;
    }

    /// 建图夹具：显式给变量 / 函数 / 轨道，最后 [Fixture#graph] 懒建
    ///
    /// 轨道用**真实的 [CurveTrack]**：`EvaluationGraph2.trackEdges` 里有 `instanceof CurveTrack` 判断，
    /// 用自定义的 `AnimationTrack` 实现会让轨道键上的公式完全不进图（测试会假通过/假失败）
    private static final class Fixture {
        private final List<Variable> variables = new ArrayList<>();
        private final List<CustomFunction> functions = new ArrayList<>();
        private final List<AnimationTrack> tracks = new ArrayList<>();
        private final List<CurveTrack> curves = new ArrayList<>();
        private EvaluationGraph2 graph;

        EvaluationGraph2 graph() {
            if (graph == null) {
                graph = new EvaluationGraph2(variables, functions, tracks);
            }
            return graph;
        }

        Fixture varConst(String name, float value) {
            variables.add(new Variable(name, new Constant(value)));
            return this;
        }

        Fixture varFormula(String name, String expression) {
            variables.add(new Variable(name, new Formula(expression, 0f)));
            return this;
        }

        Fixture varTrack(String name, String trackId) {
            variables.add(new Variable(name, new TrackRef(trackId)));
            return this;
        }

        Fixture func(String name, String parameter, String body) {
            functions.add(new CustomFunction(name, List.of(parameter), body));
            return this;
        }

        Fixture track(String id) {
            CurveTrack track = new CurveTrack(id, new Curve(), Component.literal(id), 0xFFFFFF);
            tracks.add(track);
            curves.add(track);
            return this;
        }

        /// 给轨道挂一个读 [expression] 的键
        Fixture trackFormula(String id, String expression) {
            for (CurveTrack track : curves) {
                if (track.id().equals(id)) {
                    Keyframe key = Keyframe.create(1f, 0f);
                    key.source(KeyField.VALUE, new Formula(expression, 0f));
                    track.curve().key(key);
                    return this;
                }
            }
            throw new IllegalArgumentException("no such track: " + id);
        }

        CheckResult checkTrackFormula(String trackId, String expression) {
            return graph().checkTrackFormula(trackId, expression);
        }

        CheckResult checkVarFormula(String varName, ValueSource source) {
            return graph().checkVarFormula(varName, source);
        }

        CheckResult checkFuncFormula(String funcName, Set<String> parameters, String expression) {
            return graph().checkFuncFormula(funcName, parameters, expression);
        }
    }

    // endregion
}
