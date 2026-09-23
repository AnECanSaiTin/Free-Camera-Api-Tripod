package cn.anecansaitin.free_camera_api_tripod.core.editor.layout;

import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.EditorPanel;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// 编辑器停靠布局。
///
/// 结构分三层：上排与下排由一条横向分隔条分开；每一排横向切成若干「单元」；
/// 每个单元内部纵向叠放若干面板。三种分隔条都可拖拽：排之间的横向分隔条调整下排高度、
/// 单元之间的竖向分隔条调整单元宽度、单元内的横向分隔条调整相邻两个面板的高度。
///
/// 面板标题栏可以拖到任意单元的左侧、右侧、上方或下方重新停靠，拖出停靠区则成为浮动面板。
/// 浮动面板有自己的位置与尺寸，绘制在所有停靠面板之上，拖回停靠区即可再次停靠。
public final class DockLayout {
    /// 顶部文件菜单栏高度
    public static final int FILE_BAR_HEIGHT = 18;
    /// 分隔条厚度
    public static final int SPLITTER_SIZE = 4;
    /// 浮动面板最小宽度
    public static final int MIN_FLOATING_WIDTH = 120;
    /// 浮动面板最小高度
    public static final int MIN_FLOATING_HEIGHT = 60;
    /// 重新打开被关掉的面板时使用的默认尺寸
    private static final int DEFAULT_FLOATING_WIDTH = 360;
    private static final int DEFAULT_FLOATING_HEIGHT = 300;
    /// 连续重新打开多个面板时每个窗口错开的距离
    private static final int FLOATING_CASCADE = 16;
    /// 布局串版本号。
    ///
    /// 结构或默认布局发生变化时递增，旧版本保存的串会因为版本不符而整体作废，
    /// 从而自动回落到新的默认布局，避免旧布局一直压着新设计。
    private static final int LAYOUT_VERSION = 2;

    private static final int MIN_CELL_WIDTH = 110;
    private static final int MIN_PANEL_HEIGHT = 30;
    private static final int MIN_TOP_HEIGHT = 80;
    private static final int MIN_BOTTOM_HEIGHT = 70;
    private static final float DEFAULT_BOTTOM_WEIGHT = 0.28f;

    /// 面板所在的排
    public enum Row { TOP, BOTTOM }

    /// 面板落在参照面板的哪一侧
    public enum Side { LEFT, RIGHT, ABOVE, BELOW }

    /// 拖拽落点。
    ///
    /// 只记录参照面板与方向，真正提交时再按参照面板反查位置，
    /// 这样拖拽过程中布局即使已经变化也不会插错地方。
    /// 参照面板为空表示停靠区里已经一个面板都不剩，直接新建一个单元收下它。
    public record DropTarget(Row row, @Nullable EditorPanel reference, Side side, UiRect indicator) {
    }

    /// 布局单元：排内横向的一格，内部按权重纵向叠放面板
    private static final class Cell {
        private final List<EditorPanel> panels = new ArrayList<>();
        private final List<Float> weights = new ArrayList<>();
        /// 该单元在所在排里的横向权重
        private float weight = 1f;
        private UiRect rect = new UiRect(0, 0, 0, 0);
    }

    /// 单元之间的竖向分隔条
    private record CellSplitter(UiRect rect, boolean bottom, int index) {
    }

    /// 单元内上下两个面板之间的横向分隔条
    private record StackSplitter(UiRect rect, Cell cell, int index) {
    }

    private final List<Cell> topCells = new ArrayList<>();
    private final List<Cell> bottomCells = new ArrayList<>();
    /// 所有受管面板，用于布局还原时校验面板集合是否匹配
    private final Set<EditorPanel> knownPanels = new LinkedHashSet<>();
    /// 浮动面板，顺序即层级，末尾在最上层
    private final List<EditorPanel> floatingPanels = new ArrayList<>();
    /// 初始结构的快照，供 resetLayout 恢复
    private @Nullable String defaultLayout;

    private float bottomWeight = DEFAULT_BOTTOM_WEIGHT;
    private boolean hasBottomRow;

    private final List<EditorPanel> docked = new ArrayList<>();
    private final List<CellSplitter> cellSplitters = new ArrayList<>();
    private final List<UiRect> verticalSplitters = new ArrayList<>();
    private final List<StackSplitter> stackSplitters = new ArrayList<>();
    private final List<UiRect> stackSplitterRects = new ArrayList<>();
    private UiRect horizontalSplitter = new UiRect(0, 0, 0, 0);
    private UiRect fileBarRect = new UiRect(0, 0, 0, 0);
    private UiRect topRowRect = new UiRect(0, 0, 0, 0);
    private UiRect bottomRowRect = new UiRect(0, 0, 0, 0);
    private int screenWidth;
    private int screenHeight;

