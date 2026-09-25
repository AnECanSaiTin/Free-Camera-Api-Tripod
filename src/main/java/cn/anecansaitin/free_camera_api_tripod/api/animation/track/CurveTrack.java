package cn.anecansaitin.free_camera_api_tripod.api.animation.track;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
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
        if (time < 0) {
            return -1;
        }

        // 新键取该时刻的当前值，并沿用前一个键的插值模式，避免破坏已有曲线形态
        Keyframe key = Keyframe.create(time, curve.evaluate(time));
        Keyframe pre = curve.preKey(time);

        if (pre != null) {
            key.evaluateMode(pre.evaluateMode())
                    .weightedMode(pre.weightedMode());
        }

        return curve.key(key);
    }

    /// 以指定取值插入键（用于初始化默认关键帧）
    public int addKey(float time, float value) {
        if (time < 0) {
            return -1;
        }

        return curve.key(Keyframe.create(time, value));
    }

    @Override
    public boolean removeKey(int index) {
        return curve.removeKey(index);
    }

    @Override
    public int moveKey(int index, float newTime) {
        return curve.moveKey(index, newTime);
    }

    @Override
    public float valueAt(float time) {
        return curve.evaluate(time);
    }

    @Override
    public Curve curve() {
        return curve;
    }
}
