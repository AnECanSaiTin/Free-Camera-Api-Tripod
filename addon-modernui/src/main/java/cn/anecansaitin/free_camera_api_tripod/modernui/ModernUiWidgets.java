package cn.anecansaitin.free_camera_api_tripod.modernui;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.function.Supplier;

/// Modern UI 控件工厂：统一内边距、字号与配色，让面板构建代码只关心结构。
final class ModernUiWidgets {
    static final int PAD = 6;
    static final float TITLE_SIZE = 13F;
    static final float TEXT_SIZE = 12F;
    private static final int DIVIDER_HEIGHT = 1;

    private ModernUiWidgets() {
    }

    // region 布局

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    /// 占满宽度，高度自适应
    static LinearLayout.LayoutParams fillWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /// 占满宽度并取固定高度
    static LinearLayout.LayoutParams fillWidth(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    /// 按权重占满宽度（横向分栏）
    static LinearLayout.LayoutParams weighted(float weight) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    /// 固定宽度、自适应高度
    static LinearLayout.LayoutParams fixedWidth(int width) {
        return new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /// 自适应大小
    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // endregion

    // region 文本与按钮

    static TextView title(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text, TextView.BufferType.NORMAL);
        view.setTextSize(TITLE_SIZE);
        view.setTextColor(ModernUiColors.TEXT);
        view.setPadding(PAD, PAD, PAD, PAD);
        return view;
    }

    static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text, TextView.BufferType.NORMAL);
        view.setTextSize(TEXT_SIZE);
        view.setTextColor(ModernUiColors.TEXT);
        view.setPadding(PAD, PAD, PAD, PAD);
        return view;
    }

    static TextView dim(Context context, String text) {
        TextView view = label(context, text);
        view.setTextColor(ModernUiColors.TEXT_DIM);
        return view;
    }

    static Button button(Context context, String text, Runnable action) {
        Button view = new Button(context);
        view.setText(text, TextView.BufferType.NORMAL);
        view.setTextSize(TEXT_SIZE);
        view.setTextColor(ModernUiColors.TEXT);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    /// 不带动作的按钮：调用方稍后自行挂监听（例如需要把自己当锚点的下拉菜单）
    static Button textButton(Context context, String text) {
        Button view = new Button(context);
        view.setText(text, TextView.BufferType.NORMAL);
        view.setTextSize(TEXT_SIZE);
        view.setTextColor(ModernUiColors.TEXT);
        return view;
    }

    static View divider(Context context) {
        View view = new View(context);
        view.setBackground(new ColorDrawable(ModernUiColors.BORDER));
        view.setMinimumHeight(DIVIDER_HEIGHT);
        return view;
    }

    // endregion

    // region 组合

    /// 「名称：值」一行，值固定
    static LinearLayout field(Context context, String name, String value) {
        LinearLayout row = row(context);
        row.addView(label(context, name), weighted(1.0F));
        row.addView(dim(context, value), wrap());
        return row;
    }

    /// 「名称：值」一行，值逐帧跟随；内容没变时不会去动控件
    static LinearLayout liveField(Context context, String name, ModernUiRefresher refresher,
                                  Supplier<String> value) {
        LinearLayout row = row(context);
        row.addView(label(context, name), weighted(1.0F));
        TextView view = dim(context, value.get());
        row.addView(view, wrap());
        ModernUiRefresher.Gate gate = new ModernUiRefresher.Gate();
        refresher.add(() -> {
            if (gate.changed(value.get())) {
                view.setText(value.get(), TextView.BufferType.NORMAL);
            }
        });
        return row;
    }

    /// 带标题的面板容器，内容由调用方继续 addView
    static LinearLayout panel(Context context, String title) {
        LinearLayout panel = column(context);
        panel.setPadding(PAD, PAD, PAD, PAD);
        panel.addView(title(context, title), fillWidth());
        panel.addView(divider(context), fillWidth());
        return panel;
    }

    /// 按钮行：多个按钮等宽并排
    static LinearLayout buttonRow(Context context, float weightTotal) {
        LinearLayout row = row(context);
        row.setWeightSum(weightTotal);
        return row;
    }

    /// 等宽按钮；配合 {@link #buttonRow} 使用
    static LinearLayout.LayoutParams equalWeight(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    /// 可滚动的容器
    static ScrollView scroll(Context context, View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        // 滚动条是叠着画的，关掉它时间轴各行的宽度才和标尺严格对齐
        scroll.setVerticalScrollBarEnabled(false);
        // 用基类布局参数交给 ScrollView 自己转换，免得把 LinearLayout 的参数塞进别的容器
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    // endregion
}