    // region 初始结构

    /// 新增一整列（单元），权重为它在排内的横向占比
    public void addColumn(EditorPanel panel, float weight) {
        Cell cell = new Cell();
        cell.panels.add(panel);
        cell.weights.add(1f);
        cell.weight = weight;
        topCells.add(cell);
        knownPanels.add(panel);
    }

    /// 设置下排面板，默认只占一格
    public void bottom(EditorPanel panel) {
        Cell cell = new Cell();
        cell.panels.add(panel);
        cell.weights.add(1f);
        bottomCells.add(cell);
        knownPanels.add(panel);
    }

    /// 把面板叠放到参照面板所在单元的末尾，用于构造「同一列里上下排列」的默认布局
    public void stackUnder(EditorPanel reference, EditorPanel panel, float weight) {
        Cell cell = cellContaining(reference);

        if (cell == null) {
            addColumn(panel, 1f);
            return;
        }

        cell.panels.add(panel);
        cell.weights.add(Math.max(0.05f, weight));
        knownPanels.add(panel);
    }

    /// 把当前结构记为默认布局，供 resetLayout 恢复；应在初始面板注册完成后调用
    public void captureDefaults() {
        defaultLayout = serialize();
    }

    /// 恢复默认布局：结构、比例与浮动状态全部回到初始状态
    public void resetLayout() {
        if (defaultLayout != null) {
            restore(defaultLayout);
        }

        bottomWeight = DEFAULT_BOTTOM_WEIGHT;
    }

    // endregion

    // region 持久化

    /// 序列化整个布局，格式见 {@code EditorConfig.DEFAULT_LAYOUT}
    public String serialize() {
        StringBuilder builder = new StringBuilder();
        builder.append("v=").append(LAYOUT_VERSION);
        builder.append(";top=").append(serializeRow(topCells));
        builder.append(";bottom=").append(serializeRow(bottomCells));
        builder.append(";float=");

        for (int i = 0; i < floatingPanels.size(); i++) {
            EditorPanel panel = floatingPanels.get(i);

            if (i > 0) {
                builder.append(',');
            }

            UiRect rect = panel.floatingRect();
            builder.append(panel.id()).append(':').append(rect.x()).append(':').append(rect.y())
                    .append(':').append(rect.width()).append(':').append(rect.height());
        }

        return builder.toString();
    }

