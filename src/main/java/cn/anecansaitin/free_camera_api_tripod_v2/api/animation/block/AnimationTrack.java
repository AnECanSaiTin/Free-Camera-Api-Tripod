package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 轨道：文档时间轴上一行可折叠、可选中的对象（主设计 §3.1）。
///
/// ## 三类轨道
///
/// | 分支 | 含义 | 谁实现 |
/// | --- | --- | --- |
/// | [CurveTrack] | 数值曲线：相机通道，或供变量读取的通用曲线 | 框架 |
/// | [KeyframeTrack] | 有类型 STEP 轨道：注视、机位活动、相机来源 | 框架（sealed，三个内建分支） |
/// | [ExtensionTrack] | 扩展轨道：事件、特效、第三方数据 | 框架与第三方 |
///
/// 这是**开放注册表**（主设计 D16）：类型标识用 `Identifier`，Codec 与校验器由
/// `TrackTypeRegistry` 分派。第三方专属数据通过 `ReplaceTrack` 操作整体提交
/// （主设计 §4.1），不发放原地增删移动方法。
///
/// 有类型状态轨道是**框架固定**的三个内建分支，不通过注册表开放：主设计 §8.1 要求
/// 不新增第四类开放注册表，状态值也不允许以数字伪装成曲线。
///
/// ## 身份
///
/// `id()` 是稳定身份，**在整份文档内唯一**（跨机位也唯一，主设计 §3.1）——
/// 这正是 `TrackRef` 只需要一个轨道 ID、不必退化成“机位 + 轨道”二元键的原因。
/// 用户可见名称是 `name()`，与 ID 分离：**改名不改 ID**，因此变量绑定的轨道不会因为
/// 改名而变成空指向（旧实现把“存档里的名字”和“轨道 id”当同一个值，改名要重绑符号表）。
///
/// ## 顺序
///
/// 本接口**不**声明顺序。顺序由集合自身顺序表达，并且分三处独立持久化
/// （主设计 §3.1）：机位顺序服务显示，机位内通道顺序服务序列化，文档级轨道顺序
/// 服务事件稳定分派。排序**不改变 ID**。
///
/// 注视、机位活动与相机来源轨道是所有者（`CameraRig.aim`、`RigView.active`、
/// `CameraAnimation.cameraSource`）上的**有类型访问入口**，不属于 `tracks` 通用表；
/// 统一时间轴从这些入口与文档级轨道**派生**只读目录（主设计 §3.1.1）。
///
/// ## 时长与键数
///
/// `duration()` 与 `keyCount()` 由各类型自己回答：框架只知道“这条轨道多长、有几个键”，
/// 不知道第三方轨道里的键长什么样。播放长度取启用机位的姿态与状态轨道、相机来源、
/// 文档轨道、标记与叠加范围的**有限最大结束时间**；静态布局不延长动画。
@NullMarked
public sealed interface AnimationTrack permits CurveTrack, KeyframeTrack, AnimationTrack.ExtensionTrack {

    /// 稳定身份，整份文档内唯一。移动、排序、重命名、撤销都保留它。
    TrackId id();

    /// 用户可见名称。可本地化显示、可改；改它不影响 [id]。
    String name();

    /// 轨道类型标识，注册表据此分派 Codec 与校验器。
    ///
    /// 内置类型用 `free_camera_api_tripod:curve` 一类的固定 ID；
    /// 未知类型在**解码期**报错（主设计 §8.1），不能悄悄丢轨道。
    Identifier type();

    /// 该轨道最后一个键的时间，单位秒；没有键时为 0。
    ///
    /// 时间必须**有限且非负**；非有限值属于 ERROR，不是“很长的轨道”。
    float duration();

    /// 键数量。界面用它判断“这条轨道是不是空的”，序列化用它做规模校验。
    int keyCount();

    /// 扩展轨道：不属于框架曲线或内建状态模型、由类型注册表分派的轨道。
    ///
    /// 第三方实现本接口即可接入，但必须同时满足：
    ///
    /// - 深不可变（主设计 §3.2）：实现不得暴露可变集合或原地增删方法；
    /// - 提供 `MapCodec` / `StreamCodec`，并在 `TrackTypeRegistry` 注册类型与校验器；
    /// - 需要通用时间轴编辑时，额外实现纯派生能力接口，而不是发放原地移动方法；
    /// - `type()` **不得返回内建类型 ID**：`free_camera_api_tripod:curve` / `:aim` /
    ///   `:active` / `:camera_source` 是框架保留值（见各轨道 `TYPE` 常量）。
    ///   类型注册表按 `Identifier` 分派 Codec 与校验器，伪造内建 ID 会让分派错乱；
    ///   L2 统一校验拒绝保留 ID（Q4 裁决：维持 sealed 扩展分支，同时禁止类型 ID 伪造）。
    ///
    /// 校验器参与**统一文档校验**（主设计 §4.2：注册的轨道与采样类型校验器参与统一校验），
    /// 因此第三方不能靠“解码时放行”绕过 L2–L4。
    ///
    /// ## 改名
    ///
    /// [withName] 是扩展轨道的必需能力：`EditOp.RenameTrack` 对扩展轨道直接调用它，
    /// 不再经过反射、包装器或 `ReplaceTrack` 兜底。实现必须返回**同一具体类型**的新实例，
    /// 保留 [id]、[type] 与自身 Codec 能编码的全部字段，只替换显示名；不得返回框架
    /// 不认识的装饰器，否则按 [type] 分派到的 `TrackType.codec()` 无法编码该对象。
    ///
    /// 这是对扩展点的破坏性契约变更：已有实现必须补上本方法并重新编译。
    non-sealed interface ExtensionTrack extends AnimationTrack {

        /// 返回替换显示名后的同类型实例。
        ///
        /// [id] 与 [type] 必须保持不变；其余字段由实现自行保留或按本类型语义更新。
        /// 该方法必须是纯派生操作，不修改当前对象。
        ///
        /// @param name 新的显示名，非空白
        ExtensionTrack withName(String name);
    }
}
