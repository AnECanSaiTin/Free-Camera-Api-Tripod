package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionCompiler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 有界表达式编译缓存：同文本复用、失败缓存、LRU 淘汰与并发去重。
class BoundedExpressionCompilerTest {

    @Test
    void identicalSourceUsesCachedResult() {
        BoundedExpressionCompiler compiler = new BoundedExpressionCompiler();

        CompilationResult first = compiler.compile("1 + 2");
        CompilationResult second = compiler.compile("1 + 2");

        assertSame(first, second);
        assertEquals(1, compiler.size());
    }

    @Test
    void rejectedResultIsCachedToo() {
        BoundedExpressionCompiler compiler = new BoundedExpressionCompiler();

        CompilationResult first = compiler.compile("1 +");
        CompilationResult second = compiler.compile("1 +");

        assertTrue(first instanceof CompilationResult.Rejected);
        assertSame(first, second);
        assertEquals(1, compiler.size());
    }

    @Test
    void evictsLeastRecentlyUsedEntry() {
        CountingCompiler delegate = new CountingCompiler();
        BoundedExpressionCompiler compiler = new BoundedExpressionCompiler(delegate, 2);

        compiler.compile("1");
        compiler.compile("2");
        compiler.compile("1"); // 命中，1 变为最近使用
        compiler.compile("3"); // 淘汰 2

        assertEquals(2, compiler.size());
        assertEquals(3, delegate.calls.get());

        compiler.compile("2");
        assertEquals(4, delegate.calls.get());
    }

    @Test
    void clearDropsAllEntries() {
        BoundedExpressionCompiler compiler = new BoundedExpressionCompiler();
        compiler.compile("1");
        compiler.compile("2");

        compiler.clear();

        assertEquals(0, compiler.size());
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedExpressionCompiler(0));
    }

    @Test
    void concurrentCompileOfSameSourceRunsDelegateOnce() throws Exception {
        CountingCompiler delegate = new CountingCompiler();
        BoundedExpressionCompiler compiler = new BoundedExpressionCompiler(delegate, 8);
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int index = 0; index < threads; index++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        compiler.compile("1 + 2");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertEquals(1, delegate.calls.get());
    }

    private static final class CountingCompiler implements ExpressionCompiler {
        private final ExpressionCompiler delegate = new DefaultExpressionCompiler();
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompilationResult compile(String source) {
            calls.incrementAndGet();
            return delegate.compile(source);
        }
    }
}
