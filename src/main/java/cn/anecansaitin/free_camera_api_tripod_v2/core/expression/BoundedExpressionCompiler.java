package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionCompiler;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// 按源码文本缓存编译结果的有界 LRU 编译器。
///
/// 编译契约要求“同一文本只解析一次”，但缓存不得改变返回语义：缓存值就是委托编译器返回的
/// 不可变 [CompilationResult]，成功与失败都缓存。容量按**源码文本条数**限制，不做按字节的
/// 二次估算；淘汰最久未使用的条目。
///
/// ## 线程
///
/// 所有读写都在同一把锁内，实例可被多个求值会话共享；委托编译器本身也必须允许串行调用。
/// 编解码、校验与求值可以并发使用同一实例，但解析在锁内串行。
@NullMarked
public final class BoundedExpressionCompiler implements ExpressionCompiler {

    /// 默认缓存条数。
    public static final int DEFAULT_MAX_ENTRIES = 256;

    private final ExpressionCompiler delegate;
    private final int maxEntries;
    private final Map<String, CompilationResult> cache;

    /// 使用默认无缓存编译器和默认容量。
    public BoundedExpressionCompiler() {
        this(new DefaultExpressionCompiler(), DEFAULT_MAX_ENTRIES);
    }

    /// @param maxEntries 最大缓存条数，正数
    public BoundedExpressionCompiler(int maxEntries) {
        this(new DefaultExpressionCompiler(), maxEntries);
    }

    /// @param delegate   实际编译器，非空
    /// @param maxEntries 最大缓存条数，正数
    public BoundedExpressionCompiler(ExpressionCompiler delegate, int maxEntries) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");

        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1: " + maxEntries);
        }

        this.maxEntries = maxEntries;
        this.cache = new LinkedHashMap<>(Math.min(16, maxEntries), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CompilationResult> eldest) {
                return size() > BoundedExpressionCompiler.this.maxEntries;
            }
        };
    }

    @Override
    public synchronized CompilationResult compile(String source) {
        Objects.requireNonNull(source, "source");
        return cache.computeIfAbsent(source, delegate::compile);
    }

    /// 当前缓存条数。
    public synchronized int size() {
        return cache.size();
    }

    /// 清空缓存。
    public synchronized void clear() {
        cache.clear();
    }

    /// 最大缓存条数。
    public int maxEntries() {
        return maxEntries;
    }
}
