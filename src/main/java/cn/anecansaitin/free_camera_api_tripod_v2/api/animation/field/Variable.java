package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// 文档里的一个变量：名字 + 取值来源（主设计 §4.2 L3、§6.1）。
///
/// ## 取值来源可以是轨道读数
///
/// `source` 是 [ValueSource]，因此变量既能是固定值、公式，也能读一条曲线轨道
/// （[TrackRef]）。这正是“变量读曲线”这条能力的落点，也是 [TrackRef]
/// **不实现** [NumberSource]、进不了关键帧字段的原因。
///
/// ## 与轨道的关系
///
/// 变量**不持有**轨道对象，只持有 [TrackRef] 里的稳定 `TrackId`：
///
/// - 轨道改名不改 ID，所以改名不会让变量变成空指向 ——
///   旧实现把“存档里的名字”当轨道 id，改名必须显式重绑符号表，漏一处变量就恒为 NaN。
/// - 轨道删除时，指向它的 `TrackRef` 变成**悬空引用**，由 L2 引用完整性校验报告，
///   不靠“解绑”这种写路径维持一致性。
///
/// ## 名字空间
///
/// 名字要能被表达式识别为标识符（字母、下划线或非 ASCII 字符开头）。
/// 名字唯一性与“与内置量重名”的检查属于 L3 语义校验：内置量是 `t` / `p` / `wt` 一类保留名，
/// 与保留名冲突必须报告而不是静默覆盖。
///
/// ## 没有“默认值”字段
///
/// 旧实现给变量带一个 `defaultValue` 给界面当初始数，并在求值失败时充当替代值。
/// 主设计 §6.1 明确：变量的 UI 初始值**不得**作为运行期替代值。
/// 因此本契约不含该字段——界面要初始值就自己造一个 `Constant` 放进草稿，
/// 那属于编辑事务，不属于文档数据。
///
/// @param name   变量名；文档内唯一，非空白
/// @param source 取值来源；恒非空
@NullMarked
public record Variable(String name, ValueSource source) {

    /// 结构编码：`{name, source}`。
    ///
    /// `name` 非空白；`source` 用 `ValueSource.CODEC`，可含 `constant` / `formula` / `track_ref`。
    public static final MapCodec<Variable> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codecs.nonBlankString().fieldOf("name").forGetter(Variable::name),
            ValueSource.CODEC.fieldOf("source").forGetter(Variable::source)
    ).apply(instance, Variable::new));

    /// 独立解析一个变量时使用的 Codec。
    public static final Codec<Variable> CODEC = MAP_CODEC.codec();

    public Variable {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Variable name must not be blank");
        }
    }

    /// 新建一个常量变量。这是“刚建出来的变量”的形态：来源恒非空，
    /// “没有来源”这个第三态不存在。
    public static Variable constant(String name, float value) {
        return new Variable(name, new Constant(value));
    }

    /// 本变量是否读一条曲线轨道；是则给出轨道 ID。
    ///
    /// [ValueSource] 是封闭层级，因此这个判断是完备的，不需要额外的布尔标志。
    public Optional<TrackId> trackId() {
        return source instanceof TrackRef ref
                ? Optional.of(ref.trackId())
                : Optional.empty();
    }

    public Variable withName(String newName) {
        return new Variable(newName, source);
    }

    public Variable withSource(ValueSource newSource) {
        return new Variable(name, newSource);
    }
}
