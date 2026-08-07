package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

public record Selected(int index, Type type) {
    public enum Type {
        NODE,
        IN,
        OUT
    }
}
