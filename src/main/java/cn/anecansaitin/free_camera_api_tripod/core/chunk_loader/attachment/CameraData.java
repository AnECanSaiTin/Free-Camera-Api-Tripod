package cn.anecansaitin.free_camera_api_tripod.core.chunk_loader.attachment;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Consumer;

public class CameraData {
    public boolean enable;
    public boolean update;
    public float x;
    public float y;
    public float z;
    public CameraChunkTrackingView view = new CameraChunkTrackingView(Integer.MAX_VALUE, Integer.MAX_VALUE, 0);

    public void updateState(boolean enable, boolean update) {
        this.enable = enable;
        this.update = update;
    }

    public void updatePos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean updateView(int x, int z, int radius) {
        if (view.x - x + view.z - z == 0) {
            return false;
        }

        view = new CameraChunkTrackingView(x, z, radius);
        update = true;
        return true;
    }

    public record CameraChunkTrackingView(int x, int z, int radius) implements ChunkTrackingView {
        @Override
        public boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
            return ChunkTrackingView.isWithinDistance(x, z, radius, x, z, includeOuterChunksAdjacentToViewBorder);
        }

        @Override
        public void forEach(Consumer<ChunkPos> action) {
            for (int i = this.minX(); i <= this.maxX(); i++) {
                for (int j = this.minZ(); j <= this.maxZ(); j++) {
                    if (this.contains(i, j)) {
                        action.accept(new ChunkPos(i, j));
                    }
                }
            }
        }

        private int minX() {
            return x - radius - 1;
        }

        private int minZ() {
            return z - radius - 1;
        }

        private int maxX() {
            return x + radius + 1;
        }

        private int maxZ() {
            return z + radius + 1;
        }
    }
}
