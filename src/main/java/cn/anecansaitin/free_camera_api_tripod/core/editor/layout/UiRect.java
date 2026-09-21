package cn.anecansaitin.free_camera_api_tripod.core.editor.layout;

/// 编辑器 UI 的整数矩形。不可变，便于每帧重新计算布局而不产生别名问题。
public record UiRect(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < right() && my >= y && my < bottom();
    }

    public UiRect inset(int amount) {
        return inset(amount, amount, amount, amount);
    }

    public UiRect inset(int left, int top, int right, int bottom) {
        return new UiRect(x + left, y + top, Math.max(0, width - left - right), Math.max(0, height - top - bottom));
    }

    public UiRect offset(int dx, int dy) {
        return new UiRect(x + dx, y + dy, width, height);
    }
}
