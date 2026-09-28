package cn.anecansaitin.free_camera_api_tripod.api.animation.track;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.CurveSampler;
import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.Scope;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 曲线轨道：把 {@link Curve} 适配为编辑器可统一处理的 {@link AnimationTrack}。
@NullMarked
public class CurveTrack implements AnimationTrack {
    private final String property;
    private final Curve curve;
    private final Component label;
    private final int color;

    public CurveTrack(String property, Curve curve, Component label, int color) {
        this.property = property;
        this.curve = curve;
        this.label = label;
        this.color = color;
    }

    @Override
    public Identifier type() {
        return TrackTypeRegistry.CURVE;
    }

    @Override
    public String id() {
        return property;
    }

    @Override
    public Component label() {
        return label;
    }

    @Override
    public int color() {
        return color;
    }

    @Override
    public float duration() {
        int size = curve.size();
        return size == 0 ? 0 : curve.key(size - 1).time();
    }

    @Override
    public int keyCount() {
        return curve.size();
    }

    @Override
    public @Nullable Keyframe key(int index) {
        return curve.key(index);
    }

    @Override
    public int addKey(float time) {
        // 没有作用域时取固定数值：读档、命令这类场景拿不到求值环境，退到回退值也算有个合理的起点
        return addKey(time, CurveSampler.sampleStatic(curve, time));
    }

    /// 按 [scope] 插入键：取值取该时刻**按公式算出的数**，与播放、曲线图上看到的一致。
    ///
    /// 没有作用域时（[addKey(float)]）只能取公式的回退值，插出来的键会与播放结果对不上
    @Override
    public int addKey(float time, Scope scope) {
        return addKey(time, CurveSampler.sampleOnce(curve, time, scope));
    }

    /// 以指定取值插入键（也可用于初始化默认关键帧）。
    ///
    /// 沿用前一个键的插值模式，避免破坏已有曲线形态；轨道还是空的时候没有前键，就用默认模式
    public int addKey(float time, float value) {
        if (time < 0) {
            return -1;
        }

        Keyframe key = Keyframe.create(time, value);
        Keyframe pre = curve.preKey(time);

        if (pre != null) {
            key.evaluateMode(pre.evaluateMode());
        }

        return curve.key(key);
    }

    @Override
    public boolean removeKey(int index) {
        return curve.removeKey(index);
    }

    @Override
    public int moveKey(int index, float newTime) {
        return curve.moveKey(index, newTime);
    }

    /// 底层曲线：图表编辑器与序列化都从这里取值 / 取键
    public Curve curve() {
        return curve;
    }
}
