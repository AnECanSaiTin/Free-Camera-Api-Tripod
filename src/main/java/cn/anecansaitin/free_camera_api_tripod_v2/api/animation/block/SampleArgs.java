package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 世界采样类型的**参数值**：开放不可变契约（主设计 §7.7、D16，Q7 裁决）。
///
/// ## 为什么是名义接口而不是 `Object` 或泛型
///
/// `WorldSample` 需要一个“要采什么、在哪采”的静态参数（坐标、方块 id、UUID、
/// 记分板目标名…），形状由**采样类型自己**的 `MapCodec` 定义。三种承载方式：
///
/// | 方式 | 问题 |
/// | --- | --- |
/// | 不透明 `Object` | 编译期没有任何约束；能塞进 `float[]` / `List` 等可变值；类型与 `kind` 不匹配只能运行期发现 |
/// | 泛型 `WorldSample<T>` | 文档里是异构集合，`List<WorldSample<?>>` 的通配符扩散到目录、Codec 与编辑器；仍无法阻止 `T` 与 `kind` 不匹配 |
/// | **名义接口（本类型）** | 参数至少“有身份”，`kind` 可与 `type()` 校验；Codec 与校验器有统一的注册入口 |
///
/// ## 契约要求
///
/// 实现（通常由采样类型注册表提供）必须满足：
///
/// - **深不可变**：不持有可变集合、数组或活对象（`Level` / `Entity` 一类），
///   与 [Anchor] 的扩展契约同一口径；框架只能保证自己持有的外层容器被复制，
///   第三方不可变性是扩展契约，不能宣称由开放 Java 接口完全强制（主设计 §3.2）。
/// - **值相等**：实现 `equals` / `hashCode` 的值语义，否则文档比较、撤销历史去重
///   与“无变化不新增历史”都会失真。
/// - **静态参数**：不得依赖待求值相机、公式或其他样本；文档参数只能是静态坐标或
///   外部锚点引用（主设计 §7.7）。动态采样位置属于未来单独设计。
/// - **不返回可空类型**：[type] 恒非空，且必须等于所属 `WorldSample.kind`。
///
/// ## 编解码归属
///
/// 采样类型在 **common 侧**注册类型 ID、`MapCodec` / `StreamCodec`、结构校验与能力描述；
/// 采集器只在**客户端**注册（主设计 §7.7）。本接口只声明“参数值有类型身份”，
/// 不声明 Codec 方法——Codec 由注册表按 [type] 分派。
///
/// ## 与读数的关系
///
/// 参数是**文档数据**；实际读数见 [SampleReading]（`VALUE` / `UNKNOWN` /
/// `COLLECTION_ERROR`），读数属于本帧事实，不进文档、不参与撤销。
///
/// ## 示例
///
/// ```java
/// record BlockMatchArgs(Vec3 position, Identifier block) implements SampleArgs {
///     @Override public Identifier type() {
///         return Identifier.fromNamespaceAndPath("example", "block_match");
///     }
/// }
/// ```
///
/// 上面的 `type()` 必须与 `WorldSample.kind` 一致；`WorldSample` 构造器会校验。
@NullMarked
public interface SampleArgs {

    /// 采样类型标识，与所属 `WorldSample.kind` 一致。
    ///
    /// 注册表据此分派 `MapCodec`、校验器与客户端采集器；未知类型在解码期报错，
    /// 不得静默构造默认参数（主设计 §8.1、§7.7）。
    Identifier type();
}
