package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/// 布局求值的结构化诊断工厂与稳定错误码。
///
/// ## 为什么本包定义错误码
///
/// `api.animation.edit.DiagnosticCode` 目前没有布局专用码，而本批改动不能扩展 `api`。
/// 为了不让“非有限布局值 / 非正宽高”被误标成表达式错误或编辑错误，本包在 core 内定义
/// 两个稳定码；公式本身的编译 / 求值失败仍复用 `EXPRESSION_INVALID`，与曲线采样口径一致。
///
/// 两个码是**运行期求值失败**，不是可提交的编辑诊断：它们只出现在准备帧的失败结果里，
/// 不进入文档、不参与发布校验。
///
/// ## 定位
///
/// [Diagnostic#scope] 使用本帧快照的 [DiagnosticScope.Document]；[Diagnostic#location]
/// 的对象是布局所属 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId]，
/// 字段路径形如 `view.rect.width`；曲线端点公式额外带上 `.curve.keys.<KeyId>`，
/// 便于界面把错误高亮到具体分量与键。
@NullMarked
public final class LayoutDiagnostics {

    /// 布局数值非有限。
    ///
    /// 来源可以是常量、空曲线默认值、曲线端点或插值结果；消息里会写明具体来源。
    public static final DiagnosticCode LAYOUT_VALUE_INVALID =
            new DiagnosticCode("free_camera_api_tripod:layout_value_invalid");

    /// 布局矩形几何非法：宽度或高度不是有限正数。
    public static final DiagnosticCode LAYOUT_RECT_INVALID =
            new DiagnosticCode("free_camera_api_tripod:layout_rect_invalid");

    private LayoutDiagnostics() {
    }

    /// 非有限布局数值；[keyId] 非空表示失败发生在曲线端点公式或端点值上。
    static Diagnostic valueInvalid(LayoutEvalInput input, @Nullable KeyId keyId, String detail) {
        return diagnostic(LAYOUT_VALUE_INVALID, input, keyId, detail);
    }

    /// 非正或非有限宽高；[input] 的字段应是对应的 `WIDTH` / `HEIGHT`。
    static Diagnostic rectInvalid(LayoutEvalInput input, String detail) {
        return diagnostic(LAYOUT_RECT_INVALID, input, null, detail);
    }

    /// 公式求值失败：取失败链最内层诊断，保留其错误码、区间与细节。
    static Diagnostic expressionFailure(LayoutEvalInput input,
                                        @Nullable KeyId keyId,
                                        ExpressionFailure failure) {
        ExpressionDiagnostic diagnostic = failure.innermostDiagnostic().orElse(failure.diagnostic());

        return diagnostic(DiagnosticCode.EXPRESSION_INVALID, input, keyId,
                "layout formula failed (" + diagnostic.code() + " at ["
                        + diagnostic.start() + ", " + diagnostic.end() + ")): "
                        + diagnostic.detail());
    }

    /// 公式编译失败：被拒绝的编译不产生可求值句柄，必须按诊断失败。
    static Diagnostic compileFailure(LayoutEvalInput input,
                                     @Nullable KeyId keyId,
                                     ExpressionDiagnostic diagnostic) {
        return diagnostic(DiagnosticCode.EXPRESSION_INVALID, input, keyId,
                "layout formula cannot compile (" + diagnostic.code() + " at ["
                        + diagnostic.start() + ", " + diagnostic.end() + ")): "
                        + diagnostic.detail());
    }

    /// 字段路径：`view.rect.<field>`，曲线端点公式再附加键身份。
    static String fieldPath(LayoutEvalInput input, @Nullable KeyId keyId) {
        String path = "view.rect." + input.field().name().toLowerCase(Locale.ROOT);

        if (keyId == null) {
            return path;
        }

        return path + ".curve.keys." + keyId.value();
    }

    private static Diagnostic diagnostic(DiagnosticCode code,
                                         LayoutEvalInput input,
                                         @Nullable KeyId keyId,
                                         String detail) {
        DiagnosticScope scope = new DiagnosticScope.Document(input.context().snapshot().revision());
        Diagnostic.Location location = new Diagnostic.Location(input.rigId(), fieldPath(input, keyId));

        return new Diagnostic(code, Severity.ERROR, LocalizedMessage.literal(detail), scope, location, null);
    }
}
