package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import java.util.HashMap;
import java.util.Set;

public class CmdCamera {
    public static final CmdCamera INSTANCE = new CmdCamera();
    private final HashMap<String, CameraState> states = new HashMap<>();

    public CameraState get(String id) {
        return states.get(id);
    }

    public Set<String> getAllId() {
        return states.keySet();
    }
}
