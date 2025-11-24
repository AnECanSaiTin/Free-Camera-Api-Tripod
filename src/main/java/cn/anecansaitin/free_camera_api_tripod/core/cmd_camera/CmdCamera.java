package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Set;

public class CmdCamera {
    public static final CmdCamera INSTANCE = new CmdCamera();
    private final HashMap<String, CameraState> states = new HashMap<>();

    @Nullable
    public CameraState get(String id) {
        return states.get(id);
    }

    public CameraState create(String id) {
        CameraState state = new CameraState();
        states.put(id, state);
        return state;
    }

    public Set<String> getAllId() {
        return states.keySet();
    }
}
