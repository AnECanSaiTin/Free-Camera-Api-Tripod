package cn.anecansaitin.hm.api.animation.block;

import cn.anecansaitin.hm.api.DocSet;

/// 符号表
public record Symbols(DocSet<?, ?> variables, DocSet<?, ?> functions) {
}
