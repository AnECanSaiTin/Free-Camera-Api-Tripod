package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.NumberSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.OverlayId;
import org.jspecify.annotations.NullMarked;

/// 成片叠加元素：遮幅、字幕或图片（主设计 §7.8）。
///
/// ## 每个元素持久化什么
///
/// 稳定 ID、类型、启用、层级、起止时间与类型参数。**数值参数复用封闭的值源层级**
/// （[NumberSource]），语义与关键帧字段一致：可以是固定值，也可以是公式。
///
/// **静态标签不自动变成表达式**：文本内容保持文本，只有声明的数值参数才是值源。
///
/// ## 合成顺序
///
/// 同层元素**按文档顺序**合成；`zOrder` 决定层级。文档顺序是数据，
/// 不靠 ID 排序（主设计 §3.1）。
///
/// ## 不透明度与淡入淡出
///
/// [opacity] 是该元素自身的不透明度，[fadeInDuration] / [fadeOutDuration]
/// 是按统一时间求值的淡入淡出时长。三者在 O3 阶段启用。
///
/// **淡入淡出只改变元素透明度，不是镜头之间的过渡**（主设计 §7.8）：
/// 本轮明确不做镜头过渡与混合，不能借这几个字段把过渡做进来。
///
/// ## 缺失资源必须可见
///
/// 图片资源缺失、字体不可用时，编辑器显示诊断；**正式启用的必需叠加资源不可解析**
/// 按准备失败处理，不能悄悄省略（主设计 §7.8、不变量 I9）。
///
/// ## 资源不随文档打包
///
/// 音频与图片只存资源引用，不随文档自动打包（D25）。分享时显示缺失或哈希不匹配，
/// 并允许显式重绑定。
///
/// @param id               稳定身份；文档内唯一
/// @param kind             元素类型
/// @param enabled          是否参与合成。关闭即不参与，且不参与时长计算
/// @param zOrder           层级；同层按文档顺序合成
/// @param startTime        开始时刻，秒；必须有限且非负
/// @param endTime          结束时刻，秒；必须有限且 `>= startTime`
/// @param opacity          自身不透明度，固定值或公式；取值域由 L4 校验
/// @param fadeInDuration   淡入时长，秒；`0` 表示不淡入
/// @param fadeOutDuration  淡出时长，秒；`0` 表示不淡出
/// @param content          类型参数：遮幅的比例、字幕的文本与字体、图片的资源引用等；
///                         形状由 [OverlayKind] 决定，本批以不透明引用承载
@NullMarked
public record OverlayElement(
        OverlayId id,
        OverlayKind kind,
        boolean enabled,
        int zOrder,
        float startTime,
        float endTime,
        NumberSource opacity,
        float fadeInDuration,
        float fadeOutDuration,
        Object content) {

    public OverlayElement {
        if (!Float.isFinite(startTime) || startTime < 0f) {
            throw new IllegalArgumentException("Overlay startTime must be finite and non-negative: " + startTime);
        }

        if (!Float.isFinite(endTime) || endTime < startTime) {
            throw new IllegalArgumentException(
                    "Overlay endTime must be finite and >= startTime: " + endTime + " < " + startTime);
        }

        if (!Float.isFinite(fadeInDuration) || fadeInDuration < 0f) {
            throw new IllegalArgumentException("Overlay fadeInDuration must be finite and non-negative");
        }

        if (!Float.isFinite(fadeOutDuration) || fadeOutDuration < 0f) {
            throw new IllegalArgumentException("Overlay fadeOutDuration must be finite and non-negative");
        }
    }

    /// 该元素对文档时长的贡献。
    ///
    /// **未启用的元素不计入**：关闭的叠加不该延长动画。
    public float endTime() {
        return enabled ? endTime : 0f;
    }

    /// 是否有淡入或淡出。O3 阶段启用。
    public boolean hasFade() {
        return fadeInDuration > 0f || fadeOutDuration > 0f;
    }

    public OverlayElement withEnabled(boolean value) {
        return new OverlayElement(id, kind, value, zOrder, startTime, endTime, opacity,
                fadeInDuration, fadeOutDuration, content);
    }

    public OverlayElement withRange(float newStartTime, float newEndTime) {
        return new OverlayElement(id, kind, enabled, zOrder, newStartTime, newEndTime, opacity,
                fadeInDuration, fadeOutDuration, content);
    }

    public OverlayElement withZOrder(int value) {
        return new OverlayElement(id, kind, enabled, value, startTime, endTime, opacity,
                fadeInDuration, fadeOutDuration, content);
    }

    public OverlayElement withOpacity(NumberSource newOpacity) {
        return new OverlayElement(id, kind, enabled, zOrder, startTime, endTime, newOpacity,
                fadeInDuration, fadeOutDuration, content);
    }

    public OverlayElement withContent(Object newContent) {
        return new OverlayElement(id, kind, enabled, zOrder, startTime, endTime, opacity,
                fadeInDuration, fadeOutDuration, newContent);
    }
}
