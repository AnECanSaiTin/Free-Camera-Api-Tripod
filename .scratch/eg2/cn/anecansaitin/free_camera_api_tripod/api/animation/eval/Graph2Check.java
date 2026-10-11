package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.KeyField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Constant;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Formula;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.TrackRef;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// EvaluationGraph2 三个 check 方法的测试。**只测不改**：发现问题只列出用例。
///
/// `closes` = 期望判出环（返回非空），`passes` = 期望放行（返回空）；抛异常也按失败记录，不中断整轮。
public final class Graph2Check {
    private static int total;
    private static int failures;

    public static void main(String[] args) {
        trackChecks();
        variableChecks();
        functionChecks();
        walkerChecks();
        sideObservations();

        System.out.println();
        System.out.println(failures == 0 ? "ALL OK (" + total + " cases)" : failures + " / " + total + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // region 用例

    private static void trackChecks() {
        EvaluationGraph2 plain = new EvaluationGraph2(
                List.of(var("v", new Formula("t * 2", 0f))),
                List.of(),
                List.of(track("t1", "t + 1")));

        passes("T1 轨道公式引用一个不依赖本轨道的变量", () -> plain.checkTrackFormula("t1", "v + 1"));
        passes("T2 轨道公式只用内置量", () -> plain.checkTrackFormula("t1", "t * 2 + sin(t)"));
        passes("T3 轨道公式为空白", () -> plain.checkTrackFormula("t1", "   "));
        passes("T8 轨道公式引用未定义名字", () -> plain.checkTrackFormula("t1", "nope + 1"));
        passes("T9 新增轨道（图里没有的 id），公式引用已有变量",
                () -> plain.checkTrackFormula("brand-new", "v + 1"));

        EvaluationGraph2 bound = new EvaluationGraph2(
                List.of(var("fov", new TrackRef("fov"))),
                List.of(),
                List.of(track("fov", "t + 1")));
        closes("T4 变量绑本轨道、本轨道公式又引用它", () -> bound.checkTrackFormula("fov", "fov + 1"));

        EvaluationGraph2 twoHops = new EvaluationGraph2(
                List.of(var("v", new TrackRef("fov")), var("w", new Formula("v + 1", 0f))),
                List.of(),
                List.of(track("fov", "t + 1")));
        closes("T5 两跳闭合：fov → w → v → fov", () -> twoHops.checkTrackFormula("fov", "w"));

        EvaluationGraph2 viaFunc = new EvaluationGraph2(
                List.of(var("v", new TrackRef("fov"))),
                List.of(func("g", List.of("x"), "v + x")),
                List.of(track("fov", "t + 1")));
        closes("T6 经函数闭合：fov → g → v → fov", () -> viaFunc.checkTrackFormula("fov", "g(1)"));

        EvaluationGraph2 otherCycle = new EvaluationGraph2(
                List.of(var("v1", new Formula("v2 + 1", 0f)), var("v2", new Formula("v1 + 1", 0f))),
                List.of(),
                List.of(track("t9", "t")));
        passes("T7 图里另有无关的环，本次编辑不闭合", () -> otherCycle.checkTrackFormula("t9", "v1"));
    }

    private static void variableChecks() {
        EvaluationGraph2 alone = new EvaluationGraph2(List.of(var("V")), List.of(), List.of());
        closes("V1 自引用：V = V + 1（V 已在变量表里）", () -> alone.checkVarFormula("V", new Formula("V + 1", 0f)));
        closes("V2 自引用：新变量 fresh = fresh + 1", () -> alone.checkVarFormula("fresh", new Formula("fresh + 1", 0f)));
        passes("V9 新变量 fresh = V + 1（只引用已有变量）", () -> alone.checkVarFormula("fresh", new Formula("V + 1", 0f)));

        EvaluationGraph2 viaFunc = new EvaluationGraph2(
                List.of(var("a", new Formula("1", 0f))),
                List.of(func("f", List.of("x"), "x + a")),
                List.of());
        closes("V3 a = f(1)，而 f 的函数体引用 a", () -> viaFunc.checkVarFormula("a", new Formula("f(1)", 0f)));

        EvaluationGraph2 trackRef = new EvaluationGraph2(
                List.of(var("v")),
                List.of(),
                List.of(track("fov", "v + 1")));
        closes("V4 v 绑到键公式引用 v 的轨道", () -> trackRef.checkVarFormula("v", new TrackRef("fov")));
        passes("V5 换成普通公式", () -> trackRef.checkVarFormula("v", new Formula("t + 1", 0f)));
        passes("V6 换成固定值", () -> trackRef.checkVarFormula("v", new Constant(1f)));
        passes("V7 绑一条不存在的轨道", () -> trackRef.checkVarFormula("v", new TrackRef("nope")));
        passes("V8 引用未定义变量", () -> trackRef.checkVarFormula("v", new Formula("nope + 1", 0f)));
    }

    private static void functionChecks() {
        EvaluationGraph2 alone = new EvaluationGraph2(
                List.of(),
                List.of(func("f", List.of("x"), "x + 1")),
                List.of());
        closes("F1 自递归：f(x) = f(x)（f 已在函数表里）", () -> alone.checkFuncFormula("f", Set.of("x"), "f(x)"));
        closes("F2 自递归：新函数 fresh() = fresh()", () -> alone.checkFuncFormula("fresh", Set.of(), "fresh()"));
        passes("F6 函数体为空白", () -> alone.checkFuncFormula("f", Set.of("x"), ""));
        passes("F7 函数体只用内置量与未定义名字", () -> alone.checkFuncFormula("f", Set.of("x"), "sin(t) + nope(1)"));
        passes("F8 新函数 fresh() 的函数体只引用已有变量", () -> alone.checkFuncFormula("fresh", Set.of(), "t + 1"));

        EvaluationGraph2 mutual = new EvaluationGraph2(
                List.of(),
                List.of(func("f1", List.of("x"), "x + 1"), func("f2", List.of("x"), "f1(x)")),
                List.of());
        closes("F3 f1 改调 f2（f2 本来就调 f1）", () -> mutual.checkFuncFormula("f1", Set.of("x"), "f2(x)"));

        EvaluationGraph2 shadow = new EvaluationGraph2(
                List.of(var("q", new Formula("f(1)", 0f))),
                List.of(func("f", List.of("q"), "q + 1")),
                List.of());
        passes("F4 同名形参遮蔽变量：q 不算引用", () -> shadow.checkFuncFormula("f", Set.of("q"), "q + 1"));
        closes("F5 q 不是形参时它是真引用", () -> shadow.checkFuncFormula("f", Set.of("z"), "q + 1"));
    }

    private static void walkerChecks() {
        EvaluationGraph2 twoWays = new EvaluationGraph2(
                List.of(var("v", new TrackRef("fov")), var("w", new Formula("v + 1", 0f))),
                List.of(func("g", List.of("x"), "v + x")),
                List.of(track("fov", "t + 1")));
        run("W1 一次编辑闭合两条不同的环，各报一条", "2 条", () -> {
            List<List<EvaluationGraph2.Node>> cycles = twoWays.checkTrackFormula("fov", "w + g(1)");
            return new Outcome(cycles.size() == 2, cycles.size() + " 条：" + render(cycles));
        });
    }

    private static void sideObservations() {
        // S1：check 不改图、可重复
        EvaluationGraph2 graph = new EvaluationGraph2(
                List.of(var("v", new TrackRef("fov"))),
                List.of(),
                List.of(track("fov", "t + 1")));
        run("S1 check 是只读的，且同一判定可重复", "图保持无环；两次结果一致", () -> {
            boolean cleanBefore = !graph.hasCycle();
            List<List<EvaluationGraph2.Node>> first = graph.checkTrackFormula("fov", "v");
            List<List<EvaluationGraph2.Node>> second = graph.checkTrackFormula("fov", "v");
            graph.checkVarFormula("v", new Formula("v + 1", 0f));
            graph.checkFuncFormula("f", Set.of(), "f()");
            boolean cleanAfter = !graph.hasCycle() && graph.cycleInfo().isEmpty();
            boolean ok = cleanBefore && cleanAfter && !first.isEmpty() && render(first).equals(render(second));
            return new Outcome(ok, "无环=" + cleanAfter + "，判定=" + render(first) + "，重复=" + render(second));
        });

        // S2：cycleInfo 报出来的路径内容
        EvaluationGraph2 mutual = new EvaluationGraph2(
                List.of(var("a", new Formula("b + 1", 0f)), var("b", new Formula("a + 1", 0f))),
                List.of(),
                List.of());
        run("S2 cycleInfo 报出的环应恰好含 a、b 各一次并首尾闭合",
                "[VAR a, VAR b, VAR a]（或等价的闭合顺序）", () -> {
                    List<List<EvaluationGraph2.Node>> info = mutual.cycleInfo();
                    int size = info.isEmpty() ? 0 : info.getFirst().size();
                    boolean shaped = info.size() == 1 && closedAndDistinct(info.getFirst(), 2);
                    return new Outcome(shaped, render(info) + "（首条长度 " + size + "）");
                });
    }

    // endregion

    // region 断言与工具

    @FunctionalInterface
    private interface Check {
        List<List<EvaluationGraph2.Node>> run();
    }

    @FunctionalInterface
    private interface Probe {
        Outcome run() throws Throwable;
    }

    private record Outcome(boolean pass, String actual) {
    }

    private static void closes(String what, Check check) {
        outcome(what, "判出至少一条环（非空）", check, false);
    }

    private static void passes(String what, Check check) {
        outcome(what, "放行（空表）", check, true);
    }

    private static void outcome(String what, String expected, Check check, boolean wantEmpty) {
        run(what, expected, () -> {
            List<List<EvaluationGraph2.Node>> cycles = check.run();
            boolean ok = wantEmpty ? cycles.isEmpty() : !cycles.isEmpty();
            return new Outcome(ok, cycles.isEmpty() ? "空（放行）" : render(cycles));
        });
    }

    /// 跑一个用例：抛异常也算失败，但不中断整轮
    private static void run(String what, String expected, Probe probe) {
        total++;
        boolean pass;
        String actual;

        try {
            Outcome outcome = probe.run();
            pass = outcome.pass();
            actual = outcome.actual();
        } catch (Throwable error) {
            pass = false;
            actual = "抛异常 " + error.getClass().getSimpleName() + ": " + error.getMessage();
        }

        if (!pass) {
            failures++;
        }

        System.out.println((pass ? "  ok   " : "  FAIL ") + what);
        System.out.println("         期望：" + expected);
        System.out.println("         实际：" + actual);
    }

    private static boolean closedAndDistinct(List<EvaluationGraph2.Node> cycle, int nodes) {
        if (cycle.size() != nodes + 1 || !cycle.getFirst().equals(cycle.getLast())) {
            return false;
        }

        return cycle.subList(0, cycle.size() - 1).stream().distinct().count() == nodes;
    }

    private static String render(List<List<EvaluationGraph2.Node>> cycles) {
        if (cycles.isEmpty()) {
            return "[]";
        }

        return cycles.stream()
                .map(cycle -> cycle.stream().map(Graph2Check::label).collect(Collectors.joining(" → ")))
                .collect(Collectors.joining("  |  ", "[", "]"));
    }

    /// 类型是 private enum，只能反射读；读不到就退回名字
    private static String label(EvaluationGraph2.Node node) {
        try {
            Method type = EvaluationGraph2.Node.class.getDeclaredMethod("type");
            type.setAccessible(true);
            return type.invoke(node) + " " + node.name();
        } catch (Throwable ignored) {
            return node.name();
        }
    }

    private static Variable var(String name) {
        return new Variable(name);
    }

    private static Variable var(String name, ValueSource source) {
        return new Variable(name, source);
    }

    private static CustomFunction func(String name, List<String> parameters, String body) {
        return new CustomFunction(name, List.copyOf(parameters), body);
    }

    /// 一条曲线轨道，单个键的取值槽位挂 [formula]
    private static CurveTrack track(String id, String formula) {
        Curve curve = new Curve();
        curve.key(new Keyframe(0f, 0f).source(KeyField.VALUE, new Formula(formula, 0f)));
        return new CurveTrack(id, curve, null, 0);
    }

    // endregion
}
