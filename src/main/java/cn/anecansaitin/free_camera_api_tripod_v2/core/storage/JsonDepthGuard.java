package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import org.jspecify.annotations.NullMarked;

/// 在 JSON 解析前扫描容器嵌套深度的保护器。
///
/// Gson 的递归解析器在遇到极深输入时可能先触发栈溢出，因此不能只依赖解析后的树深度；
/// 本类按字符扫描 `{` / `[`，跳过字符串与转义，深度超过上限立即返回，不构造任何对象。
@NullMarked
final class JsonDepthGuard {

    private JsonDepthGuard() {
    }

    /// 输入是否超过最大容器深度。
    ///
    /// @param json    原始 JSON 文本
    /// @param maxDepth 允许的最大嵌套深度，`>= 1`
    /// @return 超过上限时为 true
    static boolean exceeds(String json, int maxDepth) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < json.length(); index++) {
            char current = json.charAt(index);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            switch (current) {
                case '"' -> inString = true;
                case '{', '[' -> {
                    depth++;

                    if (depth > maxDepth) {
                        return true;
                    }
                }
                case '}', ']' -> {
                    if (depth > 0) {
                        depth--;
                    }
                }
                default -> {
                    // 标点之外不改变深度。
                }
            }
        }

        return false;
    }
}
