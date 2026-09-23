package cn.anecansaitin.free_camera_api_tripod.core.animation.io;

import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/// 相机动画与路径的本地文件存储。
///
/// 目录位于游戏目录下、与 screenshots 平级：
/// - {@code camera_animations}：动画文件 {@code *.animation.json}
/// - {@code camera_paths}：路径文件 {@code *.path}
///
/// 文件名中的非法字符会被替换为下划线，清理后为空的名字回退为默认名。
/// 读取时严格校验后缀：后缀不符一律按「读不到」处理，返回 null。
/// 所有 IO 异常都在内部消化，失败时返回 false / null，不向外抛出。
@NullMarked
public final class AnimationFiles {
    /// 动画文件后缀
    public static final String ANIMATION_SUFFIX = ".animation.json";
    /// 路径文件后缀
    public static final String PATH_SUFFIX = ".path.json";

    private static final String ANIMATION_DIR = "camera_animations";
    private static final String PATH_DIR = "camera_paths";
    private static final String DEFAULT_ANIMATION_NAME = "animation";
    private static final String DEFAULT_PATH_NAME = "path";
    // 文件系统非法字符（含控制字符）
    private static final String ILLEGAL_NAME_PATTERN = "[\\\\/:*?\"<>|\\p{Cntrl}]";

    private AnimationFiles() {
    }

    /// 动画目录：游戏目录下与 screenshots 平级的 camera_animations
    public static Path animationDir() {
        return gameDirectory().resolve(ANIMATION_DIR);
    }

    /// 路径目录：游戏目录下与 screenshots 平级的 camera_paths
    public static Path pathDir() {
        return gameDirectory().resolve(PATH_DIR);
    }

    /// 已保存的动画名（只列 {@code .animation.json}，不含后缀，按名字排序，目录不存在时返回空列表）
    public static List<String> listAnimations() {
        return list(animationDir(), ANIMATION_SUFFIX);
    }

    /// 已保存的路径名（只列 {@code .path}，不含后缀，按名字排序，目录不存在时返回空列表）
    public static List<String> listPaths() {
        return list(pathDir(), PATH_SUFFIX);
    }

    /// 保存动画：名字自动补 {@code .animation.json} 后缀；返回是否成功
    public static boolean saveAnimation(String name, String json) {
        return save(animationDir(), name, json, DEFAULT_ANIMATION_NAME, ANIMATION_SUFFIX);
    }

    /// 保存路径：名字自动补 {@code .path} 后缀；返回是否成功
    public static boolean savePath(String name, String json) {
        return save(pathDir(), name, json, DEFAULT_PATH_NAME, PATH_SUFFIX);
    }

    /// 读取动画：名字自动补 {@code .animation.json} 后缀；不存在或读失败返回 null
    public static @Nullable String loadAnimation(String name) {
        return load(animationDir(), name, DEFAULT_ANIMATION_NAME, ANIMATION_SUFFIX);
    }

    /// 读取路径：名字自动补 {@code .path} 后缀；不存在或读失败返回 null
    public static @Nullable String loadPath(String name) {
        return load(pathDir(), name, DEFAULT_PATH_NAME, PATH_SUFFIX);
    }

