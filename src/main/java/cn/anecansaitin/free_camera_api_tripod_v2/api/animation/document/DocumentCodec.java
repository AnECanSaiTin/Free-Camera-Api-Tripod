package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DocumentValidator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 文档的两阶段解码入口（主设计 §4.2、§8.1、§8.2、§8.3）。
///
/// ```text
/// 第一阶段：结构解码（成员 Codec + 局部约束 + 类型注册表分派）
/// 第二阶段：统一文档校验（DocumentValidator，L2–L4）
/// → Accepted / StructureFailed / ValidationFailed
/// ```
///
/// ## 与设计的关系
///
/// - §4.2：提交、解码完成与收包载入采用**同一组强制文档校验规则**。本类只组合
///   `CameraAnimation` 的 Codec 与一个 `DocumentValidator`，不另立一套校验。
/// - §8.1：未知类型、版本不符、结构损坏必须报错；**不得把部分解码结果作为合法文档发布**。
///   因此结构阶段失败时没有候选文档，也不调用校验器。
/// - §8.2：存储实现负责字节 / Ops 适配与错误报告，不解释文档子对象字段。
///   本类不读取文件、不决定资源位置，只做“Ops → 候选 → 校验”。
/// - §8.3：请求载入后仍执行文档与播放参数校验——播放参数校验在播放层，不在本类。
///
/// ## 版本
///
/// `data_version` 的检查在 [CameraAnimation#mapCodec] 内部：缺失、较旧或高于当前支持的
/// 版本都在结构阶段拒绝，不会进入校验阶段。
///
/// ## 叠加（O1–O3 前）
///
/// 当前 `OverlayElement.content` 仍是不透明引用，文档 Codec 不写 `overlays`。
/// 编码含非空叠加的文档会**明确失败**，不静默丢弃作者数据；解码得到的文档叠加为空。
/// 该限制随 O1–O3 的封闭内容类型解除。
///
/// ## 线程
///
/// 本类无状态（除注入的 Codec 与校验器），可并发用于不同输入；发布到文档存储仍必须在
/// 宿主发布线程执行（主设计 §3.3）。
@NullMarked
public final class DocumentCodec {

    private final Codec<CameraAnimation> structure;
    private final DocumentValidator validator;

    private DocumentCodec(Codec<CameraAnimation> structure, DocumentValidator validator) {
        this.structure = Objects.requireNonNull(structure, "structure");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /// 按冻结的注册表与统一校验器构造。
    ///
    /// @param registries 文档编解码需要的三个类型注册表
    /// @param validator  统一文档校验器（组合了 L2–L4 的内置与第三方校验）
    public static DocumentCodec of(DocumentRegistries registries, DocumentValidator validator) {
        return new DocumentCodec(CameraAnimation.codec(registries), validator);
    }

    /// 两阶段解码。
    ///
    /// 结构阶段捕获构造器抛出的 `IllegalArgumentException` 并转成 [DocumentDecodeResult.StructureFailed]：
    /// 值类型把跨字段约束放在构造器里，解码边界必须把它们变成可报告错误，
    /// 不能让异常穿透到调用方（主设计 §8.1：非法数据必须报错）。
    ///
    /// @param ops   序列化操作（JSON / NBT 等，由存储层决定）
    /// @param input 输入值
    /// @param <T>   序列化表示类型
    public <T> DocumentDecodeResult decode(DynamicOps<T> ops, T input) {
        DataResult<CameraAnimation> parsed;

        try {
            parsed = structure.parse(ops, input);
        } catch (RuntimeException e) {
            // 值类型构造器的局部约束（重复键时间、布局尺寸非法等）。
            // 只在解码边界兜底一次，不把异常当作校验诊断。
            String message = describe(e);
            return new DocumentDecodeResult.StructureFailed(message,
                    StructureFieldPath.locate(message, input));
        }

        if (parsed.isError()) {
            String message = errorMessage(parsed);
            return new DocumentDecodeResult.StructureFailed(message,
                    StructureFieldPath.locate(message, input));
        }

        CameraAnimation candidate = parsed.result().orElseThrow();

        List<Diagnostic> diagnostics = validator.validate(candidate);
        boolean hasError = diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);

        return hasError
                ? new DocumentDecodeResult.ValidationFailed(diagnostics)
                : new DocumentDecodeResult.Accepted(candidate, diagnostics);
    }

    /// 编码一个文档。
    ///
    /// 不支持字段的检查由**根 Codec** 统一执行（复核 R34 C1）：`overlays` 在 O1–O3 前
    /// 双向拒绝，因此直接调用 `CameraAnimation.codec(registries)` 编码含叠加文档同样失败，
    /// 不存在绕过路径。本方法只负责把错误原样带回。
    ///
    /// @param document 待编码文档
    /// @param ops      序列化操作
    /// @param <T>      序列化表示类型
    public <T> DataResult<T> encode(CameraAnimation document, DynamicOps<T> ops) {
        return structure.encodeStart(ops, document);
    }

    /// 取 DFU 错误消息；没有错误时给出统一文案（调用方已确认 `isError()`）。
    private static String errorMessage(DataResult<?> result) {
        return result.error()
                .map(com.mojang.serialization.DataResult.Error::message)
                .orElse("Document decode failed");
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
