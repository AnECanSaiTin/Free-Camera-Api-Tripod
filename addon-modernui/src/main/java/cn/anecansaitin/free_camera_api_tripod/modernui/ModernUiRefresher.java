package cn.anecansaitin.free_camera_api_tripod.modernui;

import icyllis.modernui.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// 每帧刷新器。
///
/// Modern UI 是保留模式界面：控件搭好之后不会自己跟着数据走。编辑器里的时长、选中项、
/// 播放头这些随时会变，所以面板把「把当前值写回控件」的动作登记到这里，
/// 由本类挂在界面根节点上逐帧执行。
///
/// 逐帧执行不等于逐帧重绘——真正会变的只是少数几个值，
/// 动作里用 {@link Gate} 挡掉没变化的那些，重绘量就仍然是保留模式该有的量级。
final class ModernUiRefresher {
    private final List<Runnable> actions = new ArrayList<>();
    private boolean running;

    /// 登记一个刷新动作
    void add(Runnable action) {
        actions.add(action);
    }

    /// 挂在指定节点上开始逐帧刷新；节点从窗口上摘下后自动停止
    void start(View driver) {
        if (running) {
            return;
        }

        running = true;
        driver.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                running = false;
            }
        });
        driver.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }

                for (Runnable action : actions) {
                    action.run();
                }

                driver.postOnAnimation(this);
            }
        });
    }

    /// 变化检测：把一组当前值攒成快照，只有内容真的变了才让调用方去改控件或重绘。
    ///
    /// 避免自绘控件逐帧重绘就靠它——播放头、缩放、选中项都没动时，`changed` 一直返回 false。
    static final class Gate {
        private String snapshot;

        boolean changed(Object... values) {
            String current = Arrays.toString(values);

            if (current.equals(snapshot)) {
                return false;
            }

            snapshot = current;
            return true;
        }
    }
}
