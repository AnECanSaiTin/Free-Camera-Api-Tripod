package cn.anecansaitin.free_camera_api_tripod.util;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.neoforged.neoforge.server.command.EnumArgument;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class CommandBuilder {
    private static class BuilderNode {
        private boolean literal;
        private String name;
        private ArgumentType<?> argumentType;
        private Command<CommandSourceStack> executor;
        private final List<BuilderNode> children = new ArrayList<>();
    }

    /**
     * DSL 中单个参数类型的解析器。
     * <p>
     * 接收 {@code &lt;&gt;} 内部的完整字符串（例如 {@code "int(0,10]"}, {@code "string(word)"}）。
     * 类型键已被预先匹配——此方法仅接收键匹配成功的内容。
     */
    @FunctionalInterface
    public interface TypeParser {
        @Nullable
        ArgumentType<?> parse(String typeContent);
    }

    private final Map<String, Class<? extends Enum<?>>> enumClasses = new HashMap<>();
    private final BuilderNode root = new BuilderNode();
    private final Map<String, TypeParser> parsers = new HashMap<>();
    private Predicate<CommandSourceStack> requirement;

    public CommandBuilder() {
        // 注册内置解析器
        addParser("enum", this::tryParseEnum);
        addParser("string", tc -> parseStringType(splitOptions(tc)));
        addParser("int", tc -> parseIntType(splitOptions(tc)));
        addParser("integer", tc -> parseIntType(splitOptions(tc)));
        addParser("bool", _ -> BoolArgumentType.bool());
        addParser("boolean", _ -> BoolArgumentType.bool());
        addParser("vec3", _ -> Vec3Argument.vec3());
    }

    /**
     * 从 typeContent 字符串中提取类型键。
     * 键是第一个 {@code '('} 或 {@code '<'} 之前的部分。
     * <pre>{@code
     * extractTypeKey("int(0,10]")          → "int"
     * extractTypeKey("string(word)")       → "string"
     * extractTypeKey("enum<Selected.Type>") → "enum"
     * extractTypeKey("vec3")               → "vec3"
     * }</pre>
     */
    public static String extractTypeKey(String typeContent) {
        int parenIdx = typeContent.indexOf('(');
        int angleIdx = typeContent.indexOf('<');
        int splitIdx;
        if (parenIdx < 0 && angleIdx < 0) {
            return typeContent;
        } else if (parenIdx < 0) {
            splitIdx = angleIdx;
        } else if (angleIdx < 0) {
            splitIdx = parenIdx;
        } else {
            splitIdx = Math.min(parenIdx, angleIdx);
        }
        return typeContent.substring(0, splitIdx);
    }

    /**
     * 将 typeContent（如 {@code "string(word)"}）拆分为 {@code ["string", "(word)"]}。
     * 若不存在括号，则第二个元素为空字符串。
     */
    public static String[] splitTypeOptions(String typeContent) {
        int parenIdx = typeContent.indexOf('(');
        if (parenIdx >= 0) {
            return new String[]{typeContent.substring(0, parenIdx), typeContent.substring(parenIdx)};
        }
        return new String[]{typeContent, ""};
    }

    /**
     * 便捷方法：仅提取 typeContent 中的选项部分（括号内的内容）。
     * 等价于 {@code splitTypeOptions(typeContent)[1]}。
     */
    public static String splitOptions(String typeContent) {
        int parenIdx = typeContent.indexOf('(');
        if (parenIdx >= 0) {
            return typeContent.substring(parenIdx);
        }
        return "";
    }

    /**
     * 为指定的类型键注册自定义类型解析器。
     * 重复注册同一键会替换之前的解析器，从而允许覆盖内置解析器。
     *
     * @param typeKey 用于匹配的类型名称，例如 {@code "myType"}
     * @param parser  解析器实现
     */
    public void addParser(String typeKey, TypeParser parser) {
        parsers.put(typeKey, parser);
    }

    /**
     * 注册一个枚举类，用于命令定义。
     * <pre>{@code
     * builder.registerEnumClass("Selected.Type", Selected.Type.class);
     * builder.add("cmd_camera path select type<enum<Selected.Type>>", executes);
     * }</pre>
     */
    public void registerEnumClass(String name, Class<? extends Enum<?>> enumClass) {
        enumClasses.put(name, enumClass);
    }

    /**
     * 设置根命令的权限判断条件。
     */
    public CommandBuilder requires(Predicate<CommandSourceStack> requirement) {
        this.requirement = requirement;
        return this;
    }

    /**
     * 添加一个命令执行器，映射到指定的命令路径模式。
     * <p>
     * 模式格式使用空格分隔的段：
     * <ul>
     *   <li>纯文本 → 文字节点</li>
     *   <li>{@code name<type>} → 参数节点，带指定类型</li>
     * </ul>
     * <p>
     * 支持的命令参数类型：
     * <ul>
     *   <li>{@code string} / {@code string(word)} / {@code string(string)} / {@code string(greedyString)}</li>
     *   <li>{@code int} / {@code integer} / {@code int(min,max]}（带范围与包含/排除边界）</li>
     *   <li>{@code bool} / {@code boolean}</li>
     *   <li>{@code vec3}</li>
     *   <li>{@code enum<ClassName>}</li>
     * </ul>
     * <p>
     * 整数范围写法：
     * <ul>
     *   <li>{@code int(0,10]}  → 0 &lt; 值 ≤ 10（排除下限，包含上限）</li>
     *   <li>{@code int[0,10)}  → 0 ≤ 值 &lt; 10（包含下限，排除上限）</li>
     *   <li>{@code int(-2,]}   → -2 ≤ 值（无上限）</li>
     *   <li>{@code int(,5)}    → 值 &lt; 5（无下限）</li>
     * </ul>
     * <p>
     * 使用示例：
     * <pre>{@code
     * builder.add("cmd_camera path create name<string(word)>", createPath());
     * builder.add("cmd_camera path clear", cleanPath());
     * builder.add("cmd_camera path select type<enum<Selected.Type>>", selectPathNodeWithType());
     * builder.add("cmd_camera path select index<int(0,10]>", selectPathNode());
     * builder.add("cmd_camera path select index<int(-2,]> type<enum<Selected.Type>>", selectPathNodeWithIndexAndType());
     * }</pre>
     */
    public void add(String command, Command<CommandSourceStack> executor) {
        String[] parts = command.split(" ");
        BuilderNode current = root;

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            if (part.contains("<")) {
                // 参数段：name<type> 或 name<type<param>>
                int openIdx = part.indexOf('<');
                String argName = part.substring(0, openIdx);
                String typeContent = part.substring(openIdx + 1, part.length() - 1);
                ArgumentType<?> argType = parseArgumentType(typeContent);
                current = findOrCreateChild(current, argName, false, argType);
            } else {
                // 文字段
                current = findOrCreateChild(current, part, true, null);
            }
        }

        current.executor = executor;
    }

    /**
     * 构建命令树并返回根文字参数构建器。
     * 返回的构建器可通过 {@code dispatcher.register(builder.build())} 注册。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        if (root.children.isEmpty()) {
            throw new IllegalStateException("未注册任何命令");
        }
        if (root.children.size() > 1) {
            throw new IllegalStateException(
                    "注册了多个根命令。所有命令必须共享同一个根文字节点。");
        }

        BuilderNode firstChild = root.children.get(0);
        if (!firstChild.literal) {
            throw new IllegalStateException("根命令必须是文字节点。");
        }

        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(firstChild.name);
        if (requirement != null) {
            builder.requires(requirement);
        }
        if (firstChild.executor != null) {
            builder.executes(firstChild.executor);
        }
        for (BuilderNode child : firstChild.children) {
            builder.then(buildNode(child));
        }
        return builder;
    }

    // ---- 解析器委托 ----

    /**
     * 在解析器字典中查找类型键，并委托给匹配的解析器。
     */
    private ArgumentType<?> parseArgumentType(String typeContent) {
        String key = extractTypeKey(typeContent);
        TypeParser parser = parsers.get(key);
        if (parser != null) {
            return parser.parse(typeContent);
        }
        throw new IllegalArgumentException("未知的参数类型：" + typeContent
                + "。没有为键 \"" + key + "\" 注册解析器。");
    }

    // ---- 内置解析器 ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentType<?> tryParseEnum(String typeContent) {
        // 走到这里时 typeContent 保证以 "enum<" 开头、以 ">" 结尾
        // 因为键 "enum" 已被预先匹配
        String enumStr = typeContent.substring(5, typeContent.length() - 1).trim();
        // 去掉可选的 ".class" 后缀
        if (enumStr.endsWith(".class")) {
            enumStr = enumStr.substring(0, enumStr.length() - 6).trim();
        }

        // 查询已注册的枚举类
        Class<? extends Enum<?>> enumClass = enumClasses.get(enumStr);
        if (enumClass != null) {
            return EnumArgument.enumArgument((Class) enumClass);
        }

        // 兜底：尝试用全限定名通过 Class.forName 加载
        try {
            String fqn = enumStr.replace("$", ".");
            if (!fqn.contains(".")) {
                throw new IllegalArgumentException(
                        "无包名的枚举类无法解析：" + enumStr
                                + "。请先通过 registerEnumClass() 注册。");
            }
            Class<?> clazz = Class.forName(fqn);
            return EnumArgument.enumArgument((Class) clazz);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("找不到枚举类：" + enumStr, e);
        }
    }

    /**
     * 解析字符串子类型：{@code string(word)}、{@code string(string)}、{@code string(greedyString)}。
     * 默认（无选项）为 {@link StringArgumentType#string()}。
     */
    private static ArgumentType<?> parseStringType(String options) {
        if (options.isEmpty()) {
            return StringArgumentType.string();
        }
        // options = "(word)", "(string)", "(greedyString)"
        String subType = options.substring(1, options.length() - 1);
        return switch (subType) {
            case "word" -> StringArgumentType.word();
            case "string" -> StringArgumentType.string();
            case "greedyString" -> StringArgumentType.greedyString();
            default -> throw new IllegalArgumentException(
                    "未知的字符串子类型：" + subType
                            + "。期望值：word、string 或 greedyString。");
        };
    }

    /**
     * 解析整数范围：{@code int(min,max]}。
     * <ul>
     *   <li>{@code (} / {@code [}  — 排除 / 包含下限</li>
     *   <li>{@code )} / {@code ]}  — 排除 / 包含上限</li>
     *   <li>省略值表示无边界，例如 {@code int(-2,]}（下限为 -2，无上限）</li>
     * </ul>
     * 默认（无选项）为 {@link IntegerArgumentType#integer(int) IntegerArgumentType.integer(0)}。
     */
    private static ArgumentType<?> parseIntType(String options) {
        if (options.isEmpty()) {
            return IntegerArgumentType.integer(0);
        }
        // options = "(0,10]", "(-2,]", "(,5)", "[1,9]" 等
        char minBracket = options.charAt(0);          // '(' 或 '['
        char maxBracket = options.charAt(options.length() - 1); // ')' 或 ']'
        String inner = options.substring(1, options.length() - 1); // 例如 "0,10"
        String[] parts = inner.split(",", 2);

        boolean hasMin = !parts[0].isEmpty();
        boolean hasMax = parts.length > 1 && !parts[1].isEmpty();

        int min = 0;
        if (hasMin) {
            min = Integer.parseInt(parts[0]);
            if (minBracket == '(') {
                min++; // 排除：值 > min，所以实际 min = min + 1
            }
        }

        int max = 0;
        if (hasMax) {
            max = Integer.parseInt(parts[1]);
            if (maxBracket == ')') {
                max--; // 排除：值 < max，所以实际 max = max - 1
            }
        }

        if (hasMin && hasMax) {
            return IntegerArgumentType.integer(min, max);
        } else if (hasMin) {
            return IntegerArgumentType.integer(min);
        } else if (hasMax) {
            return IntegerArgumentType.integer(Integer.MIN_VALUE, max);
        } else {
            return IntegerArgumentType.integer();
        }
    }

    // ---- 树形辅助方法 ----

    private static BuilderNode findOrCreateChild(BuilderNode parent, String name,
                                                  boolean isLiteral, ArgumentType<?> argType) {
        for (BuilderNode child : parent.children) {
            if (child.literal == isLiteral && child.name.equals(name)) {
                return child;
            }
        }
        BuilderNode child = new BuilderNode();
        child.literal = isLiteral;
        child.name = name;
        child.argumentType = argType;
        parent.children.add(child);
        return child;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CommandNode<CommandSourceStack> buildNode(BuilderNode node) {
        if (node.literal) {
            LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(node.name);
            if (node.executor != null) {
                builder.executes(node.executor);
            }
            for (BuilderNode child : node.children) {
                builder.then(buildNode(child));
            }
            return builder.build();
        } else {
            RequiredArgumentBuilder builder = Commands.argument(node.name, node.argumentType);
            if (node.executor != null) {
                builder.executes(node.executor);
            }
            for (BuilderNode child : node.children) {
                builder.then(buildNode(child));
            }
            return builder.build();
        }
    }
}
