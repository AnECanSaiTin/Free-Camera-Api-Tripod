package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 声明式世界采样：文档声明“要读哪个世界量”（主设计 §7.7，D24）。
///
/// ```text
/// WorldSample
///   id: SampleId
///   name: 文档内唯一名称
///   kind: Identifier
///   args: SampleArgs（类型自身 MapCodec 定义的不可变参数）
///   resampleEveryTicks: 正整数，默认 1
/// ```
///
/// ## 为什么是声明式而不是查询函数
///
/// 表达式**不能**在求值中途任意访问世界。任意查询函数会让求值不再是纯函数、
/// 预算不可控、并可能顺带触发区块加载。声明式采样把“世界读数”做成宿主灌进求值环境的
/// **帧事实**：一帧采集一次，帧内所有通道读同一张表（主设计 §7.7、不变量 I5）。
///
/// ## 一律返回 float
///
/// 表达式只有数值。分类信息用**匹配查询**表达：`sample("ground_is_stone")` 返回 `0/1`，
/// 而不是返回“方块 id 的哈希”——哈希既不可读也不可移植（主设计 §7.7）。
///
/// 本 record 只是**声明**；宿主在帧准备阶段采集出的实际读数见 [SampleReading]：
/// `VALUE` / `UNKNOWN` / `COLLECTION_ERROR`。读数属于本帧事实，不进文档。
///
/// ## 参数是名义值
///
/// [args] 是 [SampleArgs] 的实例：参数形状由采样类型自己的 `MapCodec` 定义，
/// 但类型上仍必须“有身份”（[SampleArgs#type]），不能是不透明 `Object`——
/// 否则 `float[]`、`List` 这类可变值也能混进已发布文档（Q7 裁决）。
///
/// `kind` 与 `args.type()` **必须一致**：构造器直接拒绝不一致的组合。否则
/// 注册表会按 `kind` 分派 Codec 却解出另一类型的参数，属于无法解释的状态。
/// 类型自身的参数内容是否合法仍由采样类型校验器（L1/L2）判定，不在本 record 里展开。
///
/// ## 名字绑定
///
/// `name` 在文档内唯一，表达式用 `sample("名字")` 引用。名字必须是**静态字符串**，
/// 提交期绑定到 `SampleId`；重命名必须显式同步引用或拒绝产生悬空引用
/// （主设计 §7.7、§4.2 L3「样本名字字面量绑定」）。
///
/// ## 重采周期按游戏刻
///
/// `resampleEveryTicks` 是**正整数游戏刻**，默认 1。宿主在帧准备阶段冻结本帧可用表；
/// 周期大于 1 时不会因为渲染帧率高就重复采集（主设计 §7.7）。
/// 读数里的 `sampledAtTick` 与这里同口径。
///
/// @param id                 稳定身份；文档内唯一
/// @param name               表达式里使用的名字；文档内唯一
/// @param kind               采样类型标识，注册表据此分派 Codec 与采集器
/// @param args               类型自身定义的不可变参数，其 `type()` 必须等于 `kind`
/// @param resampleEveryTicks 重采周期，单位游戏刻，`>= 1`
@NullMarked
public record WorldSample(
        SampleId id,
        String name,
        Identifier kind,
        SampleArgs args,
        int resampleEveryTicks) {

    public WorldSample {
        if (name.isBlank()) {
            throw new IllegalArgumentException("WorldSample name must not be blank");
        }

        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(args, "args");

        if (!kind.equals(args.type())) {
            throw new IllegalArgumentException(
                    "WorldSample kind must match args.type(): " + kind + " != " + args.type());
        }

        if (resampleEveryTicks < 1) {
            throw new IllegalArgumentException(
                    "WorldSample resampleEveryTicks must be >= 1: " + resampleEveryTicks);
        }
    }

    /// 每次采集都重采的默认周期。
    public static final int EVERY_TICK = 1;

    /// 是否需要跨帧复用上一次读数。
    public boolean resampled() {
        return resampleEveryTicks > 1;
    }

    /// 换参数；`kind` 不变，构造器会重新校验 `kind == args.type()`。
    public WorldSample withArgs(SampleArgs newArgs) {
        return new WorldSample(id, name, kind, newArgs, resampleEveryTicks);
    }

    /// 换重采周期；参数与类型不变。
    public WorldSample withResampleEveryTicks(int newResampleEveryTicks) {
        return new WorldSample(id, name, kind, args, newResampleEveryTicks);
    }
}
