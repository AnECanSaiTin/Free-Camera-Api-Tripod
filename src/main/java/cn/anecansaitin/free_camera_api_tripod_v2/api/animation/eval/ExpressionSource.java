package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 公式源码的身份：失败诊断属于哪一段文本，以及它在文档中的位置（主设计 §4.2、§6.1）。
///
/// ## 为什么不能只有区间
///
/// 嵌套求值时，内层失败（函数体、变量公式、被引用轨道公式）的区间属于**内层源码**，
/// 不是入口表达式。若只带 `[start, end)`，上层无法判断该区间应映射到入口、函数体还是
/// 变量定义，也不能安全地高亮。
///
/// ## 为什么需要字段粒度
///
/// 同一机位的两条通道可以写相同源码；同一布局的 `x / y / width / height` 也可以写相同源码
/// 与相同区间。若来源只到 `TrackId` / `RigId`，求值入口无法区分当前是哪条通道、哪个分量
/// （复核 R44-N2）。因此通道来源可带 `KeyId`，布局来源必须带 [LayoutField] 并可带 `KeyId`。
///
/// ## 与缓存的关系
///
/// 本类型描述的是**调用位置**，不是编译产物：编译结果仍按源码文本共享。
/// 缓存命中取回的结果若来自其它入口，必须按当前 [ExpressionSource] 重新包装诊断，
/// 不能把别的调用者的来源链当成自己的。
@NullMarked
public sealed interface ExpressionSource
        permits ExpressionSource.Document,
        ExpressionSource.Channel,
        ExpressionSource.Layout,
        ExpressionSource.Variable,
        ExpressionSource.Function {

    /// 文档级公式（当前未使用，保留给将来的文档级派生数值）。
    record Document() implements ExpressionSource {
    }

    /// 数值通道 / 曲线轨道内的公式。
    ///
    /// @param trackId 所属轨道；跨机位唯一
    /// @param keyId   公式所在关键帧；曲线整体公式或非关键帧位置为空
    record Channel(TrackId trackId, @Nullable KeyId keyId) implements ExpressionSource {
        public Channel {
            Objects.requireNonNull(trackId, "trackId");
        }

        /// 轨道级公式（不在具体关键帧内）。
        public static Channel of(TrackId trackId) {
            return new Channel(trackId, null);
        }
    }

    /// 机位布局（`AnimatedNumber`）内的公式。
    ///
    /// @param rigId 所属机位
    /// @param field 布局分量：`x / y / width / height`
    /// @param keyId 公式所在关键帧；静态值或曲线整体公式为空
    record Layout(RigId rigId, LayoutField field, @Nullable KeyId keyId)
            implements ExpressionSource {
        public Layout {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(field, "field");
        }

        /// 布局某分量的静态公式（不在关键帧内）。
        public static Layout of(RigId rigId, LayoutField field) {
            return new Layout(rigId, field, null);
        }
    }

    /// 变量定义内的公式。
    ///
    /// @param name 变量名；文档内唯一
    record Variable(String name) implements ExpressionSource {
        public Variable {
            requireName(name);
        }
    }

    /// 自定义函数体内的公式。
    ///
    /// @param name 函数名；文档内唯一
    record Function(String name) implements ExpressionSource {
        public Function {
            requireName(name);
        }
    }

    /// 布局矩形的数值分量。
    enum LayoutField {
        /// 矩形 x。
        X,

        /// 矩形 y。
        Y,

        /// 矩形宽度。
        WIDTH,

        /// 矩形高度。
        HEIGHT
    }

    private static void requireName(@Nullable String name) {
        Objects.requireNonNull(name, "name");

        if (name.isBlank()) {
            throw new IllegalArgumentException("ExpressionSource name must not be blank");
        }
    }
}
