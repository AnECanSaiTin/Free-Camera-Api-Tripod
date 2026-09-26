package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ExpressionContext;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationCodec;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationFiles;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationSavedData;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.info.CameraInfo;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlayer;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ConfirmDialog;
import cn.anecansaitin.freecameraapi.core.ModifierManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/// 编辑器共享上下文：动画数据、播放器、编辑模型，以及时间轴/曲线图共用的视图参数。
public final class EditorContext {
    private final CameraEditorModel editor;
    private final CameraPlayer player;
    private final CameraAnimation animation;
    private final CameraInfo info;

    private float pixelsPerSecond = 80f;
    private float viewStartTime;
    private boolean snapEnabled = true;
    private float snapStep = 0.1f;
    private boolean bezierSymmetric = true;
    private float flySpeed = 12f;

    private @Nullable Component statusMessage;
    private long statusUntil;
    /// 当前的二次确认弹窗；由面板发起，屏幕负责绘制与派发点击
    private @Nullable ConfirmDialog dialog;
    /// 当前的表达式编辑窗口；同样是模态的，与二次确认一样由屏幕在最上层处理
    private @Nullable ExpressionEditorWindow expressionEditor;

    public EditorContext(CameraEditorModel editor, CameraPlayer player, CameraAnimation animation, CameraInfo info) {
        this.editor = editor;
        this.player = player;
        this.animation = animation;
        this.info = info;
    }

    public CameraEditorModel editor() {
        return editor;
    }

    public CameraPlayer player() {
        return player;
    }

    public CameraAnimation animation() {
        return animation;
    }

    public CameraInfo info() {
        return info;
    }

    public float duration() {
        return animation.duration();
    }

    // region 时间轴视图

    public float pixelsPerSecond() {
        return pixelsPerSecond;
    }

    public void pixelsPerSecond(float pixelsPerSecond) {
        this.pixelsPerSecond = Mth.clamp(pixelsPerSecond, 6f, 600f);
    }

    public float viewStartTime() {
        return viewStartTime;
    }

    public void viewStartTime(float viewStartTime) {
        // 时间轴最左端就是 0 秒：拖到 0 之后不再继续往左走，避免出现负时间
        this.viewStartTime = Math.max(0f, viewStartTime);
    }

    public boolean snapEnabled() {
        return snapEnabled;
    }

    public void snapEnabled(boolean snapEnabled) {
        this.snapEnabled = snapEnabled;
    }

    public float snapStep() {
        return snapStep;
    }

    /// 贝塞尔控制点是否对称：为 true 时在曲线图里拖动一侧切线，另一侧自动跟随
    public boolean bezierSymmetric() {
        return bezierSymmetric;
    }

    public void bezierSymmetric(boolean bezierSymmetric) {
        this.bezierSymmetric = bezierSymmetric;
    }

    /// 路径距离的显示形式：false 为绝对距离（格），true 为占全程的百分比（0~1）
    public boolean distancePercent() {
        return animation.distanceMode() == CameraAnimation.DistanceMode.PERCENT;
    }

    /// 切换距离口径；动画数据里已有的位置键会一起换算
    public void distancePercent(boolean distancePercent) {
        animation.distanceMode(distancePercent
                ? CameraAnimation.DistanceMode.PERCENT
                : CameraAnimation.DistanceMode.ABSOLUTE);
    }

    /// 时间轴与曲线图共用的主刻度间隔，保证刻线间距不小于约 56 像素
    public float majorTickStep() {
        float density = pixelsPerSecond;
        float[] candidates = {0.05f, 0.1f, 0.2f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 30f, 60f};

        for (float candidate : candidates) {
            if (candidate * density >= 56f) {
                return candidate;
            }
        }

        return 120f;
    }

    public String formatTick(float time, float step) {
        return step >= 1f
                ? Math.round(time) + "s"
                : String.format(java.util.Locale.ROOT, "%.1fs", time);
    }

    public float snapTime(float time) {
        if (!snapEnabled) {
            return Math.max(0f, time);
        }

        return Math.max(0f, Math.round(time / snapStep) * snapStep);
    }

    // endregion

    public float flySpeed() {
        return flySpeed;
    }

