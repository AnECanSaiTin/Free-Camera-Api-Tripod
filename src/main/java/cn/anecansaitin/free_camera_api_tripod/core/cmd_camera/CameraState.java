package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import org.jetbrains.annotations.NotNull;

public class CameraState {
    private int state;
    @NotNull
    private ControlScheme scheme;

    public CameraState(int state, @NotNull ControlScheme scheme) {
        this.state = state;
        this.scheme = scheme;
    }

    public CameraState(int state) {
        this.state = state;
        scheme = ControlScheme.VANILLA;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    @NotNull
    public ControlScheme getScheme() {
        return scheme;
    }

    public void setScheme(@NotNull ControlScheme scheme) {
        this.scheme = scheme;
    }
}
