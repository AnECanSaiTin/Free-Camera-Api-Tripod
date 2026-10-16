/// 动画文档的稳定 ID 取值集合（主设计 D20 / §3.1）。
///
/// 一个 ID 类型就是一层文档作用域：`TrackId` 在整份文档内唯一，`KeyId` 在其所属轨道内唯一。
/// 复制对象生成新 ID，移动 / 排序 / 重命名 / 撤销保留 ID，整份换档建立新的文档作用域。
///
/// 类型标识（`net.minecraft.resources.Identifier`，走注册表分派）与文档对象身份（本包）
/// 是两套东西，不要互相替代。
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id;

import org.jspecify.annotations.NullMarked;
