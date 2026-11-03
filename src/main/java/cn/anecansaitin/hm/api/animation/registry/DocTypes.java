package cn.anecansaitin.hm.api.animation.registry;

import cn.anecansaitin.hm.Hm;
import cn.anecansaitin.hm.api.DocObject;
import cn.anecansaitin.hm.api.DocSet;
import cn.anecansaitin.hm.api.animation.block.PlaybackRange;
import net.minecraft.resources.Identifier;

public final class DocTypes {
    public static final DocType<DocSet<?, ?>> DOC_SET;
    public static final DocType<PlaybackRange.Auto> PLAYBACK_RANGE_AUTO;
    public static final DocType<PlaybackRange.Manual> PLAYBACK_RANGE_MANUAL;

    static {
        DOC_SET = docType(DocSet.class, DocTypeCodec.flex(DocSet::codec, DocSet::mapCodec, DocSet::streamCodec));
        PLAYBACK_RANGE_AUTO = docType(PlaybackRange.Auto.class, DocTypeCodec.stable(PlaybackRange.Auto.CODEC, PlaybackRange.Auto.MAP_CODEC, PlaybackRange.Auto.STREAM_CODEC));
        PLAYBACK_RANGE_MANUAL = docType(PlaybackRange.Manual.class, DocTypeCodec.stable(PlaybackRange.Manual.CODEC, PlaybackRange.Manual.MAP_CODEC, PlaybackRange.Manual.STREAM_CODEC));
    }

    private static <T extends DocObject> DocType<T> docType(Class<?> clazz, DocTypeCodec<T> codec) {
        return new DocType<>(Identifier.fromNamespaceAndPath(Hm.MODID, toSnakeCase(clazz.getName())), clazz, codec);
    }

    public static String toSnakeCase(String fullClassName) {
        if (fullClassName.isEmpty()) {
            return fullClassName;
        }

        // 1. 省略包名：取最后一个 '.' 之后的部分
        int lastDotIndex = fullClassName.lastIndexOf('.');
        String simpleName = (lastDotIndex != -1) ? fullClassName.substring(lastDotIndex + 1) : fullClassName;

        // 2. 按 '$' 分割处理内部类，分别转换后拼接
        String[] parts = simpleName.split("\\$");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append("_");
            }
            result.append(camelToSnake(parts[i]));
        }

        return result.toString();
    }

    private static String camelToSnake(String camelCase) {
        if (camelCase.isEmpty()) {
            return camelCase;
        }

        // 正则1: 在小写字母/数字 和 大写字母之间插入下划线
        // 例如: MyClass -> My_Class,  xmlParser -> xml_Parser
        String step1 = camelCase.replaceAll("([a-z0-9])([A-Z])", "$1_$2");

        // 正则2: 在连续大写字母 和 大写字母+小写字母之间插入下划线
        // 例如: XMLParser -> XML_Parser
        String step2 = step1.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");

        // 转小写
        return step2.toLowerCase();
    }

    private DocTypes() {}
}
