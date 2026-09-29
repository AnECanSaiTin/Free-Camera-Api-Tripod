package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.Scope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 轨道读数：取某条曲线轨道在求值时刻的值。
///
/// 只作**变量的取值来源**用，不会出现在关键帧 / 路径节点的字段上（那些字段只有固定值与公式两种模式），
/// 所以它没有固定数值（[#constant] 返回 NaN）。
///
/// 取轨道值时走的是**静态曲线**（见 [ExpressionScope#track]），因此"变量绑的轨道上又引用了这个变量"
/// 这种自嵌套不会无限递归；代价见 [ExpressionScope] 的类文档。
@NullMarked
public final class TrackValue implements ValueSource {
    private String trackId;

    public TrackValue(String trackId) {
        this.trackId = trackId;
    }

    public String trackId() {
        return trackId;
    }

    public TrackValue trackId(String trackId) {
        this.trackId = trackId;
        return this;
    }

    @Override
    public float evaluate(@Nullable Scope scope) {
        return scope == null ? Float.NaN : scope.track(trackId);
    }

    @Override
    public float constant() {
        return Float.NaN;
    }

    /// 轨道读数没有固定数值可写，只能整体换成一个固定值源
    @Override
    public ValueSource withConstant(float value) {
        return new ConstantValue(value);
    }

    @Override
    public ValueSource copy() {
        return new TrackValue(trackId);
    }

    @Override
    public String toString() {
        return "@" + trackId;
    }
}