    public void flySpeed(float flySpeed) {
        this.flySpeed = Mth.clamp(flySpeed, 1f, 200f);
    }

    // region 提示信息

    public void notify(Component message) {
        this.statusMessage = message;
        this.statusUntil = System.currentTimeMillis() + 2600;
    }

    public @Nullable Component statusMessage() {
        return System.currentTimeMillis() < statusUntil ? statusMessage : null;
    }

    // endregion

    // region 二次确认

    /// 弹出二次确认；用户点「确认」后才执行 action
    public void confirm(Component message, Runnable action) {
        this.dialog = new ConfirmDialog(message, action);
    }

    /// 弹出多选一的询问；用户点了某一项才执行对应的动作
    public void choose(Component message, List<ConfirmDialog.Choice> choices) {
        this.dialog = new ConfirmDialog(message, choices);
    }

    public @Nullable ConfirmDialog dialog() {
        return dialog;
    }

    public void closeDialog() {
        this.dialog = null;
    }

    // endregion

    // region 表达式

    /// 本帧共用的求值上下文，由 {@link #beginFrame()} 每帧清空
    private @Nullable ExpressionContext frameContext;

    /// 每帧渲染开头调用一次。
    ///
    /// 表达式求值上下文里缓存着变量的取值，而变量是可以被界面随时改掉的（改名、改固定值、换绑定），
    /// 暂停时播放头时间又不动，光靠时间没法判断缓存是否过期，所以按帧清：一帧内复用、跨帧重算。
    /// 漏调不会算错数值，只会让界面预览停在旧值上，因此两个编辑器屏幕都在渲染开头调它
    public void beginFrame() {
        this.frameContext = null;
    }

    /// 打开表达式编辑窗口；窗口自己负责绘制与输入，屏幕只在最上层调用它。
    /// 同一时刻只留一个，后开的会直接顶掉已有的，不会叠出两层。
    public void openExpressionEditor(Component label, @Nullable String expression, Consumer<String> onConfirm) {
        this.expressionEditor = new ExpressionEditorWindow(animation, player, label, expression, onConfirm);
    }

    public @Nullable ExpressionEditorWindow expressionEditor() {
        return expressionEditor;
    }

    public void closeExpressionEditor() {
        this.expressionEditor = null;
    }

    /// 按播放头所在时刻求值一段公式；公式非法或引用到取不到值的变量时返回 NaN。
    ///
    /// 一帧内的多次调用共用一份上下文，同一个变量因此每帧只算一次——
    /// 变量面板的每一行、每个挂了公式的输入框都会来问一次，不共用就会重复求值
    public float evaluateExpression(@Nullable String expression) {
        if (frameContext == null) {
            frameContext = new ExpressionContext(animation, player.time());
        }

        return frameContext.evaluate(expression);
    }

    // endregion

    // region 路径绑定与运动模式

    /// 切到路径模式：先问是新建一条还是绑定已有的。
    ///
    /// 两种走法都会丢掉位置关键帧（路径模式下位置由路径给出），所以这句问话就充当了那一次二次确认，
    /// 选定之后不再重复询问。绑定已有的还会再问一次来源（存档 / 本地文件）。
    public void switchToPathMode() {
        if (animation.motionMode() == CameraAnimation.MotionMode.PATH) {
            return;
        }

        choose(EditorLang.t("inspector.path.switch_mode_prompt"), List.of(
                new ConfirmDialog.Choice(EditorLang.t("inspector.path.create_new"), this::createNewPath),
                new ConfirmDialog.Choice(EditorLang.t("inspector.path.bind_existing"), this::chooseBindSource)));
    }

    /// 新建一条路径并切到路径模式：清掉节点，只重建通道、不补关键帧。
    /// 与切到坐标模式对称——先切模式，再让模型按新模式重建通道
    private void createNewPath() {
        animation.motionMode(CameraAnimation.MotionMode.PATH);
        editor.path().clear();
        editor.pathReplaced();
        notify(EditorLang.t("notify.path_bound", editor.path().name()));
    }

