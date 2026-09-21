package cn.anecansaitin.free_camera_api_tripod.core.editor.layout;

import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.EditorPanel;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 编辑器停靠布局。
///
/// 上排为若干可横向拖拽调整宽度的面板列，下排为一个可纵向调整高度的面板。
/// 面板标题栏之间可以互相拖拽交换位置，双击标题栏可折叠。
public final class DockLayout {
    public static final int TOOLBAR_HEIGHT = 22;
    public static final int SPLITTER_SIZE = 4;

    private static final int MIN_COLUMN_WIDTH = 110;
    private static final int MIN_TOP_HEIGHT = 80;
    private static final int MIN_BOTTOM_HEIGHT = 70;

    private final List<EditorPanel> columns = new ArrayList<>();
    private final List<Float> columnWeights = new ArrayList<>();
    private final List<Float> defaultColumnWeights = new ArrayList<>();
    private @Nullable EditorPanel bottomPanel;
    private float bottomWeight = 0.28f;
    private static final float DEFAULT_BOTTOM_WEIGHT = 0.28f;

    private final List<UiRect> columnRects = new ArrayList<>();
    private final List<UiRect> verticalSplitters = new ArrayList<>();
    private UiRect horizontalSplitter = new UiRect(0, 0, 0, 0);
    private UiRect bottomRect = new UiRect(0, 0, 0, 0);
    private UiRect toolbarRect = new UiRect(0, 0, 0, 0);
    private int screenWidth;
    private int screenHeight;

    public void addColumn(EditorPanel panel, float weight) {
        columns.add(panel);
        columnWeights.add(weight);
        defaultColumnWeights.add(weight);
    }

    /// 恢复默认的面板比例
    public void resetLayout() {
        columnWeights.clear();
        columnWeights.addAll(defaultColumnWeights);
        bottomWeight = DEFAULT_BOTTOM_WEIGHT;
    }

    /// 上排列的 id 与权重，形如 {@code viewport:0.34}，供配置持久化使用
    public List<String> serializeColumns() {
        List<String> result = new ArrayList<>(columns.size());

        for (int i = 0; i < columns.size(); i++) {
            result.add(columns.get(i).id() + ":" + columnWeights.get(i));
        }

        return result;
    }

    /// 按保存的顺序与权重恢复上排列。
    ///
    /// 只要 id 集合与当前面板对不上（例如面板被增删）就整体放弃，避免布局错乱。
    public void restoreColumns(String saved) {
        if (saved == null || saved.isBlank()) {
            return;
        }

        List<EditorPanel> order = new ArrayList<>();
        List<Float> weights = new ArrayList<>();

        for (String entry : saved.split(",")) {
            int separator = entry.lastIndexOf(':');

            if (separator <= 0) {
                continue;
            }

            float weight;

            try {
                weight = Float.parseFloat(entry.substring(separator + 1).trim());
            } catch (NumberFormatException e) {
                continue;
            }

            String id = entry.substring(0, separator).trim();

            for (EditorPanel column : columns) {
                if (column.id().equals(id) && !order.contains(column)) {
                    order.add(column);
                    weights.add(weight);
                    break;
                }
            }
        }

        if (order.size() != columns.size()) {
            return;
        }

        columns.clear();
        columns.addAll(order);
        columnWeights.clear();
        columnWeights.addAll(weights);
    }

    public float bottomWeight() {
        return bottomWeight;
    }

    public void bottomWeight(float bottomWeight) {
        this.bottomWeight = Mth.clamp(bottomWeight, 0f, 1f);
    }

    public void bottom(EditorPanel panel) {
        this.bottomPanel = panel;
    }

    public List<EditorPanel> columns() {
        return columns;
    }

    public int columnCount() {
        return columns.size();
    }

    public @Nullable EditorPanel bottom() {
        return bottomPanel;
    }

    public UiRect toolbarRect() {
        return toolbarRect;
    }

    public List<UiRect> verticalSplitters() {
        return verticalSplitters;
    }

    public UiRect horizontalSplitter() {
        return horizontalSplitter;
    }

    /// 重新计算所有面板矩形
    public void update(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        toolbarRect = new UiRect(0, 0, width, TOOLBAR_HEIGHT);

        int contentY = TOOLBAR_HEIGHT;
        int contentHeight = Math.max(0, height - TOOLBAR_HEIGHT);

        // 折叠的下排面板只保留标题栏高度，把空间让给上排
        int bottomHeight;

        if (bottomPanel != null && bottomPanel.collapsed()) {
            bottomHeight = EditorPanel.HEADER_HEIGHT;
        } else {
            bottomHeight = Mth.clamp(Math.round(contentHeight * bottomWeight), MIN_BOTTOM_HEIGHT, Math.max(MIN_BOTTOM_HEIGHT, contentHeight - MIN_TOP_HEIGHT - SPLITTER_SIZE));
        }

        int topHeight = contentHeight - bottomHeight - SPLITTER_SIZE;

        int columnCount = columns.size();
        columnRects.clear();
        verticalSplitters.clear();

        if (columnCount > 0) {
            normalizeWeights();
            int available = width - (columnCount - 1) * SPLITTER_SIZE;
            int used = 0;
            int x = 0;

            for (int i = 0; i < columnCount; i++) {
                int columnWidth = i == columnCount - 1 ? available - used : Math.round(columnWeights.get(i) * available);
                columnWidth = Math.max(0, columnWidth);
                used += columnWidth;
                UiRect rect = new UiRect(x, contentY, columnWidth, topHeight);
                columnRects.add(rect);
                columns.get(i).rect(rect);
                x += columnWidth;

                if (i < columnCount - 1) {
                    verticalSplitters.add(new UiRect(x, contentY, SPLITTER_SIZE, topHeight));
                    x += SPLITTER_SIZE;
                }
            }
        }

        horizontalSplitter = new UiRect(0, contentY + topHeight, width, SPLITTER_SIZE);
        bottomRect = new UiRect(0, horizontalSplitter.bottom(), width, bottomHeight);

        if (bottomPanel != null) {
            bottomPanel.rect(bottomRect);
        }
    }

