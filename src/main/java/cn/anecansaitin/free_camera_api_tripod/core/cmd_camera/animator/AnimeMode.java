package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator;

import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

public sealed interface AnimeMode {
    record Global() implements AnimeMode {
    }

    record Local(Vector3f pos) implements AnimeMode {
    }

    record Attach(Entity entity) implements AnimeMode {
    }
}
