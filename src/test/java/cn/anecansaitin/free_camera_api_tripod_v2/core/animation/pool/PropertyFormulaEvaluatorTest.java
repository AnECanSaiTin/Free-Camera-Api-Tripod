package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 属性公式的显式 `t` / `p` / `wt` 输入与确定性内建函数。
class PropertyFormulaEvaluatorTest {

    @Test
    void quantitiesAndBuiltinsUseExplicitInputs() {
        PropertyFormulaEvaluator.Result result = PropertyFormulaEvaluator.evaluate(
                "t + p + wt + clamp(100, 0, 5) + select(t, 1, 2)",
                new TrackPoolEvaluationInputs(2f, 0.5, 10.0),
                "fov", Revision.INITIAL);

        assertTrue(!result.isError());
        assertEquals(2.0 + 0.5 + 10.0 + 5.0 + 1.0, result.value(), 1e-9);
    }

    @Test
    void unknownVariableIsRejected() {
        PropertyFormulaEvaluator.Result result = PropertyFormulaEvaluator.evaluate(
                "missing + 1", new TrackPoolEvaluationInputs(0f, 0.0, 0.0),
                "fov", Revision.INITIAL);

        assertTrue(result.isError());
    }
}