    private void normalizeWeights() {
        float sum = 0;

        for (float weight : columnWeights) {
            sum += weight;
        }

        if (sum <= 0) {
            return;
        }

        for (int i = 0; i < columnWeights.size(); i++) {
            columnWeights.set(i, columnWeights.get(i) / sum);
        }
    }

    /// 拖拽竖向分隔条：分配相邻两列的宽度
    public boolean resizeVertical(int splitterIndex, int mouseX) {
        if (splitterIndex < 0 || splitterIndex >= columnRects.size() - 1) {
            return false;
        }

        int available = screenWidth - (columns.size() - 1) * SPLITTER_SIZE;

        if (available <= 0) {
            return false;
        }

        normalizeWeights();
        float leftWeight = columnWeights.get(splitterIndex);
        float rightWeight = columnWeights.get(splitterIndex + 1);
        int pairWidth = Math.round((leftWeight + rightWeight) * available);
        int columnLeft = columnRects.get(splitterIndex).x();
        int desired = mouseX - columnLeft - SPLITTER_SIZE / 2;
        desired = Mth.clamp(desired, MIN_COLUMN_WIDTH, Math.max(MIN_COLUMN_WIDTH, pairWidth - MIN_COLUMN_WIDTH));

        float newLeft = (float) desired / available;
        columnWeights.set(splitterIndex, newLeft);
        columnWeights.set(splitterIndex + 1, leftWeight + rightWeight - newLeft);
        return true;
    }

    /// 拖拽横向分隔条：调整下排面板高度
    public boolean resizeHorizontal(int mouseY) {
        int contentY = TOOLBAR_HEIGHT;
        int contentHeight = Math.max(1, screenHeight - TOOLBAR_HEIGHT);
        int topHeight = mouseY - contentY - SPLITTER_SIZE / 2;
        topHeight = Mth.clamp(topHeight, MIN_TOP_HEIGHT, Math.max(MIN_TOP_HEIGHT, contentHeight - MIN_BOTTOM_HEIGHT - SPLITTER_SIZE));
        bottomWeight = Mth.clamp((float) (contentHeight - SPLITTER_SIZE - topHeight) / contentHeight, 0f, 1f);
        return true;
    }

    /// 交换上排两列的位置，实现面板拖拽重排
    public void swapColumns(int a, int b) {
        if (a == b || a < 0 || b < 0 || a >= columns.size() || b >= columns.size()) {
            return;
        }

        EditorPanel panelA = columns.get(a);
        EditorPanel panelB = columns.get(b);
        columns.set(a, panelB);
        columns.set(b, panelA);

        float weightA = columnWeights.get(a);
        float weightB = columnWeights.get(b);
        columnWeights.set(a, weightB);
        columnWeights.set(b, weightA);
        update(screenWidth, screenHeight);
    }

    public int verticalSplitterAt(double mouseX, double mouseY) {
        for (int i = 0; i < verticalSplitters.size(); i++) {
            if (verticalSplitters.get(i).contains(mouseX, mouseY)) {
                return i;
            }
        }

        return -1;
    }

    public boolean horizontalSplitterAt(double mouseX, double mouseY) {
        return horizontalSplitter.contains(mouseX, mouseY);
    }

    /// 命中面板标题栏的面板
    public @Nullable EditorPanel panelHeaderAt(double mouseX, double mouseY) {
        for (EditorPanel column : columns) {
            if (column.headerRect().contains(mouseX, mouseY)) {
                return column;
            }
        }

        if (bottomPanel != null && bottomPanel.headerRect().contains(mouseX, mouseY)) {
            return bottomPanel;
        }

        return null;
    }

    public @Nullable EditorPanel panelAt(double mouseX, double mouseY) {
        for (EditorPanel column : columns) {
            if (!column.collapsed() && column.contentRect().contains(mouseX, mouseY)) {
                return column;
            }

            if (column.collapsed() && column.rect().contains(mouseX, mouseY)) {
                return column;
            }
        }

        if (bottomPanel != null && bottomPanel.rect().contains(mouseX, mouseY)) {
            return bottomPanel;
        }

        return null;
    }

    /// 面板 -> 矩形，便于调试与外部查询
    public Map<EditorPanel, UiRect> panelRects() {
        Map<EditorPanel, UiRect> map = new LinkedHashMap<>();

        for (EditorPanel column : columns) {
            map.put(column, column.rect());
        }

        if (bottomPanel != null) {
            map.put(bottomPanel, bottomRect);
        }

        return map;
    }
}
