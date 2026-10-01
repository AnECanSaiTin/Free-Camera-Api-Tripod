package cn.anecansaitin.free_camera_api_tripod.api.animation;

import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/// [KeyField] 的集合查询。
///
/// 单独一个类只为一件事：Java 不允许同一个类里出现**签名相同的静态方法与实例方法**，
/// 而"这一个槽位在该模式下参不参与"（[KeyField#activeIn(EvaluateMode)]）与
/// "该模式下有哪些槽位参与"（[#activeIn(EvaluateMode)]）恰好同名——后者是面板要用的那一个，
/// 它决定了面板显示哪几行。
@NullMarked
public final class KeyFields {
    private KeyFields() {
    }

    /// 该模式下参与求值的全部槽位；面板据此决定显示哪几行。
    ///
    /// 返回的是不可变集合。取值槽位在三种模式下都参与，所以结果不会为空
    public static Set<KeyField> activeIn(EvaluateMode mode) {
        EnumSet<KeyField> active = EnumSet.noneOf(KeyField.class);

        for (KeyField field : KeyField.values()) {
            if (field.activeIn(mode)) {
                active.add(field);
            }
        }

        return Collections.unmodifiableSet(active);
    }
}