    private static String serializeRow(List<Cell> row) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < row.size(); i++) {
            Cell cell = row.get(i);

            if (i > 0) {
                builder.append('|');
            }

            builder.append(weight(cell.weight)).append(':');

            for (int j = 0; j < cell.panels.size(); j++) {
                if (j > 0) {
                    builder.append('+');
                }

                builder.append(cell.panels.get(j).id()).append(':').append(weight(cell.weights.get(j)));
            }
        }

        return builder.toString();
    }

    private static String weight(float value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    /// 按保存的布局恢复面板的停靠位置、叠放顺序、权重与浮动窗口。
    ///
    /// 只要面板集合对不上或格式损坏就整体放弃并返回 false，让调用方沿用默认布局。
    public boolean restore(String saved) {
        if (saved == null || saved.isBlank()) {
            return false;
        }

        Map<String, EditorPanel> byId = new HashMap<>();

        for (EditorPanel panel : knownPanels) {
            byId.put(panel.id(), panel);
        }

        Set<EditorPanel> seen = new HashSet<>();
        List<Cell> top = new ArrayList<>();
        List<Cell> bottom = new ArrayList<>();
        Map<EditorPanel, UiRect> floats = new LinkedHashMap<>();
        boolean versionMatched = false;

        for (String section : saved.split(";")) {
            if (section.isBlank()) {
                continue;
            }

            int separator = section.indexOf('=');

            if (separator <= 0) {
                return false;
            }

            String value = section.substring(separator + 1).trim();

            switch (section.substring(0, separator).trim()) {
                case "v" -> {
                    // 版本不符说明默认布局已经改过，直接作废让调用方用新默认布局
                    if (!value.equals(String.valueOf(LAYOUT_VERSION))) {
                        return false;
                    }

                    versionMatched = true;
                }
                case "top" -> {
                    List<Cell> parsed = parseRow(value, byId, seen);

                    if (parsed == null) {
                        return false;
                    }

                    top = parsed;
                }
                case "bottom" -> {
                    List<Cell> parsed = parseRow(value, byId, seen);

                    if (parsed == null) {
                        return false;
                    }

                    bottom = parsed;
                }
                case "float" -> {
                    if (!parseFloating(value, byId, seen, floats)) {
                        return false;
                    }
                }
                default -> {
                }
            }
        }

        if (!versionMatched || seen.size() != knownPanels.size()) {
            return false;
        }

        topCells.clear();
        topCells.addAll(top);
        bottomCells.clear();
        bottomCells.addAll(bottom);
        floatingPanels.clear();

        for (Map.Entry<EditorPanel, UiRect> entry : floats.entrySet()) {
            entry.getKey().floatingRect(entry.getValue());
            floatingPanels.add(entry.getKey());
        }

        syncFloating();
        return true;
    }

    private static @Nullable List<Cell> parseRow(String value, Map<String, EditorPanel> byId, Set<EditorPanel> seen) {
        List<Cell> row = new ArrayList<>();

        if (value.isBlank()) {
            return row;
        }

        for (String cellText : value.split("\\|")) {
            int separator = cellText.indexOf(':');

            if (separator <= 0) {
                return null;
            }

            float cellWeight = parseWeight(cellText.substring(0, separator));

            if (Float.isNaN(cellWeight)) {
                return null;
            }

            Cell cell = new Cell();
            cell.weight = cellWeight > 0 ? cellWeight : 1f;

            for (String panelText : cellText.substring(separator + 1).split("\\+")) {
                int weightAt = panelText.lastIndexOf(':');
                String id = weightAt > 0 ? panelText.substring(0, weightAt) : panelText;
                float panelWeight = weightAt > 0 ? parseWeight(panelText.substring(weightAt + 1)) : 1f;

                if (Float.isNaN(panelWeight)) {
                    return null;
                }

                EditorPanel panel = byId.get(id.trim());

                if (panel == null || !seen.add(panel)) {
                    return null;
                }

                cell.panels.add(panel);
                cell.weights.add(panelWeight > 0 ? panelWeight : 1f);
            }

            if (cell.panels.isEmpty()) {
                return null;
            }

            row.add(cell);
        }

        return row;
    }

    private static boolean parseFloating(String value, Map<String, EditorPanel> byId, Set<EditorPanel> seen,
                                         Map<EditorPanel, UiRect> out) {
        if (value.isBlank()) {
            return true;
        }

        for (String entry : value.split(",")) {
            String[] parts = entry.split(":");

            if (parts.length != 5) {
                return false;
            }

            EditorPanel panel = byId.get(parts[0].trim());

            if (panel == null || !seen.add(panel)) {
                return false;
            }

            int[] numbers = new int[4];

            for (int i = 0; i < numbers.length; i++) {
                try {
                    numbers[i] = Integer.parseInt(parts[i + 1].trim());
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            out.put(panel, new UiRect(numbers[0], numbers[1], numbers[2], numbers[3]));
        }

        return true;
    }

    private static float parseWeight(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    /// 让面板的浮动标记与浮动列表保持一致
    private void syncFloating() {
        for (EditorPanel panel : knownPanels) {
            panel.floating(floatingPanels.contains(panel));
        }
    }

    // endregion

    // region 布局计算

    /// 重新计算所有面板矩形与分隔条矩形
    public void update(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        fileBarRect = new UiRect(0, 0, width, FILE_BAR_HEIGHT);

        int contentY = FILE_BAR_HEIGHT;
        int contentHeight = Math.max(0, height - FILE_BAR_HEIGHT);
        hasBottomRow = !bottomCells.isEmpty();

        // 整排折叠时只保留标题栏高度，把空间让给上排
        int bottomHeight;

        if (!hasBottomRow) {
            bottomHeight = 0;
        } else if (isRowCollapsed(bottomCells)) {
            bottomHeight = EditorPanel.HEADER_HEIGHT;
        } else {
            bottomHeight = Mth.clamp(Math.round(contentHeight * bottomWeight), MIN_BOTTOM_HEIGHT,
                    Math.max(MIN_BOTTOM_HEIGHT, contentHeight - MIN_TOP_HEIGHT - SPLITTER_SIZE));
        }

        // 上排要为两排之间的分隔条让出厚度；只有下排时才需要
        int reserved = hasBottomRow ? SPLITTER_SIZE : 0;
        int topHeight = Math.max(0, contentHeight - bottomHeight - reserved);

        cellSplitters.clear();
        verticalSplitters.clear();
        stackSplitters.clear();
        stackSplitterRects.clear();
        docked.clear();

        topRowRect = new UiRect(0, contentY, width, topHeight);
        layoutRow(topCells, topRowRect, false);

        if (hasBottomRow) {
            horizontalSplitter = new UiRect(0, contentY + topHeight, width, SPLITTER_SIZE);
            bottomRowRect = new UiRect(0, horizontalSplitter.bottom(), width, bottomHeight);
            layoutRow(bottomCells, bottomRowRect, true);
        } else {
            horizontalSplitter = new UiRect(0, contentY + topHeight, width, 0);
            bottomRowRect = new UiRect(0, contentY + topHeight, width, 0);
        }

        layoutFloating();
    }

    private static boolean isRowCollapsed(List<Cell> row) {
        boolean any = false;

        for (Cell cell : row) {
            for (EditorPanel panel : cell.panels) {
                any = true;

                if (!panel.collapsed()) {
                    return false;
                }
            }
        }

        return any;
    }

    /// 布局一排：先把排横向切成单元，再让单元内部纵向叠放面板
    private void layoutRow(List<Cell> row, UiRect rowRect, boolean bottom) {
        if (row.isEmpty()) {
            return;
        }

        normalizeWeights(row);
        int count = row.size();
        int available = rowRect.width() - (count - 1) * SPLITTER_SIZE;
        int used = 0;
        int x = rowRect.x();

        for (int i = 0; i < count; i++) {
            Cell cell = row.get(i);
            int cellWidth = i == count - 1 ? available - used : Math.round(cell.weight * available);
            cellWidth = Math.max(0, cellWidth);
            used += cellWidth;
            cell.rect = new UiRect(x, rowRect.y(), cellWidth, rowRect.height());
            layoutCell(cell);
            x += cellWidth;

            if (i < count - 1) {
                UiRect splitter = new UiRect(x, rowRect.y(), SPLITTER_SIZE, rowRect.height());
                cellSplitters.add(new CellSplitter(splitter, bottom, i));
                verticalSplitters.add(splitter);
                x += SPLITTER_SIZE;
            }
        }
    }

    /// 单元内部：折叠的面板只占标题栏，展开的面板按权重瓜分剩余高度
    private void layoutCell(Cell cell) {
        int count = cell.panels.size();

        if (count == 0) {
            return;
        }

        int available = stackAvailable(cell);
        float sum = stackWeightSum(cell);
        int lastExpanded = -1;

        for (int i = count - 1; i >= 0; i--) {
            if (!cell.panels.get(i).collapsed()) {
                lastExpanded = i;
                break;
            }
        }

        int y = cell.rect.y();
        int left = available;

        for (int i = 0; i < count; i++) {
            EditorPanel panel = cell.panels.get(i);
            int height;

            if (panel.collapsed()) {
                height = EditorPanel.HEADER_HEIGHT;
            } else {
                // 最后一个展开的面板吃掉余数，避免取整误差累积出空隙
                height = i == lastExpanded ? Math.max(0, left)
                        : Math.min(left, Math.round(available * Math.max(0.001f, cell.weights.get(i)) / sum));
                left -= height;
            }

            panel.rect(new UiRect(cell.rect.x(), y, cell.rect.width(), height));
            docked.add(panel);
            y += height;

            // 相邻两个展开的面板之间留出可拖拽的分隔条
            if (i + 1 < count && !panel.collapsed() && !cell.panels.get(i + 1).collapsed()) {
                UiRect rect = new UiRect(cell.rect.x(), y, cell.rect.width(), SPLITTER_SIZE);
                stackSplitters.add(new StackSplitter(rect, cell, i));
                stackSplitterRects.add(rect);
                y += SPLITTER_SIZE;
            }
        }
    }

    /// 单元内可分配给展开面板的总高度（扣掉折叠面板与分隔条）
    private static int stackAvailable(Cell cell) {
        int count = cell.panels.size();
        int collapsedHeight = 0;
        int adjacentPairs = 0;

        for (int i = 0; i < count; i++) {
            if (cell.panels.get(i).collapsed()) {
                collapsedHeight += EditorPanel.HEADER_HEIGHT;
            }

            if (i + 1 < count && !cell.panels.get(i).collapsed() && !cell.panels.get(i + 1).collapsed()) {
                adjacentPairs++;
            }
        }

        return Math.max(0, cell.rect.height() - collapsedHeight - adjacentPairs * SPLITTER_SIZE);
    }

    private static float stackWeightSum(Cell cell) {
        float sum = 0;

        for (int i = 0; i < cell.panels.size(); i++) {
            if (!cell.panels.get(i).collapsed()) {
                sum += Math.max(0.001f, cell.weights.get(i));
            }
        }

        return sum;
    }

    private static void normalizeWeights(List<Cell> row) {
        float sum = 0;

        for (Cell cell : row) {
            sum += cell.weight;
        }

        if (sum <= 0) {
            return;
        }

        for (Cell cell : row) {
            cell.weight /= sum;
        }
    }

    /// 浮动面板：收敛到屏幕内，且不遮住顶部文件栏
    private void layoutFloating() {
        for (EditorPanel panel : floatingPanels) {
            UiRect rect = panel.floatingRect();

            if (screenWidth > 0 && screenHeight > 0) {
                int width = Mth.clamp(rect.width(), MIN_FLOATING_WIDTH, Math.max(MIN_FLOATING_WIDTH, screenWidth));
                int height = Mth.clamp(rect.height(), MIN_FLOATING_HEIGHT, Math.max(MIN_FLOATING_HEIGHT, screenHeight));
                int x = Mth.clamp(rect.x(), 0, Math.max(0, screenWidth - width));
                int y = Mth.clamp(rect.y(), FILE_BAR_HEIGHT, Math.max(FILE_BAR_HEIGHT, screenHeight - height));
                rect = new UiRect(x, y, width, height);
                panel.floatingRect(rect);
            }

            panel.rect(panel.collapsed()
                    ? new UiRect(rect.x(), rect.y(), rect.width(), EditorPanel.HEADER_HEIGHT)
                    : rect);
        }
    }

    // endregion

    // region 拖拽调整

    /// 拖拽竖向分隔条：在相邻两个单元之间重新分配宽度
    public boolean resizeVertical(int splitterIndex, int mouseX) {
        if (splitterIndex < 0 || splitterIndex >= cellSplitters.size()) {
            return false;
        }

        CellSplitter splitter = cellSplitters.get(splitterIndex);
        List<Cell> row = splitter.bottom() ? bottomCells : topCells;
        UiRect rowRect = splitter.bottom() ? bottomRowRect : topRowRect;
        int index = splitter.index();

        if (index < 0 || index + 1 >= row.size()) {
            return false;
        }

        int available = rowRect.width() - (row.size() - 1) * SPLITTER_SIZE;

        if (available <= 0) {
            return false;
        }

        normalizeWeights(row);
        Cell left = row.get(index);
        Cell right = row.get(index + 1);
        float pairWeight = left.weight + right.weight;
        int pairWidth = Math.round(pairWeight * available);
        int desired = mouseX - left.rect.x() - SPLITTER_SIZE / 2;
        desired = Mth.clamp(desired, MIN_CELL_WIDTH, Math.max(MIN_CELL_WIDTH, pairWidth - MIN_CELL_WIDTH));

        float newLeft = (float) desired / available;
        left.weight = newLeft;
        right.weight = pairWeight - newLeft;
        return true;
    }

    /// 拖拽排之间的横向分隔条：调整下排高度
    public boolean resizeHorizontal(int mouseY) {
        int contentY = FILE_BAR_HEIGHT;
        int contentHeight = Math.max(1, screenHeight - FILE_BAR_HEIGHT);
        int topHeight = mouseY - contentY - SPLITTER_SIZE / 2;
        topHeight = Mth.clamp(topHeight, MIN_TOP_HEIGHT, Math.max(MIN_TOP_HEIGHT, contentHeight - MIN_BOTTOM_HEIGHT - SPLITTER_SIZE));
        bottomWeight = Mth.clamp((float) (contentHeight - SPLITTER_SIZE - topHeight) / contentHeight, 0f, 1f);
        return true;
    }

    /// 拖拽单元内的横向分隔条：调整上下两个面板的高度
    public boolean resizeStack(int splitterIndex, int mouseY) {
        if (splitterIndex < 0 || splitterIndex >= stackSplitters.size()) {
            return false;
        }

        StackSplitter splitter = stackSplitters.get(splitterIndex);
        Cell cell = splitter.cell();
        int index = splitter.index();

        if (index < 0 || index + 1 >= cell.panels.size()) {
            return false;
        }

        int available = stackAvailable(cell);
        float sum = stackWeightSum(cell);

        if (available <= 0 || sum <= 0) {
            return false;
        }

        float upper = Math.max(0.001f, cell.weights.get(index));
        float lower = Math.max(0.001f, cell.weights.get(index + 1));
        float scale = available / sum;
        int pairHeight = Math.round((upper + lower) * scale);
        int desired = mouseY - cell.panels.get(index).rect().y() - SPLITTER_SIZE / 2;
        desired = Mth.clamp(desired, MIN_PANEL_HEIGHT, Math.max(MIN_PANEL_HEIGHT, pairHeight - MIN_PANEL_HEIGHT));

        float newUpper = desired / scale;
        cell.weights.set(index, newUpper);
        cell.weights.set(index + 1, upper + lower - newUpper);
        return true;
    }

    // endregion

    // region 拖拽提交

    /// 判定拖拽落点：在停靠区内返回具体插入位置，之外返回 null 表示不放回停靠区
    public @Nullable DropTarget dropTargetAt(double mouseX, double mouseY) {
        int fallbackTop = bottomFallbackTop();

        // 下排被拖空后布局里已经没有下排，此时屏幕底部仍留一条落点，
        // 否则时间轴一旦被拖出去就再也拖不回底部
        if (bottomCells.isEmpty() && !topCells.isEmpty() && mouseY >= fallbackTop) {
            return new DropTarget(Row.BOTTOM, null, Side.ABOVE, new UiRect(0, fallbackTop, screenWidth, SPLITTER_SIZE));
        }

        double topRowBottom = topRowRect.bottom() + (hasBottomRow ? SPLITTER_SIZE : 0);

        if (!topCells.isEmpty() && mouseY >= topRowRect.y() && mouseY < topRowBottom) {
            return targetInRow(Row.TOP, topCells, topRowRect, mouseX, mouseY);
        }

        if (hasBottomRow && mouseY >= bottomRowRect.y() && mouseY < bottomRowRect.bottom()) {
            return targetInRow(Row.BOTTOM, bottomCells, bottomRowRect, mouseX, mouseY);
        }

        // 面板全被拖成浮动窗口后停靠区空无一物，仍要留一个落点让它们能停靠回来
        if (topCells.isEmpty() && !hasBottomRow && mouseY >= FILE_BAR_HEIGHT) {
            int x = Mth.clamp((int) mouseX - SPLITTER_SIZE / 2, 0, Math.max(0, screenWidth - SPLITTER_SIZE));
            return new DropTarget(Row.TOP, null, Side.ABOVE, new UiRect(x, FILE_BAR_HEIGHT, SPLITTER_SIZE, Math.max(0, bottomRowRect.y() - FILE_BAR_HEIGHT)));
        }

        return null;
    }

    /// 下排缺席时，GUI 最底部留出的下排落点高度。
    ///
    /// 只留薄薄一条：面板拖到屏幕下半部分不会被动变成下排（避免误触），
    /// 但拖到最底边仍然能一眼看懂并轻松停靠回去。
    private static final int BOTTOM_DROP_BAND = 24;

    /// 下排缺席时，屏幕最底边往下这一段仍算下排落点
    private int bottomFallbackTop() {
        return Math.max(FILE_BAR_HEIGHT, screenHeight - BOTTOM_DROP_BAND);
    }

    private static DropTarget targetInRow(Row row, List<Cell> cells, UiRect rowRect, double mouseX, double mouseY) {
        Cell cell = cells.get(cellIndexAt(cells, mouseX));
        UiRect rect = cell.rect;
        int edge = Mth.clamp(rect.width() / 4, 4, 16);

        // 贴近左右两侧：拆出一个新的并列单元
        if (mouseX < rect.x() + edge) {
            return new DropTarget(row, cell.panels.get(0), Side.LEFT,
                    new UiRect(Math.max(0, rect.x() - SPLITTER_SIZE), rowRect.y(), SPLITTER_SIZE, rowRect.height()));
        }

        if (mouseX >= rect.right() - edge) {
            return new DropTarget(row, cell.panels.get(0), Side.RIGHT,
                    new UiRect(rect.right(), rowRect.y(), SPLITTER_SIZE, rowRect.height()));
        }

        // 其余区域：按鼠标在命中面板的上下半区，插入到单元内的叠放顺序中
        EditorPanel hovered = panelAt(cell, mouseY);
        UiRect hoveredRect = hovered.rect();
        boolean above = mouseY < hoveredRect.y() + hoveredRect.height() / 2.0;
        int y = above ? hoveredRect.y() : hoveredRect.bottom();
        UiRect indicator = new UiRect(rect.x(), Math.max(rowRect.y(), y - SPLITTER_SIZE / 2), rect.width(), SPLITTER_SIZE);
        return new DropTarget(row, hovered, above ? Side.ABOVE : Side.BELOW, indicator);
    }

    /// 鼠标 x 所在的单元；落在分隔条上时取左边的一格
    private static int cellIndexAt(List<Cell> row, double mouseX) {
        for (int i = 0; i < row.size(); i++) {
            if (mouseX < row.get(i).rect.right()) {
                return i;
            }
        }

        return row.size() - 1;
    }

    /// 单元内鼠标 y 命中的面板；落在空隙里时取最接近的一个
    private static EditorPanel panelAt(Cell cell, double mouseY) {
        for (EditorPanel panel : cell.panels) {
            if (mouseY < panel.rect().bottom()) {
                return panel;
            }
        }

        return cell.panels.get(cell.panels.size() - 1);
    }

    /// 提交一次拖拽：把面板停靠到落点，落点为 null 时改为浮动面板
    public void applyDrop(EditorPanel panel, @Nullable DropTarget target, UiRect floatingRect) {
        if (target == null) {
            detach(panel);
            floatingPanels.add(panel);
            panel.floatingRect(floatingRect);
            syncFloating();
            update(screenWidth, screenHeight);
            return;
        }

        // 落点就是自己：位置没有变化
        if (target.reference() == panel) {
            update(screenWidth, screenHeight);
            return;
        }

        List<Cell> row = target.row() == Row.BOTTOM ? bottomCells : topCells;
        Cell referenceCell = cellContaining(target.reference(), row);

        if (referenceCell == null) {
            // 参照面板已经不在这一排（极端情况），退化为追加到末尾
            detach(panel);
            Cell cell = new Cell();
            cell.panels.add(panel);
            cell.weights.add(1f);
            row.add(cell);
            syncFloating();
            update(screenWidth, screenHeight);
            return;
        }

        Cell originCell = cellContaining(panel);
        int originIndex = originCell == null ? -1 : originCell.panels.indexOf(panel);
        List<Cell> originRow = originCell == null ? null : rowOf(originCell);
        // 摘除面板后原单元是否会被清掉，决定插入位置要不要前移一格
        boolean originCellRemoved = originCell != null && originCell.panels.size() == 1;
        int referenceIndex = referenceCell.panels.indexOf(target.reference());

        if (target.side() == Side.LEFT || target.side() == Side.RIGHT) {
            // 拆出新的并列单元，插到参照单元的前面或后面
            int insertAt = row.indexOf(referenceCell) + (target.side() == Side.RIGHT ? 1 : 0);

            if (originRow == row && originCellRemoved && row.indexOf(originCell) < insertAt) {
                insertAt--;
            }

            detach(panel);
            Cell cell = new Cell();
            cell.weight = Math.max(0.05f, referenceCell.weight);
            cell.panels.add(panel);
            cell.weights.add(1f);
            row.add(insertAt, cell);
        } else {
            int insertIndex = referenceIndex + (target.side() == Side.BELOW ? 1 : 0);

            if (referenceCell == originCell && originIndex >= 0 && originIndex < insertIndex) {
                insertIndex--;
            }

            if (referenceCell == originCell && insertIndex == originIndex) {
                update(screenWidth, screenHeight);
                return;
            }

            float weight = Math.max(0.05f, referenceCell.weights.get(referenceIndex));
            detach(panel);
            referenceCell.panels.add(insertIndex, panel);
            referenceCell.weights.add(insertIndex, weight);
        }

        syncFloating();
        update(screenWidth, screenHeight);
    }

    /// 把面板从停靠结构与浮动列表中摘除，空掉的单元一并清理
    private void detach(EditorPanel panel) {
        floatingPanels.remove(panel);
        removeFrom(topCells, panel);
        removeFrom(bottomCells, panel);
    }

    private static void removeFrom(List<Cell> row, EditorPanel panel) {
        for (int i = 0; i < row.size(); i++) {
            Cell cell = row.get(i);
            int index = cell.panels.indexOf(panel);

            if (index < 0) {
                continue;
            }

            cell.panels.remove(index);
            cell.weights.remove(index);

            if (cell.panels.isEmpty()) {
                row.remove(i);
            }

            return;
        }
    }

    private @Nullable Cell cellContaining(EditorPanel panel) {
        Cell cell = cellContaining(panel, topCells);
        return cell != null ? cell : cellContaining(panel, bottomCells);
    }

    private static @Nullable Cell cellContaining(EditorPanel panel, List<Cell> row) {
        for (Cell cell : row) {
            if (cell.panels.contains(panel)) {
                return cell;
            }
        }

        return null;
    }

    private @Nullable List<Cell> rowOf(Cell cell) {
        if (topCells.contains(cell)) {
            return topCells;
        }

        return bottomCells.contains(cell) ? bottomCells : null;
    }

    // endregion

    // region 浮动面板

    /// 把浮动面板提到最上层
    public void raiseFloating(EditorPanel panel) {
        if (floatingPanels.remove(panel)) {
            floatingPanels.add(panel);
        }
    }

    /// 移动浮动面板（拖标题栏）
    public void moveFloating(EditorPanel panel, int x, int y) {
        if (!floatingPanels.contains(panel)) {
            return;
        }

        UiRect rect = panel.floatingRect();
        panel.floatingRect(new UiRect(x, y, rect.width(), rect.height()));
        update(screenWidth, screenHeight);
    }

    /// 调整浮动面板尺寸（拖右下角）
    public void resizeFloating(EditorPanel panel, int width, int height) {
        if (!floatingPanels.contains(panel)) {
            return;
        }

        UiRect rect = panel.floatingRect();
        panel.floatingRect(new UiRect(rect.x(), rect.y(), width, height));
        update(screenWidth, screenHeight);
    }

    // endregion

    // region 查询

    public float bottomWeight() {
        return bottomWeight;
    }

    public void bottomWeight(float bottomWeight) {
        this.bottomWeight = Mth.clamp(bottomWeight, 0f, 1f);
    }

    /// 所有停靠面板，按绘制顺序
    public List<EditorPanel> dockedPanels() {
        return docked;
    }

    /// 浮动面板，顺序即层级，末尾在最上层
    public List<EditorPanel> floatingPanels() {
        return floatingPanels;
    }

    public boolean hasBottomRow() {
        return hasBottomRow;
    }

    public UiRect fileBarRect() {
        return fileBarRect;
    }

    /// 把面板转成悬浮窗口：脱离停靠结构，位置与尺寸取传入的矩形
    public void floatPanel(EditorPanel panel, UiRect rect) {
        applyDrop(panel, null, rect);
    }

    /// 关闭面板：直接从布局里摘掉（停靠与悬浮都不再包含它）。
    /// 参照 Photoshop 的做法，关掉的窗口不占布局空间；再次打开时以浮动窗口形式回来。
    public void closePanel(EditorPanel panel) {
        detach(panel);
        syncFloating();
        update(screenWidth, screenHeight);
    }

    /// 面板是否还在布局里（停靠着或作为浮动窗口都算）
    public boolean containsPanel(EditorPanel panel) {
        return cellContaining(panel) != null || floatingPanels.contains(panel);
    }

    /// 把面板作为浮动窗口放回布局，位置取屏幕中间偏上的默认位置
    public void openFloating(EditorPanel panel) {
        if (containsPanel(panel)) {
            return;
        }

        int width = Math.max(MIN_FLOATING_WIDTH, Math.min(DEFAULT_FLOATING_WIDTH, screenWidth / 3));
        int height = Math.max(MIN_FLOATING_HEIGHT, Math.min(DEFAULT_FLOATING_HEIGHT, screenHeight / 3));
        int x = Math.max(0, (screenWidth - width) / 2);
        int y = Math.max(FILE_BAR_HEIGHT, (screenHeight - height) / 2);
        // 位置逐次错开一点，连续打开多个窗口时不会完全重叠
        int offset = floatingPanels.size() * FLOATING_CASCADE;
        floatingPanels.add(panel);
        panel.floatingRect(new UiRect(x + offset, y + offset, width, height));
        syncFloating();
        update(screenWidth, screenHeight);
    }

    /// 取消悬浮，恢复为固定面板：重新参与界面排列，停靠在上排末尾
    public void dockPanel(EditorPanel panel) {
        if (!floatingPanels.contains(panel)) {
            return;
        }

        floatingPanels.remove(panel);
        Cell cell = new Cell();
        cell.weight = 1f;
        cell.panels.add(panel);
        cell.weights.add(1f);
        topCells.add(cell);
        syncFloating();
        update(screenWidth, screenHeight);
    }

    public List<UiRect> verticalSplitters() {
        return verticalSplitters;
    }

    /// 单元内上下两个面板之间的分隔条
    public List<UiRect> stackSplitterRects() {
        return stackSplitterRects;
    }

    public UiRect horizontalSplitter() {
        return horizontalSplitter;
    }

    public int verticalSplitterAt(double mouseX, double mouseY) {
        for (int i = 0; i < verticalSplitters.size(); i++) {
            if (verticalSplitters.get(i).contains(mouseX, mouseY)) {
                return i;
            }
        }

        return -1;
    }

    public int stackSplitterAt(double mouseX, double mouseY) {
        for (int i = 0; i < stackSplitterRects.size(); i++) {
            if (stackSplitterRects.get(i).contains(mouseX, mouseY)) {
                return i;
            }
        }

        return -1;
    }

    public boolean horizontalSplitterAt(double mouseX, double mouseY) {
        return hasBottomRow && horizontalSplitter.contains(mouseX, mouseY);
    }

    /// 命中停靠面板的标题栏
    public @Nullable EditorPanel panelHeaderAt(double mouseX, double mouseY) {
        for (EditorPanel panel : docked) {
            if (panel.headerRect().contains(mouseX, mouseY)) {
                return panel;
            }
        }

        return null;
    }

    /// 命中停靠面板的内容区（折叠后整块都算内容）
    public @Nullable EditorPanel panelAt(double mouseX, double mouseY) {
        for (EditorPanel panel : docked) {
            UiRect rect = panel.collapsed() ? panel.rect() : panel.contentRect();

            if (rect.contains(mouseX, mouseY)) {
                return panel;
            }
        }

        return null;
    }

    /// 命中最上层的浮动面板
    public @Nullable EditorPanel floatingPanelAt(double mouseX, double mouseY) {
        for (int i = floatingPanels.size() - 1; i >= 0; i--) {
            EditorPanel panel = floatingPanels.get(i);

            if (panel.rect().contains(mouseX, mouseY)) {
                return panel;
            }
        }

        return null;
    }

    /// 面板 -> 矩形，便于调试与外部查询
    public Map<EditorPanel, UiRect> panelRects() {
        Map<EditorPanel, UiRect> map = new LinkedHashMap<>();

        for (EditorPanel panel : docked) {
            map.put(panel, panel.rect());
        }

        for (EditorPanel panel : floatingPanels) {
            map.put(panel, panel.rect());
        }

        return map;
    }

    // endregion
}
