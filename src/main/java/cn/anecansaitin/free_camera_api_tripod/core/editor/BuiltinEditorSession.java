package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Pathc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorSession;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.Selected;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ConfirmDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// 内置编辑器会话：把 {@link EditorContext} 适配成对外暴露的 {@link EditorSession}。
///
/// 内置界面与外部界面后端共用同一个会话实例，因此看到的是同一份状态与编辑进度。
public final class BuiltinEditorSession implements EditorSession {
    private final EditorContext context;

    public BuiltinEditorSession(EditorContext context) {
        this.context = context;
    }

    @Override
    public CameraAnimationc animation() {
        return context.animation();
    }

    @Override
    public Pathc path() {
        return context.editor().path();
    }

    @Override
    public float duration() {
        return context.duration();
    }

    @Override
    public float pixelsPerSecond() {
        return context.pixelsPerSecond();
    }

    @Override
    public void pixelsPerSecond(float pixelsPerSecond) {
        context.pixelsPerSecond(pixelsPerSecond);
    }

    @Override
    public float viewStartTime() {
        return context.viewStartTime();
    }

    @Override
    public void viewStartTime(float viewStartTime) {
        context.viewStartTime(viewStartTime);
    }

    @Override
    public float majorTickStep() {
        return context.majorTickStep();
    }

    @Override
    public String formatTick(float time, float step) {
        return context.formatTick(time, step);
    }

    @Override
    public float snapTime(float time) {
        return context.snapTime(time);
    }

    @Override
    public float playheadTime() {
        return context.player().time();
    }

    @Override
    public boolean playing() {
        return context.player().playing();
    }

    @Override
    public void togglePlay() {
        // 播放前先把视角切回预览视角，否则自由视角会盖住动画，播放看不到效果
        if (!context.player().playing() && context.editor().viewMode() != CameraEditorModel.ViewMode.PREVIEW) {
            context.editor().viewMode(CameraEditorModel.ViewMode.PREVIEW);
        }

        context.player().toggle();
    }

    @Override
    public void stopPlayback() {
        context.player().stop();
    }

    @Override
    public void seek(float time) {
        context.player().seek(time);
    }

    @Override
    public @Nullable AnimationTrack selectedTrack() {
        return context.editor().selectedTrack();
    }

    @Override
    public int selectedKeyIndex() {
        return context.editor().selectedKeyIndex();
    }

    @Override
    public int selectedPathIndex() {
        return context.editor().selectedPathNode().index();
    }

    @Override
    public boolean pathMode() {
        return context.animation().motionMode() == CameraAnimation.MotionMode.PATH;
    }

    @Override
    public void selectTrack(String trackId) {
        context.editor().selectTrack(trackId);
    }

    @Override
    public void selectKey(int index) {
        context.editor().selectKey(index);
    }

    @Override
    public void switchToPathMode() {
        context.switchToPathMode();
    }

    @Override
    public void switchToCoordinateMode() {
        context.switchToCoordinateMode();
    }

    @Override
    public void recordPathNode() {
        context.recordPathNode();
    }

    @Override
    public boolean selectPathNode(int index) {
        return context.editor().selectPathNode(new Selected(index, Selected.Type.NODE));
    }

    @Override
    public int addKey(float time) {
        @Nullable AnimationTrack track = context.editor().selectedTrack();
        return track == null ? -1 : context.editor().addKey(track, time);
    }

    @Override
    public int moveKey(int index, float time) {
        @Nullable AnimationTrack track = context.editor().selectedTrack();
        return track == null ? -1 : context.editor().moveKey(track, index, time);
    }

    @Override
    public boolean removeKey(int index) {
        @Nullable AnimationTrack track = context.editor().selectedTrack();
        return track != null && context.editor().removeKey(track, index);
    }

    @Override
    public boolean removeSelectedKey() {
        @Nullable AnimationTrack track = context.editor().selectedTrack();
        int index = context.editor().selectedKeyIndex();
        return track != null && index >= 0 && track.removeKey(index);
    }

    @Override
    public @Nullable Component pendingConfirm() {
        @Nullable ConfirmDialog dialog = context.dialog();
        return dialog == null ? null : dialog.message();
    }

    @Override
    public void confirmPending() {
        @Nullable ConfirmDialog dialog = context.dialog();
        context.closeDialog();

        if (dialog != null) {
            dialog.confirm();
        }
    }

    @Override
    public void dismissPending() {
        context.closeDialog();
    }

    @Override
    public void notify(Component message) {
        context.notify(message);
    }

    @Override
    public @Nullable Component statusMessage() {
        return context.statusMessage();
    }

    @Override
    public void close() {
        // 走屏幕自己的关闭流程，让内置界面有机会保存布局、释放视口资源
        if (Minecraft.getInstance().screen != null) {
            Minecraft.getInstance().screen.onClose();
        }
    }
}
