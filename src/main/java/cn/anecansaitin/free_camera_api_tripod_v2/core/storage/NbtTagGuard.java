package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 在遍历文档前检查 NBT 的嵌套深度与条目数量。
///
/// 只统计 Compound 键、List 元素与基本数组元素；标量不增加条目。发现超限立即返回，不继续
/// 遍历输入。字节规模仍由 [net.minecraft.nbt.NbtAccounter] 在读取阶段限制。
@NullMarked
final class NbtTagGuard {

    private NbtTagGuard() {
    }

    /// @return 超限说明；未超限时为 `null`
    static @Nullable String check(Tag tag, NbtResourceLimits limits) {
        Counter counter = new Counter();
        String violation = visit(tag, 1, limits, counter);

        if (violation != null) {
            return violation;
        }

        if (counter.entries > limits.maxEntries()) {
            return "NBT entries exceed limit " + limits.maxEntries()
                    + ": " + counter.entries;
        }

        return null;
    }

    private static @Nullable String visit(Tag tag,
                                          int depth,
                                          NbtResourceLimits limits,
                                          Counter counter) {
        if (tag instanceof CompoundTag compound) {
            if (depth > limits.maxDepth()) {
                return "NBT nesting exceeds limit " + limits.maxDepth() + ": " + depth;
            }

            counter.entries += compound.size();

            if (counter.entries > limits.maxEntries()) {
                return "NBT entries exceed limit " + limits.maxEntries()
                        + ": " + counter.entries;
            }

            for (String key : compound.keySet()) {
                String violation = visit(compound.get(key), depth + 1, limits, counter);

                if (violation != null) {
                    return violation;
                }
            }

            return null;
        }

        if (tag instanceof ListTag list) {
            if (depth > limits.maxDepth()) {
                return "NBT nesting exceeds limit " + limits.maxDepth() + ": " + depth;
            }

            counter.entries += list.size();

            if (counter.entries > limits.maxEntries()) {
                return "NBT entries exceed limit " + limits.maxEntries()
                        + ": " + counter.entries;
            }

            for (Tag element : list) {
                String violation = visit(element, depth + 1, limits, counter);

                if (violation != null) {
                    return violation;
                }
            }

            return null;
        }

        if (tag instanceof ByteArrayTag array) {
            counter.entries += array.size();
        } else if (tag instanceof IntArrayTag array) {
            counter.entries += array.size();
        } else if (tag instanceof LongArrayTag array) {
            counter.entries += array.size();
        }

        return counter.entries > limits.maxEntries()
                ? "NBT entries exceed limit " + limits.maxEntries() + ": " + counter.entries
                : null;
    }

    private static final class Counter {
        private long entries;
    }
}
