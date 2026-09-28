package cn.anecansaitin.free_camera_api_tripod.api.animation.curve;

import org.jspecify.annotations.NullMarked;

/// 直接读键上固定数值的读取器：没有公式时就是那个数本身，挂了公式时读的是它的回退值。
///
/// 变量读轨道走的也是这一份（见 `eval.ExpressionScope`），所以"变量指向的轨道、
/// 该轨道的键又引用了这个变量"不会无限递归；代价是同一个键"作为相机属性播放"与
/// "作为变量被引用"可能得出不同的值，那是另一种语义歧义，由自嵌套检查单独提示。
@NullMarked
public final class StaticKeys implements KeyValues {
    private final Curvec curve;

    public StaticKeys(Curvec curve) {
        this.curve = curve;
    }

    @Override
    public float value(int index) {
        return curve.key(index).value();
    }

    @Override
    public float inSlope(int index) {
        return curve.key(index).inSlope();
    }

    @Override
    public float inLength(int index) {
        return curve.key(index).inLength();
    }

    @Override
    public float outSlope(int index) {
        return curve.key(index).outSlope();
    }

    @Override
    public float outLength(int index) {
        return curve.key(index).outLength();
    }
}