    /// 把 JSON 写到指定文件（父目录不存在时创建）；失败返回 false
    public static boolean saveTo(Path file, String json) {
        if (file == null || json == null) {
            return false;
        }

        try {
            Path parent = file.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(file, json, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /// 读取动画文件；后缀不是 {@code .animation.json} 或读取失败返回 null
    public static @Nullable String loadAnimationFrom(Path file) {
        return isAnimationFile(file) ? read(file) : null;
    }

    /// 读取路径文件；后缀不是 {@code .path} 或读取失败返回 null
    public static @Nullable String loadPathFrom(Path file) {
        return isPathFile(file) ? read(file) : null;
    }

    /// 给用户输入的名字补上规定后缀。
    ///
    /// 先去掉名字里可能残留的其它已知后缀（例如用户输入 test.json、或沿用旧的 Path.json），
    /// 再补上规定后缀，保证落盘文件名严格是「名字 + 规定后缀」：test.path / test.animation.json。
    public static Path withSuffix(Path file, String suffix) {
        Path named = Path.of(stripKnownSuffix(fileName(file)) + suffix);
        Path parent = file.getParent();
        return parent == null ? named : parent.resolve(named);
    }

    /// 去掉已知后缀（{@code .animation.json} / {@code .path} / {@code .json}），用作动画名 / 路径名
    public static String stem(Path file) {
        return stripKnownSuffix(fileName(file));
    }

    /// 文件是否是动画文件（只看文件名后缀，不访问磁盘）
    public static boolean isAnimationFile(Path file) {
        return endsWithIgnoreCase(fileName(file), ANIMATION_SUFFIX);
    }

    /// 文件是否是路径文件（只看文件名后缀，不访问磁盘）
    public static boolean isPathFile(Path file) {
        return endsWithIgnoreCase(fileName(file), PATH_SUFFIX);
    }

    /// 游戏目录；客户端未初始化时回退到工作目录
    private static Path gameDirectory() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft == null || minecraft.gameDirectory == null) {
            return Path.of("").toAbsolutePath();
        }

        return minecraft.gameDirectory.toPath();
    }

    /// 列出目录下指定后缀的文件名（去掉后缀，按名字排序）
    private static List<String> list(Path dir, String suffix) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> endsWithIgnoreCase(name, suffix))
                    .map(name -> name.substring(0, name.length() - suffix.length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /// 读取指定文件内容；不是普通文件或读失败返回 null
    private static @Nullable String read(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }

        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean save(Path dir, String name, String json, String fallbackName, String suffix) {
        if (name == null || json == null) {
            return false;
        }

        try {
            Files.createDirectories(dir);
            Files.writeString(file(dir, name, fallbackName, suffix), json, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static @Nullable String load(Path dir, String name, String fallbackName, String suffix) {
        if (name == null) {
            return null;
        }

        return read(file(dir, name, fallbackName, suffix));
    }

    /// 用户输入里的已知后缀：用户可能把 {@code test.json} 这类名字直接填进来，
    /// 剥掉这一层再补规定后缀，避免落盘变成 {@code test.json.path.json}。
    private static final String[] KNOWN_SUFFIXES = {ANIMATION_SUFFIX, PATH_SUFFIX, ".json"};

    /// 目录 + 清理后的名字 + 规定后缀；名字已带该后缀时不重复补
    private static Path file(Path dir, String name, String fallbackName, String suffix) {
        String cleaned = sanitize(name, fallbackName);
        return dir.resolve(endsWithIgnoreCase(cleaned, suffix) ? cleaned : cleaned + suffix);
    }

    /// 文件名部分；没有文件名（如盘符）时退回整个路径
    private static String fileName(Path file) {
        Path fileName = file.getFileName();
        return fileName == null ? file.toString() : fileName.toString();
    }

    /// 去掉名字末尾的已知后缀；名字本身（如 test.v2）不受影响
    private static String stripKnownSuffix(String name) {
        for (String known : KNOWN_SUFFIXES) {
            if (endsWithIgnoreCase(name, known)) {
                return name.substring(0, name.length() - known.length());
            }
        }

        return name;
    }

    /// 后缀比较：大小写不敏感（文件系统本身也不区分大小写）
    private static boolean endsWithIgnoreCase(String name, String suffix) {
        return name.length() >= suffix.length()
                && name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length());
    }

    /// 替换文件名中的非法字符；清理后为空或全为空白的名字回退为默认名
    private static String sanitize(@Nullable String name, String fallbackName) {
        if (name == null) {
            return fallbackName;
        }

        String cleaned = name.replaceAll(ILLEGAL_NAME_PATTERN, "_").strip();
        return cleaned.isEmpty() ? fallbackName : cleaned;
    }
}
