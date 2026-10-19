package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 共享求值会话的记账规则（R42-E1 契约补齐）。
///
/// 本批只交付契约，没有解释器实现；测试验证额度扣减、非部分扣减、深度成对与上限行为。
class EvaluationSessionTest {

    @Test
    void frameQuotaIsSharedAcrossCallsAndNeverPartiallyConsumed() {
        EvaluationSession session = EvaluationSession.create(100L, 8);

        assertTrue(session.tryConsumeWork(80));
        assertEquals(20L, session.remainingWork());
        assertEquals(80L, session.consumedWork());
        assertFalse(session.exhausted());

        // 第二次根调用拿不到 80 的额度：不足时不部分扣减。
        assertFalse(session.tryConsumeWork(80));
        assertEquals(20L, session.remainingWork());
        assertEquals(80L, session.consumedWork());

        assertTrue(session.tryConsumeWork(20));
        assertEquals(0L, session.remainingWork());
        assertTrue(session.exhausted());

        // 透支后才失败；失败本身不能退回已消耗额度。
        assertFalse(session.tryConsumeWork(1));
        assertEquals(100L, session.consumedWork());
    }

    @Test
    void zeroQuotaRejectsAnyRealWorkButAllowsZeroCostOperations() {
        EvaluationSession session = EvaluationSession.create(0L, 4);

        assertTrue(session.exhausted());
        assertTrue(session.tryConsumeWork(0));
        assertFalse(session.tryConsumeWork(1));
        assertEquals(0L, session.consumedWork());
    }

    @Test
    void depthMustBeEnteredAndExitedInPairs() {
        EvaluationSession session = EvaluationSession.create(100L, 2);

        assertEquals(0, session.currentDepth());
        assertTrue(session.tryEnterDepth());
        assertTrue(session.tryEnterDepth());
        assertEquals(2, session.currentDepth());
        assertEquals(0, session.remainingDepth());

        // 超过上限不改变深度。
        assertFalse(session.tryEnterDepth());
        assertEquals(2, session.currentDepth());

        session.exitDepth();
        session.exitDepth();
        assertEquals(0, session.currentDepth());

        assertThrows(IllegalStateException.class, session::exitDepth);
    }

    @Test
    void rejectsInvalidConfigurationAndNegativeUnits() {
        assertThrows(IllegalArgumentException.class, () -> EvaluationSession.create(-1L, 8));
        assertThrows(IllegalArgumentException.class, () -> EvaluationSession.create(10L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> EvaluationSession.create(10L, 8).tryConsumeWork(-1));
    }
}
