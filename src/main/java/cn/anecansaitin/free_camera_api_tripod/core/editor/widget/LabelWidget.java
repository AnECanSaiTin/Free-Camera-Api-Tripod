package cn.anecansaitin.free_camera_api_tripod.core.editor.widget;

import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// 只读文本控件：一行文字，可截断、可指定颜色，可选点击回调。
///
/// 面板里那些"只是展示 + 悬停提示 + 偶尔点一下"的内容（提示行、只读预览、下拉标签、选中标记）
/// 用它来摆，省掉每处手写矩形命中与提示绘制——漏一处就会出现"看得见点不着"的死区。
public class LabelWidget extends EditorWidget {
    /// 颜色为 0 时随主题取 {@link Draw#TEXT}
    private static final int DEFAULT_COLOR = 0;

    private Component text;
    private int color = DEFAULT_COLOR;
    /// 画成输入框那样的底框：表示这一格点得开
    private boolean field;
    /// 行尾标记（展开箭头之类），右对齐；文字给它让出宽度，不会压上去
    private @Nullable Component suffix;
    /// 常态底色；0 表示不画（列表行靠它铺选中高亮）
    private int background;
    /// 悬停底色；0 表示不画
    private int hoverBackground;
    private @Nullable Runnable onClick;

    public LabelWidget(UiRect rect, Component text) {
        super(rect);
        this.text = text;
    }

    public Component text() {
        return text;
    }

    public void text(Component text) {
        this.text = text;
    }

    /// 文字色；传 0 表示随主题取默认色
    public LabelWidget color(int color) {
        this.color = color;
        return this;
    }

    /// 画成输入框那样的底框，提示这一格可以点开
    public LabelWidget field(boolean field) {
        this.field = field;
        return this;
    }

    /// 行尾标记：右对齐的小图标，文字会避开它
    public LabelWidget suffix(@Nullable Component suffix) {
        this.suffix = suffix;
        return this;
    }

    /// 行底色：常态一种、悬停一种，传 0 表示不画。
    /// 已经有常态底色时不再改成悬停色，选中行不会被悬停色盖掉
    public LabelWidget background(int background, int hoverBackground) {
        this.background = background;
        this.hoverBackground = hoverBackground;
        return this;
    }

    /// 左键点击时执行；不设则这一行不参与点击
    public LabelWidget onClick(@Nullable Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    /// 悬停提示；返回标签本身以便链式设置
    @Override
    public LabelWidget tooltip(@Nullable Component tooltip) {
        super.tooltip(tooltip);
        return this;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int resolved = color != DEFAULT_COLOR ? color : (enabled() ? Draw.TEXT : Draw.TEXT_DISABLED);
        int base = background != 0 ? background : (hovered() ? hoverBackground : 0);

        if (base != 0) {
            Draw.canvas(graphics, rect(), base);
        }

        if (field) {
            Draw.field(graphics, rect(), hovered());
        }

        int inset = field ? 3 : 0;
        int reserved = 0;

        if (suffix != null) {
            String icon = suffix.getString();
            Draw.textRight(graphics, icon, rect().right() - inset - 1, rect().centerY() - 4, Draw.TEXT_DIM);
            reserved = Draw.font().width(icon) + 3;
        }

        Draw.textEllipsized(graphics, text.getString(), rect().x() + inset, rect().centerY() - 4,
                Math.max(1, rect().width() - inset * 2 - reserved), resolved);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active() || onClick == null || event.button() != 0) {
            return false;
        }

        onClick.run();
        return true;
    }
}
