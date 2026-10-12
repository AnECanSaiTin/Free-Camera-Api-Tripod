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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [EvaluationGraph2] 的判环与增删改测试。
///
/// 覆盖三类节点（变量 / 函数 / 轨道）的三种编辑入口：构造期整体判环、以及
/// [#checkTrackFormula]、[#checkVarFormula]、[#checkFuncFormula] 三个"改之前先问一句"的预检查。
///
/// 另外覆盖配套编辑器的两组要求（见 [EvaluationGraph2] 的类文档）：
/// 删除前查引用与三种删法（[#referrers] / [#dependents]、[#removeNode] / [#breakReferences] /
/// [#removeNodeCascade]），以及增与改（[#addNode]、[#applyTrackFormula] / [#applyVarFormula] /
/// [#applyFuncFormula]——改之前先判环，成环则整条不做）。
///
/// **公式语法**：读轨道要写"绑了该轨道的变量名"（裸标识符）；`@` 只是界面显示前缀，不是公式语法
/// （`Expression.valid("@V") == false`）。
///
/// **每个用例都断言"环信息打印"**：不只是类型对不对，还要断言按 [#render] 打印出来的内容。
/// 打印做了两步规范化，因此与 DFS 起点、遍历顺序无关：
/// 1. 每条闭合路径去掉末尾重复的收尾节点，再旋转到字典序最小的起点（`a -> b -> a` 与 `b -> a -> b` 同一条）；
/// 2. 多条环按字符串排序。
/// 节点打印成 `类别 名字`（`VAR V` / `FUNC f` / `TRACK fov`）——编辑器要能区分同名不同类的节点。
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
            assertNoCycle("空图", f.graph().cycleInfo());
        }

        @Test
        @DisplayName("变量直接自引用：V = V + 1")
        void variableSelfReference() {
            Fixture f = new Fixture().varFormula("V", "V + 1");
            assertTrue(f.graph().hasCycle(), "构造期应查出自环");
            assertCycle("[[VAR V -> VAR V]]", "变量自引用", f.graph().cycleInfo());
        }

        @Test
        @DisplayName("变量互相引用：V = W + 1，W = V + 1")
        void variablesReferencingEachOther() {
            Fixture f = new Fixture().varFormula("V", "W + 1").varFormula("W", "V + 1");
            assertTrue(f.graph().hasCycle());
            assertCycle("[[VAR V -> VAR W -> VAR V]]", "变量互引", f.graph().cycleInfo());
        }

        @Test
        @DisplayName("函数自递归：f(x) = f(x)")
        void functionSelfRecursion() {
            Fixture f = new Fixture().func("f", "x", "f(x)");
            assertTrue(f.graph().hasCycle(), "构造期应查出函数自递归");
            assertCycle("[[FUNC f -> FUNC f]]", "函数自递归", f.graph().cycleInfo());
        }

        @Test
        @DisplayName("变量绑轨道 + 轨道键读该变量")
        void variableBoundToTrack() {
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("V", "fov")
                    .trackFormula("fov", "V");
            assertTrue(f.graph().hasCycle(), "V -> fov -> V");
            assertCycle("[[TRACK fov -> VAR V -> TRACK fov]]", "变量绑轨道闭环", f.graph().cycleInfo());
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
            assertFalse(f.graph().hasCycle());
            assertNoCycle("无环动画", f.graph().cycleInfo());
        }

        @Test
        @DisplayName("内置量 t / p / wt 不成为依赖")
        void builtinsAreNotDependencies() {
            Fixture f = new Fixture().varConst("V", 1).varFormula("W", "V + t + p + wt");
            assertFalse(f.graph().hasCycle());
            assertNoCycle("只有内置量参与", f.graph().cycleInfo());
        }

        @Test
        @DisplayName("多条环·两条互不相干：a <-> b 与 c <-> d 各报一条")
        void twoIndependentCycles() {
            Fixture f = new Fixture()
                    .varFormula("a", "b + 1")
                    .varFormula("b", "a + 1")
                    .varFormula("c", "d + 1")
                    .varFormula("d", "c + 1");
            assertCycle("[[VAR a -> VAR b -> VAR a], [VAR c -> VAR d -> VAR c]]", "两条独立环",
                    f.graph().cycleInfo());
        }

        @Test
        @DisplayName("多条环·自环与两节点环混在一起：s = s + 1 与 a <-> b")
        void mixedSelfLoopAndPair() {
            Fixture f = new Fixture()
                    .varFormula("s", "s + 1")
                    .varFormula("a", "b + 1")
                    .varFormula("b", "a + 1");
            assertCycle("[[VAR a -> VAR b -> VAR a], [VAR s -> VAR s]]", "自环 + 两节点环",
                    f.graph().cycleInfo());
        }

        @Test
        @DisplayName("多条环·共享同一个节点：a = b + c，b、c 都指回 a（a <-> b 与 a <-> c）")
        void cyclesSharingANode() {
            Fixture f = new Fixture()
                    .varFormula("a", "b + c")
                    .varFormula("b", "a + 1")
                    .varFormula("c", "a + 1");
            assertCycle("[[VAR a -> VAR b -> VAR a], [VAR a -> VAR c -> VAR a]]", "共享节点的两条环",
                    f.graph().cycleInfo());
        }

        @Test
        @DisplayName("多条环·长度不同：a -> b -> c -> a 与 x <-> y")
        void cyclesOfDifferentLength() {
            Fixture f = new Fixture()
                    .varFormula("a", "b + 1")
                    .varFormula("b", "c + 1")
                    .varFormula("c", "a + 1")
                    .varFormula("x", "y + 1")
                    .varFormula("y", "x + 1");
            assertCycle("[[VAR a -> VAR b -> VAR c -> VAR a], [VAR x -> VAR y -> VAR x]]", "长环 + 短环",
                    f.graph().cycleInfo());
        }

        @Test
        @DisplayName("多条环·三条：a <-> b、c <-> d、e <-> f")
        void threeCycles() {
            Fixture f = new Fixture()
                    .varFormula("a", "b + 1")
                    .varFormula("b", "a + 1")
                    .varFormula("c", "d + 1")
                    .varFormula("d", "c + 1")
                    .varFormula("e", "f + 1")
                    .varFormula("f", "e + 1");
            assertCycle("[[VAR a -> VAR b -> VAR a], [VAR c -> VAR d -> VAR c], [VAR e -> VAR f -> VAR e]]",
                    "三条环都报", f.graph().cycleInfo());
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
            assertCycle("[[TRACK fov -> VAR V -> TRACK fov]]", "T4",
                    f.checkTrackFormula("fov", "V"));
        }

        @Test
        @DisplayName("T5 两跳：fov -> v -> w -> fov -> 拒")
        void twoHopCycle() {
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("w", "fov")
                    .varFormula("v", "w + 1");
            assertCycle("[[TRACK fov -> VAR v -> VAR w -> TRACK fov]]", "T5",
                    f.checkTrackFormula("fov", "v"));
        }

        @Test
        @DisplayName("T6 经函数：fov -> v -> g -> w -> fov -> 拒")
        void cycleThroughFunction() {
            // 夹具调整：轨道要"被谁引用"才可能绕回来，这里用绑着 fov 的变量 w 当那一跳
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("w", "fov")
                    .func("g", "a", "a + w")
                    .varFormula("v", "g(1)");
            assertCycle("[[FUNC g -> VAR w -> TRACK fov -> VAR v -> FUNC g]]", "T6",
                    f.checkTrackFormula("fov", "v"));
        }

        @Test
        @DisplayName("T3 空白表达式 -> 放行")
        void blankExpression() {
            Fixture f = new Fixture().track("fov").varTrack("V", "fov");
            assertSuccess("T3", f.checkTrackFormula("fov", "   "));
        }

        @Test
        @DisplayName("T8 未定义名字不成边 -> 放行")
        void undefinedName() {
            Fixture f = new Fixture().track("fov").varTrack("V", "fov");
            assertSuccess("T8", f.checkTrackFormula("fov", "nope"));
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
            assertCycle("[[VAR a -> VAR b -> VAR a]]", "前提里的既有环", f.graph().cycleInfo());
            assertSuccess("T7 常量表达式不成边，与既有环无关",
                    f.checkTrackFormula("fov", "1 + 2"));
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
            assertCycle("[[VAR a -> VAR b -> VAR a]]", "前提里的既有环", f.graph().cycleInfo());
            assertSuccess("T7b 把 w 换成固定值与既有环无关",
                    f.checkVarFormula("w", new Constant(0f)));
        }

        @Test
        @DisplayName("T9 图里没有这条轨道 -> 不应抛异常且放行")
        void unknownTrackId() {
            Fixture f = new Fixture().varFormula("v", "t * 2").track("t1");
            CheckResult result = assertDoesNotThrow(() -> f.checkTrackFormula("brand-new", "v + 1"),
                    "轨道 id 不在图里时不应抛 Guava 的 IllegalArgumentException");
            // 新轨道没有前驱（别的节点对它的引用在建图时已被当成未定义过滤），因此无从绕回来
            assertSuccess("T9", result);
        }

        @Test
        @DisplayName("T10 一次编辑闭合三条环（三个函数各绕回轨道）-> 三条都报")
        void multipleCyclesThroughDifferentFunctions() {
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("v", "fov")
                    .func("f", "a", "a + v")
                    .func("g", "a", "a + v")
                    .func("h", "a", "a + v");
            assertCycle("[[FUNC f -> VAR v -> TRACK fov -> FUNC f],"
                            + " [FUNC g -> VAR v -> TRACK fov -> FUNC g],"
                            + " [FUNC h -> VAR v -> TRACK fov -> FUNC h]]",
                    "T10 三条环", f.checkTrackFormula("fov", "f(1) + g(2) + h(3)"));
        }

        @Test
        @DisplayName("T11 一次编辑闭合两条长度不同的环（一条经三跳、一条经一跳）-> 两条都报")
        void multipleCyclesOfDifferentLength() {
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("w", "fov")
                    .varFormula("x", "w + 1")
                    .varFormula("y", "x + 1")
                    .func("g", "a", "a + w");
            assertCycle("[[FUNC g -> VAR w -> TRACK fov -> FUNC g],"
                            + " [TRACK fov -> VAR y -> VAR x -> VAR w -> TRACK fov]]",
                    "T11 两条长短不同的环", f.checkTrackFormula("fov", "y + g(1)"));
        }

        @Test
        @DisplayName("T12 只闭合一条环时不夹带别的环（编辑只引用一个函数）")
        void singleCycleAmongSeveralCandidates() {
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("v", "fov")
                    .func("f", "a", "a + v")
                    .func("g", "a", "a + v");
            assertCycle("[[FUNC f -> VAR v -> TRACK fov -> FUNC f]]", "T12 只引用 f",
                    f.checkTrackFormula("fov", "f(1)"));
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
            assertCycle("[[VAR V -> VAR V]]", "V1 零长度环", result);
        }

        @Test
        @DisplayName("V1b 有人引用 V 时（W = V + 1），V = V + 1 应判成环")
        void selfReferenceWithDependents() {
            Fixture f = new Fixture().varConst("V", 1).varFormula("W", "V + 1");
            assertCycle("[[VAR V -> VAR V]]", "V1b 零长度环",
                    f.checkVarFormula("V", new Formula("V + 1", 0f)));
        }

        @Test
        @DisplayName("V9 新变量名（未进表）+ 引用老变量 -> 不应抛异常且放行")
        void freshVariableName() {
            Fixture f = new Fixture().varConst("V", 1);
            CheckResult result = assertDoesNotThrow(() -> f.checkVarFormula("fresh", new Formula("V + 1", 0f)),
                    "变量还没进符号表时不应抛 Guava 的 IllegalArgumentException");
            // 新变量没有前驱，所以 V 引用不到它，绕不回来
            assertSuccess("V9", result);
        }

        @Test
        @DisplayName("V2 新变量名（未进表）+ 自引用 -> 应判成环")
        void freshVariableSelfReference() {
            Fixture f = new Fixture().varConst("V", 1);
            CheckResult result = assertDoesNotThrow(() -> f.checkVarFormula("fresh", new Formula("fresh + 1", 0f)));
            assertCycle("[[VAR fresh -> VAR fresh]]", "V2 零长度环", result);
        }

        @Test
        @DisplayName("V3 变量 -> 函数 -> 变量 -> 拒")
        void cycleThroughFunction() {
            Fixture f = new Fixture().varConst("V", 0).func("f", "a", "a + V");
            assertCycle("[[FUNC f -> VAR V -> FUNC f]]", "V3",
                    f.checkVarFormula("V", new Formula("f(1)", 0f)));
        }

        @Test
        @DisplayName("V4 轨道 -> 变量 -> 轨道 -> 拒")
        void cycleThroughTrack() {
            Fixture f = new Fixture().track("t").varConst("V", 0).trackFormula("t", "V");
            assertCycle("[[TRACK t -> VAR V -> TRACK t]]", "V4",
                    f.checkVarFormula("V", new TrackRef("t")));
        }

        @Test
        @DisplayName("V5 换成固定值 -> 放行")
        void constantSource() {
            Fixture f = new Fixture().varConst("V", 1);
            assertSuccess("V5", f.checkVarFormula("V", new Constant(2f)));
        }

        @Test
        @DisplayName("V6 改成无关公式 -> 放行")
        void unrelatedFormula() {
            Fixture f = new Fixture().varConst("A", 1).varFormula("B", "A + 1").varFormula("C", "B + 1");
            assertSuccess("V6", f.checkVarFormula("C", new Formula("B + 2", 0f)));
        }

        @Test
        @DisplayName("V7 绑一条不存在的轨道 -> 放行")
        void unknownTrack() {
            Fixture f = new Fixture().varConst("V", 0);
            assertSuccess("V7", f.checkVarFormula("V", new TrackRef("ghost")));
        }

        @Test
        @DisplayName("V8 空图 / 无依赖的变量 -> 放行")
        void noDependency() {
            Fixture f = new Fixture().varConst("V", 1);
            assertSuccess("V8", f.checkVarFormula("V", new Constant(0f)));
        }

        @Test
        @DisplayName("V10 自环命中会提前返回：同一编辑闭合的兄弟环不报（当前取舍，见注释）")
        void selfReferenceSwallowsSiblingCycle() {
            Fixture f = new Fixture().varConst("V", 1).func("f", "a", "a + V");
            // V = V + f(1) 其实闭合了两条环：V -> V 与 V -> f -> V。
            // walker 一旦发现"自己就在 dest 里"就立刻返回 [[V, V]]，不再沿前驱走下去，
            // 所以这里只会报自环。编辑照样被拒（目的达到了），但"报全"在这条路径上不成立
            assertCycle("[[VAR V -> VAR V]]", "V10 自环保留", 
                    f.checkVarFormula("V", new Formula("V + f(1)", 0f)));
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
            assertCycle("[[FUNC f -> FUNC f]]", "F1 零长度环", result);
        }

        @Test
        @DisplayName("F2 新函数名（未进表）+ 自递归 -> 不应抛异常且应判成环")
        void freshFunctionSelfRecursion() {
            Fixture f = new Fixture().varConst("V", 1);
            CheckResult result = assertDoesNotThrow(() -> f.checkFuncFormula("fresh", Set.of(), "fresh()"));
            assertCycle("[[FUNC fresh -> FUNC fresh]]", "F2 零长度环", result);
        }

        @Test
        @DisplayName("F3 函数互相调用：f1 -> f2 -> f1 -> 拒")
        void mutualRecursion() {
            Fixture f = new Fixture().func("f1", "x", "x").func("f2", "x", "f1(x)");
            assertCycle("[[FUNC f1 -> FUNC f2 -> FUNC f1]]", "F3",
                    f.checkFuncFormula("f1", Set.of("x"), "f2(x)"));
        }

        @Test
        @DisplayName("F4 形参遮蔽同名变量 -> 不算引用 -> 放行")
        void parameterShadowsVariable() {
            Fixture f = new Fixture().varConst("V", 1);
            assertSuccess("F4", f.checkFuncFormula("f", Set.of("V"), "V + 1"));
        }

        @Test
        @DisplayName("F5 同名不是形参时算引用 -> 拒")
        void sameNameIsNotParameter() {
            // 夹具调整：要让"引用 V"真的成环，V 必须指回 f，且 f 得先在表里
            // （f 不存在时，建图阶段 V 对它的引用会被当成未定义名字滤掉，见 definedWith 的说明）
            Fixture f = new Fixture().func("f", "x", "x").varFormula("V", "f(1)");
            assertCycle("[[FUNC f -> VAR V -> FUNC f]]", "F5",
                    f.checkFuncFormula("f", Set.of("x"), "1 + V"));
        }

        @Test
        @DisplayName("F6 空白函数体 -> 放行")
        void blankBody() {
            Fixture f = new Fixture().varConst("V", 1);
            assertSuccess("F6", f.checkFuncFormula("f", Set.of("x"), "  "));
        }

        @Test
        @DisplayName("F7 未定义名字不成边 -> 放行")
        void undefinedName() {
            Fixture f = new Fixture().varConst("V", 1);
            assertSuccess("F7", f.checkFuncFormula("f", Set.of("x"), "nope + x"));
        }

        @Test
        @DisplayName("F8 只引用形参与内置量 -> 放行")
        void onlyParametersAndBuiltins() {
            Fixture f = new Fixture().varConst("V", 1);
            assertSuccess("F8", f.checkFuncFormula("f", Set.of("x"), "x + t"));
        }

        @Test
        @DisplayName("F9 一次编辑闭合两条环（两个函数各自调回 f1）-> 两条都报")
        void multipleCyclesThroughTwoFunctions() {
            Fixture f = new Fixture()
                    .func("f1", "a", "a")
                    .func("f2", "a", "f1(a)")
                    .func("f3", "a", "f1(a)");
            assertCycle("[[FUNC f1 -> FUNC f2 -> FUNC f1], [FUNC f1 -> FUNC f3 -> FUNC f1]]",
                    "F9 两条环", f.checkFuncFormula("f1", Set.of("a"), "f2(a) + f3(a)"));
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

            assertCycle("[[TRACK fov -> VAR v -> TRACK fov]]", "S1 第一次（轨道公式）", first);
            assertCycle("[[VAR v -> VAR v]]", "S1 第二次（变量来源）", second);
            assertCycle("[[FUNC f -> FUNC f]]", "S1 第三次（函数体）", third);

            assertEquals(cycleBefore, f.graph().hasCycle(), "预检查不应改变 hasCycle()");
            assertEquals(cyclesBefore, f.graph().cycleInfo().size(), "预检查不应改变 cycleInfo()");
            assertNoCycle("S1 预检查之后图仍无环", f.graph().cycleInfo());
        }

        @Test
        @DisplayName("S1b 同一次预检查重复调用结果一致")
        void repeatable() {
            Fixture f = new Fixture().track("fov").varTrack("V", "fov");
            CheckResult first = f.checkTrackFormula("fov", "V");
            CheckResult second = f.checkTrackFormula("fov", "V");
            assertCycle("[[TRACK fov -> VAR V -> TRACK fov]]", "S1b 第一次", first);
            assertCycle("[[TRACK fov -> VAR V -> TRACK fov]]", "S1b 第二次", second);
            assertEquals(render(first), render(second));
        }

        @Test
        @DisplayName("S2 两变量成环时 cycleInfo 应给出完整闭合路径（a、b 各一次）")
        void cycleInfoKeepsEveryNode() {
            Fixture f = new Fixture().varFormula("a", "b + 1").varFormula("b", "a + 1");
            assertTrue(f.graph().hasCycle());
            assertCycle("[[VAR a -> VAR b -> VAR a]]", "S2 环路径不能丢节点", f.graph().cycleInfo());
        }

        @Test
        @DisplayName("W1 一次编辑闭合两条不同的环 -> 两条都报出来")
        void reportsAllClosedCycles() {
            // 夹具调整：原夹具里 f、g 都读 v，而 v 直接绑着轨道，改动只闭合一条环；
            // 让这次编辑同时引用 f 与 g，才有两条互不相同的环（各经一个函数绕回轨道）
            Fixture f = new Fixture()
                    .track("fov")
                    .varTrack("v", "fov")
                    .func("f", "a", "a + v")
                    .func("g", "a", "a + v");
            assertCycle("[[FUNC f -> VAR v -> TRACK fov -> FUNC f], [FUNC g -> VAR v -> TRACK fov -> FUNC g]]",
                    "W1 两条环各报一条", f.checkTrackFormula("fov", "f(1) + g(2)"));
        }
    }

    // endregion

    // region 增删改

    @Nested
    @DisplayName("增删改")
    class Mutation {

        // region 增

        @Test
        @DisplayName("A1 新增节点：无边、不成环，重复加返回 false")
        void addNodeIsIsolated() {
            EvaluationGraph2 graph = new Fixture().varConst("V", 1).graph();
            assertTrue(graph.addNode(new Node.Var("fresh")), "新名字应该加得进去");
            assertFalse(graph.addNode(new Node.Var("fresh")), "同一个节点加两次，第二次返回 false");
            assertTrue(graph.contains(new Node.Var("fresh")));
            assertTrue(graph.referrers(new Node.Var("fresh")).isEmpty(), "新节点没人引用");
            assertTrue(graph.dependents(new Node.Var("fresh")).isEmpty());
            assertFalse(graph.hasCycle(), "孤立节点闭合不了任何环");
        }

        // endregion

        // region 查引用

        @Test
        @DisplayName("Q1 referrers 只给直接引用者（变量 / 函数 / 轨道各一）")
        void referrersAreDirect() {
            Fixture f = new Fixture()
                    .varConst("V", 1)
                    .varFormula("W", "V + 1")
                    .func("g", "a", "a + V")
                    .track("fov")
                    .trackFormula("fov", "V");
            EvaluationGraph2 graph = f.graph();
            assertEquals(List.of("FUNC g", "TRACK fov", "VAR W"), sorted(graph.referrers(new Node.Var("V"))));
        }

        @Test
        @DisplayName("Q2 dependents 给整条引用链：间接引用者也算")
        void dependentsAreTransitive() {
            Fixture f = new Fixture()
                    .varFormula("a", "b + 1")
                    .varFormula("b", "c + 1")
                    .varConst("c", 1);
            EvaluationGraph2 graph = f.graph();
            assertEquals(List.of("VAR b"), sorted(graph.referrers(new Node.Var("c"))), "直接引用者只有 b");
            assertEquals(List.of("VAR a", "VAR b"), sorted(graph.dependents(new Node.Var("c"))),
                    "沿引用链能走到 a");
            assertTrue(graph.dependents(new Node.Var("a")).isEmpty(), "没人引用 a");
        }

        @Test
        @DisplayName("Q3 不在图里的节点：查引用给空集，不抛异常")
        void queriesOnUnknownNode() {
            EvaluationGraph2 graph = new Fixture().varConst("V", 1).graph();
            Node ghost = new Node.Var("ghost");
            assertFalse(graph.contains(ghost));
            assertTrue(assertDoesNotThrow(() -> graph.referrers(ghost), "不该抛 Guava 的 IllegalArgumentException").isEmpty());
            assertTrue(graph.dependents(ghost).isEmpty());
        }

        // endregion

        // region 删

        @Test
        @DisplayName("R1 没被引用的节点可以直接删；删完图里没有它了")
        void removeUnreferenced() {
            EvaluationGraph2 graph = new Fixture().varConst("V", 1).varConst("W", 1).graph();
            assertTrue(graph.removeNode(new Node.Var("W")));
            assertFalse(graph.contains(new Node.Var("W")));
            assertFalse(graph.removeNode(new Node.Var("W")), "已经删掉的不再删第二次");
            assertNoCycle("删掉一个孤立节点不影响环信息", graph.cycleInfo());
        }

        @Test
        @DisplayName("R2 被引用时删不掉，且图一点没动")
        void removeReferencedIsRefused() {
            Fixture f = new Fixture().varConst("V", 1).track("fov").trackFormula("fov", "V");
            EvaluationGraph2 graph = f.graph();
            assertFalse(graph.removeNode(new Node.Var("V")), "轨道 fov 引用了 V，不能直接删");
            assertTrue(graph.contains(new Node.Var("V")));
            // 探针：把 V 绑回 fov 会成环，靠的正是 fov -> V 那条边；它还说明图没被改动
            assertCycle("[[TRACK fov -> VAR V -> TRACK fov]]", "删失败之后边还在",
                    graph.checkVarFormula("V", new TrackRef("fov")));
            assertFalse(graph.hasCycle());
        }

        @Test
        @DisplayName("R3 断开引用：入边去掉、引用者交回，之后就能删了")
        void breakReferencesUnblocksRemoval() {
            Fixture f = new Fixture().varConst("V", 1).track("fov").trackFormula("fov", "V");
            EvaluationGraph2 graph = f.graph();
            assertEquals(List.of("TRACK fov"), sorted(graph.breakReferences(new Node.Var("V"))),
                    "交回被断开的引用者，编辑器据此把来源改成固定值 0");
            assertTrue(graph.referrers(new Node.Var("V")).isEmpty(), "断完之后没人引用它");
            assertSuccess("断完引用，那条边没了", graph.checkVarFormula("V", new TrackRef("fov")));
            assertTrue(graph.contains(new Node.Var("V")), "断引用不删节点");
            assertTrue(graph.removeNode(new Node.Var("V")), "断完引用就能删了");
            assertFalse(graph.contains(new Node.Var("V")));
        }

        @Test
        @DisplayName("R4 沿引用链整删：变量与轨道一起被带走")
        void cascadeRemovesWholeChain() {
            Fixture f = new Fixture()
                    .varConst("V", 1)
                    .varFormula("W", "V + 1")
                    .track("fov")
                    .trackFormula("fov", "W");
            EvaluationGraph2 graph = f.graph();
            assertEquals(List.of("TRACK fov", "VAR V", "VAR W"), sorted(graph.removeNodeCascade(new Node.Var("V"))));
            assertFalse(graph.contains(new Node.Var("V")));
            assertFalse(graph.contains(new Node.Var("W")));
            assertFalse(graph.contains(new Node.Track("fov")));
            assertNoCycle("整条删完之后没有环", graph.cycleInfo());
        }

        @Test
        @DisplayName("R5 不在图里的节点：删 / 断 / 级联都给空结果，不抛异常")
        void removalsOnUnknownNode() {
            EvaluationGraph2 graph = new Fixture().varConst("V", 1).graph();
            Node ghost = new Node.Var("ghost");
            assertFalse(assertDoesNotThrow(() -> graph.removeNode(ghost), "不该抛 Guava 的 IllegalArgumentException"));
            assertTrue(graph.breakReferences(ghost).isEmpty());
            assertTrue(graph.removeNodeCascade(ghost).isEmpty());
        }

        @Test
        @DisplayName("R6 图里本来就有自环时也算被引用；断引用之后能删，环信息跟着更新")
        void selfLoopCountsAsReferenced() {
            EvaluationGraph2 graph = new Fixture().varFormula("V", "V + 1").graph();
            assertTrue(graph.hasCycle(), "前提：V = V + 1 是构造期就报出来的自环");
            assertFalse(graph.removeNode(new Node.Var("V")), "自引用也算被引用");
            assertEquals(List.of("VAR V"), sorted(graph.breakReferences(new Node.Var("V"))));
            assertTrue(graph.contains(new Node.Var("V")));
            assertTrue(graph.removeNode(new Node.Var("V")));
            assertNoCycle("删完之后自环也跟着没了", graph.cycleInfo());
        }

        // endregion

        // region 改

        @Test
        @DisplayName("M1 换变量的来源：旧引用退掉、新引用挂上")
        void applyVarFormulaReplacesRefs() {
            Fixture f = new Fixture().varConst("A", 1).varFormula("B", "A + 1");
            EvaluationGraph2 graph = f.graph();
            // 探针：`A = B + 1` 会不会成环 —— 等价于问"从 B 走得到 A 吗"
            assertCycle("[[VAR A -> VAR B -> VAR A]]", "改之前 B -> A 那条边在",
                    graph.checkVarFormula("A", new Formula("B + 1", 0f)));
            assertSuccess("改成与 A 无关的公式", graph.applyVarFormula("B", new Formula("t * 2", 0f)));
            assertSuccess("改之后 B 不再引用 A", graph.checkVarFormula("A", new Formula("B + 1", 0f)));
        }

        @Test
        @DisplayName("M2 成环时整条不做：图状态不变，新节点也不会被加进来")
        void applyVarFormulaRejectsCycle() {
            Fixture f = new Fixture().varConst("V", 1).func("f", "a", "a + V");
            EvaluationGraph2 graph = f.graph();
            assertCycle("[[FUNC f -> VAR V -> FUNC f]]", "V = f(1) 成环",
                    graph.applyVarFormula("V", new Formula("f(1)", 0f)));
            assertFalse(graph.hasCycle(), "被拒的改动不该改图");
            assertCycle("[[VAR fresh -> VAR fresh]]", "新变量自引用",
                    graph.applyVarFormula("fresh", new Formula("fresh + 1", 0f)));
            assertFalse(graph.contains(new Node.Var("fresh")), "成环时新节点也不该加进来");
        }

        @Test
        @DisplayName("M3 图里已有环时，无关的改动照旧放行（与 T7b 同一口径）")
        void applyOnGraphWithExistingCycle() {
            Fixture f = new Fixture()
                    .varFormula("a", "b + 1")
                    .varFormula("b", "a + 1")
                    .varConst("w", 1);
            EvaluationGraph2 graph = f.graph();
            assertTrue(graph.hasCycle(), "前提：a <-> b 已经成环");
            assertSuccess("把 w 换成固定值与既有环无关", graph.applyVarFormula("w", new Constant(2f)));
            assertTrue(graph.hasCycle(), "既有环还在（改完重算过，不是缓存过期）");
            assertCycle("[[VAR a -> VAR b -> VAR a]]", "既有环原样", graph.cycleInfo());
        }

        @Test
        @DisplayName("M4 改完会重算环信息：把成环的那条边换掉，hasCycle 跟着变")
        void applyRefreshesCycleInfo() {
            Fixture f = new Fixture().varFormula("a", "b + 1").varFormula("b", "a + 1");
            EvaluationGraph2 graph = f.graph();
            assertTrue(graph.hasCycle(), "前提：a <-> b 成环");
            assertSuccess("a 换成固定值", graph.applyVarFormula("a", new Constant(0f)));
            assertFalse(graph.hasCycle(), "a 那条边退掉之后环就没了");
            assertNoCycle("改完之后重算过", graph.cycleInfo());
        }

        @Test
        @DisplayName("M5 轨道挂公式：边加上；摘掉公式：边退掉")
        void applyTrackFormulaAddsAndRetracts() {
            Fixture f = new Fixture().varConst("V", 1).track("fov");
            EvaluationGraph2 graph = f.graph();
            assertSuccess("新挂一条读 V 的公式", graph.applyTrackFormula("fov", null, "V"));
            // 探针：把 V 绑回 fov 会成环，说明 fov -> V 那条边在
            assertCycle("[[TRACK fov -> VAR V -> TRACK fov]]", "边加上了",
                    graph.checkVarFormula("V", new TrackRef("fov")));
            assertSuccess("摘掉这条公式", graph.applyTrackFormula("fov", "V", null));
            assertSuccess("边退掉了", graph.checkVarFormula("V", new TrackRef("fov")));
        }

        @Test
        @DisplayName("M6 同一条轨道两个槽位都提到 V：换掉其中一个，V 那条边不能跟着没")
        void applyTrackFormulaKeepsSharedRefs() {
            Fixture f = new Fixture()
                    .varConst("V", 1)
                    .varConst("W", 2)
                    .track("fov")
                    .trackFormula("fov", 1f, KeyField.VALUE, "V")
                    .trackFormula("fov", 2f, KeyField.VALUE, "V + W");
            EvaluationGraph2 graph = f.graph();
            // V 被两条公式提到（计数 2）：把第一个槽位换成不引用谁的常量，V 的边必须留着
            assertSuccess("槽位一 V -> 0", graph.applyTrackFormula("fov", "V", "0"));
            assertCycle("[[TRACK fov -> VAR V -> TRACK fov]]", "另一个槽位还读着 V",
                    graph.checkVarFormula("V", new TrackRef("fov")));
            // 第二个槽位也换掉，V 与 W 的计数这才归零
            assertSuccess("槽位二 V + W -> 0", graph.applyTrackFormula("fov", "V + W", "0"));
            assertSuccess("两个槽位都不读 V 了", graph.checkVarFormula("V", new TrackRef("fov")));
            assertSuccess("W 也一起退了", graph.checkVarFormula("W", new TrackRef("fov")));
        }

        @Test
        @DisplayName("M7 轨道公式成环时整条不做（T4 的改版：走 apply 而不是 check）")
        void applyTrackFormulaRejectsCycle() {
            Fixture f = new Fixture().track("fov").varTrack("V", "fov");
            EvaluationGraph2 graph = f.graph();
            assertCycle("[[TRACK fov -> VAR V -> TRACK fov]]", "挂在绑定自己的变量上",
                    graph.applyTrackFormula("fov", null, "V"));
            assertFalse(graph.hasCycle(), "被拒的改动不该改图");
            assertSuccess("边没加上", graph.checkVarFormula("V", new TrackRef("fov")));
        }

        @Test
        @DisplayName("M8 换函数体：旧引用退掉、新引用挂上；成环时整条不做")
        void applyFuncFormula() {
            Fixture f = new Fixture().varConst("V", 1).func("f", "x", "x");
            EvaluationGraph2 graph = f.graph();
            assertSuccess("函数体改成读 V", graph.applyFuncFormula("f", Set.of("x"), "x + V"));
            assertCycle("[[FUNC f -> VAR V -> FUNC f]]", "函数体引用了 V",
                    graph.checkVarFormula("V", new Formula("f(1)", 0f)));
            assertSuccess("函数体改回不引用任何变量", graph.applyFuncFormula("f", Set.of("x"), "x"));
            assertSuccess("边退掉了", graph.checkVarFormula("V", new Formula("f(1)", 0f)));
            assertCycle("[[FUNC fresh -> FUNC fresh]]", "新函数自递归",
                    graph.applyFuncFormula("fresh", Set.of(), "fresh()"));
            assertFalse(graph.contains(new Node.Func("fresh")), "成环时新节点也不该加进来");
        }

        @Test
        @DisplayName("M9 节点不在图里时 apply 顺手加进来（与 check 的 definedWith 同口径）")
        void applyAddsMissingNode() {
            Fixture f = new Fixture().varConst("V", 1);
            EvaluationGraph2 graph = f.graph();
            assertSuccess("新变量直接给来源", graph.applyVarFormula("fresh", new Formula("V + 1", 0f)));
            assertTrue(graph.contains(new Node.Var("fresh")));
            assertSuccess("新轨道直接挂公式", graph.applyTrackFormula("newTrack", null, "V"));
            assertTrue(graph.contains(new Node.Track("newTrack")));
            assertFalse(graph.hasCycle());
        }

        // endregion
    }

    // endregion

    // region 夹具与工具

    /// 放行：类型 SUCCESS，且环信息打印为空
    private static void assertSuccess(String what, CheckResult result) {
        assertEquals(CheckResult.Type.SUCCESS, result.type(), what + "：应放行，实际打印 " + render(result));
        assertTrue(result.cycles().isEmpty(), what + "：放行时不该带环信息，实际 " + render(result));
        assertEquals("SUCCESS", render(result), what + "：打印应与类型一致");
    }

    /// 拒：类型 CYCLE，且环信息打印恰好是 [expected]
    private static void assertCycle(String expected, String what, CheckResult result) {
        assertEquals(CheckResult.Type.CYCLE, result.type(), what + "：应判成环，实际打印 " + render(result));
        assertEquals(expected, render(result), what + "：环信息打印不符");
    }

    /// 拒：构造期/整图判环的环信息打印恰好是 [expected]
    private static void assertCycle(String expected, String what, List<List<Node>> cycles) {
        assertFalse(cycles.isEmpty(), what + "：应报出环，实际打印 " + render(cycles));
        assertEquals(expected, render(cycles), what + "：环信息打印不符");
    }

    /// 无环：环信息打印为空
    private static void assertNoCycle(String what, List<List<Node>> cycles) {
        assertTrue(cycles.isEmpty(), what + "：不该有环信息，实际 " + render(cycles));
        assertEquals("[]", render(cycles), what + "：环信息打印应为空表");
    }

    /// 环信息打印：非 CYCLE 打类型名，CYCLE 打规范化后的环路径表
    private static String render(CheckResult result) {
        if (result.type() != CheckResult.Type.CYCLE) {
            return result.type().name();
        }
        return render(result.cycles());
    }

    /// 环信息打印：每条环规范化成 `[起点 -> … -> 起点]`，多条按字符串排序，于是与 DFS 起点无关
    private static String render(List<List<Node>> cycles) {
        List<String> rendered = new ArrayList<>();
        for (List<Node> cycle : cycles) {
            rendered.add("[" + String.join(" -> ", canonical(cycle)) + "]");
        }
        Collections.sort(rendered);
        return rendered.toString();
    }

    /// 一条闭合路径的规范形式：去掉末尾重复的收尾节点 → 旋转到字典序最小的起点 → 把起点补回末尾
    private static List<String> canonical(List<Node> cycle) {
        List<String> nodes = display(cycle);

        if (nodes.size() > 1 && nodes.getFirst().equals(nodes.getLast())) {
            nodes.removeLast();
        }

        int pivot = 0;
        for (int i = 1; i < nodes.size(); i++) {
            if (nodes.get(i).compareTo(nodes.get(pivot)) < 0) {
                pivot = i;
            }
        }

        List<String> rotated = new ArrayList<>(nodes.subList(pivot, nodes.size()));
        rotated.addAll(nodes.subList(0, pivot));
        rotated.add(rotated.getFirst());
        return rotated;
    }

    /// 节点打印成 `类别 名字`：同名不同类也分得清
    private static List<String> display(List<Node> cycle) {
        List<String> names = new ArrayList<>();
        for (Node node : cycle) {
            names.add(display(node));
        }
        return names;
    }

    /// 单个节点的打印：三类节点各是一个 record，这里按类型分派
    private static String display(Node node) {
        return switch (node) {
            case Node.Var var -> "VAR " + var.name();
            case Node.Func func -> "FUNC " + func.name();
            case Node.Track track -> "TRACK " + track.id();
        };
    }

    /// 节点集合的打印：按显示名排序，免得断言依赖集合自己的迭代顺序
    private static List<String> sorted(Set<Node> nodes) {
        List<String> rendered = new ArrayList<>();
        for (Node node : nodes) {
            rendered.add(display(node));
        }
        Collections.sort(rendered);
        return rendered;
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

        /// 给轨道挂一个读 [expression] 的键（时间 1 的取值槽位）
        Fixture trackFormula(String id, String expression) {
            return trackFormula(id, 1f, KeyField.VALUE, expression);
        }

        /// 给轨道在 [time] 处的键的 [field] 槽位挂一条公式。
        ///
        /// 一条轨道上要挂多条公式时靠时间或槽位区分开——同一个时间的键会被后一次整体覆盖
        /// （`Curve.key(Keyframe)` 对已存在的时间走 `set`）
        Fixture trackFormula(String id, float time, KeyField field, String expression) {
            for (CurveTrack track : curves) {
                if (track.id().equals(id)) {
                    Keyframe key = Keyframe.create(time, 0f);
                    key.source(field, new Formula(expression, 0f));
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

        CheckResult applyTrackFormula(String trackId, String oldExpression, String newExpression) {
            return graph().applyTrackFormula(trackId, oldExpression, newExpression);
        }

        CheckResult applyVarFormula(String varName, ValueSource source) {
            return graph().applyVarFormula(varName, source);
        }

        CheckResult applyFuncFormula(String funcName, Set<String> parameters, String expression) {
            return graph().applyFuncFormula(funcName, parameters, expression);
        }
    }

    // endregion
}
