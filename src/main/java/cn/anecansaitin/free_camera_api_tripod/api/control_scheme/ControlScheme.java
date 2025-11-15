package cn.anecansaitin.free_camera_api_tripod.api.control_scheme;

public sealed interface ControlScheme {
    VANILLA VANILLA = new VANILLA();
    CAMERA_RELATIVE CAMERA_RELATIVE = new CAMERA_RELATIVE();
    CAMERA_RELATIVE_STRAFE CAMERA_RELATIVE_STRAFE = new CAMERA_RELATIVE_STRAFE();
    PLAYER_RELATIVE_STRAFE PLAYER_RELATIVE_STRAFE = new PLAYER_RELATIVE_STRAFE();

    static PLAYER_RELATIVE PLAYER_RELATIVE(int angle) {
        return new PLAYER_RELATIVE(angle);
    }

    record VANILLA() implements ControlScheme {
    }

    record CAMERA_RELATIVE() implements ControlScheme {
    }

    record CAMERA_RELATIVE_STRAFE() implements ControlScheme {
    }

    record PLAYER_RELATIVE(int angle) implements ControlScheme {
    }

    record PLAYER_RELATIVE_STRAFE() implements ControlScheme {
    }
}