    /// 读入的动画是路径模式时问一次路径从哪来。
    ///
    /// 动画 JSON 里只有「位置取自路径」这个标记，路径本身不存在里面，所以读完动画必须先定下一条路径。
    /// 问法与切到路径模式完全一致（新建 / 绑定已有的），区别只是模式标记已经就位，不必再切一次。
    public void choosePathAfterLoad() {
        if (animation.motionMode() != CameraAnimation.MotionMode.PATH) {
            return;
        }

        choose(EditorLang.t("inspector.path.load_prompt"), List.of(
                new ConfirmDialog.Choice(EditorLang.t("inspector.path.create_new"), this::createNewPath),
                new ConfirmDialog.Choice(EditorLang.t("inspector.path.bind_existing"), this::chooseBindSource)));
    }

    /// 绑定已有路径：再问一次从哪里取
    private void chooseBindSource() {
        choose(EditorLang.t("inspector.path.bind_source_prompt"), List.of(
                new ConfirmDialog.Choice(EditorLang.t("menu.file.storage"), this::bindFromStorage),
                new ConfirmDialog.Choice(EditorLang.t("menu.file.local"), this::bindFromLocal)));
    }

    /// 从本地 `.path` 文件绑定：先弹文件管理界面挑文件，选中后直接绑定（来源已在上一步问过）
    private void bindFromLocal() {
        FileBrowserScreen.open(false, AnimationFiles.pathDir(), null, AnimationFiles.PATH_SUFFIX, this::confirmBindPath);
    }

    /// 从存档里已有的路径绑定
    private void bindFromStorage() {
        if (AnimationSavedData.get() == null) {
            notify(EditorLang.t("notify.no_storage"));
            return;
        }

        StorageBrowserScreen.open(false, true, null, this::confirmBindStorage);
    }

    /// 选好本地文件后的绑定
    private void confirmBindPath(java.nio.file.Path file) {
        String json = AnimationFiles.loadPathFrom(file);
        String name = AnimationFiles.stem(file);

        if (json == null) {
            notify(EditorLang.t("notify.path_load_failed", name));
            return;
        }

        bindPath(json, name);
    }

    /// 选好存档里的路径后的绑定
    private void confirmBindStorage(String name) {
        String json = AnimationSavedData.loadPath(name);

        if (json == null) {
            notify(EditorLang.t("notify.path_load_failed", name));
            return;
        }

        bindPath(json, name);
    }

    /// 用已读出的 JSON 绑定路径：替换当前路径、切到路径模式，只重建通道、不补关键帧
    public boolean bindPath(String json, String name) {
        Path loaded = AnimationCodec.pathFromJson(json);

        if (loaded == null) {
            notify(EditorLang.t("notify.path_load_failed", name));
            return false;
        }

        loaded.name(name);
        editor.path(loaded);
        editor.pathReplaced();
        animation.motionMode(CameraAnimation.MotionMode.PATH);
        notify(EditorLang.t("notify.path_bound", name));
        return true;
    }

    /// 切到直接坐标模式：会丢掉路径距离关键帧，先二次确认。
    /// 确认后同时解除路径绑定——该模式的位置由坐标关键帧给出，留着旧路径只会让人误以为还绑着。
    public void switchToCoordinateMode() {
        if (animation.motionMode() == CameraAnimation.MotionMode.COORDINATE) {
            return;
        }

        confirm(EditorLang.t("notify.delete_position_keys_confirm"), () -> {
            animation.motionMode(CameraAnimation.MotionMode.COORDINATE);
            editor.path().clear();
            editor.pathReplaced();
        });
    }

    // endregion

    // region 录路径点

    /// 记录一个路径点：把相机当前位置加入路径，并选中它
    public void recordPathNode() {
        editor.addPathNodeAt(currentCameraPosition());
        notify(EditorLang.t("notify.path_node_added"));
    }

    // endregion

    // region 当前相机

    public Vector3f currentCameraPosition() {
        return new Vector3f(ModifierManager.INSTANCE.pos());
    }

    public Vector3f currentCameraRotation() {
        return new Vector3f(ModifierManager.INSTANCE.rot());
    }

    public float currentCameraFov() {
        return ModifierManager.INSTANCE.fov();
    }

    /// 把自由视角同步到当前相机，便于切到自由视角时不跳变
    public void syncFreePoseFromCamera() {
        editor.syncFreePose(currentCameraPosition(), currentCameraRotation(), currentCameraFov());
    }

    // endregion
}
