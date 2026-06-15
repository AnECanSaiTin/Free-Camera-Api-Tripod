package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.api.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Clip;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Curve;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Evaluator;
import cn.anecansaitin.freecameraapi.api.CameraModifier;
import cn.anecansaitin.freecameraapi.api.CameraPlugin;
import cn.anecansaitin.freecameraapi.api.Plugin;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;

@Plugin("cmd_camera")
public class CmdCamera implements CameraPlugin {
    public static CmdCamera INSTANCE;
    private final CameraModifier modifier;
    private Clip clip;
    private PlayingState state = PlayingState.STOP;
    private int startTime = 0;
    private int pauseTime = 0;
    private int pauseDuration = 0;

    public CmdCamera(CameraModifier modifier) {
        INSTANCE = this;
        this.modifier = modifier;
        clip = new Clip();
        clip.addCurve("position.x", new Curve());
        clip.addCurve("position.y", new Curve());
        clip.addCurve("position.z", new Curve());
        clip.addCurve("rotation.x", new Curve());
        clip.addCurve("rotation.y", new Curve());
        clip.addCurve("rotation.z", new Curve());
        clip.addCurve("fov", new Curve());
        modifier.enableFov()
                .enablePos()
                .enableGlobalMode()
                .enableRotation();
    }

    @Override
    public void update(float deltaTime) {
        switch (state){
            case PRE_PLAY -> prePlay(deltaTime);
            case PLAY -> playing(deltaTime);
            case PRE_STOP -> preStop();
            case PAUSED -> {
                int currentTime = Minecraft.getInstance().levelRenderer.getTicks();
                pauseDuration = currentTime - pauseTime;
            }
            case STOP -> {

            }
        }
    }

    private void prePlay(float deltaTime) {
        modifier.enable();
        state = PlayingState.PLAY;
        startTime = Minecraft.getInstance().levelRenderer.getTicks();
        playing(deltaTime);
    }

    private void playing(float deltaTime) {
        int currentTime = Minecraft.getInstance().levelRenderer.getTicks();
        float time = (currentTime - startTime - pauseDuration + deltaTime) / 20f;

        if (time > clip.duration()) {
            preStop();
            return;
        }

        Vector3f pos = evaluatePos(time);
        Vector3f rot = evaluateRot(time);
        float fov = evaluateFov(time);
        modifier.setPos(pos)
                .setRotationYXZ(rot)
                .setFov(fov);
    }

    private void preStop() {
        modifier.disable();
        state = PlayingState.STOP;
    }

    private Vector3f evaluatePos(float time) {
        return clip.evaluate(time, posEvaluator);
    }

    private Vector3f evaluateRot(float time) {
        return clip.evaluate(time, rotEvaluator);
    }

    private float evaluateFov(float time) {
        return clip.evaluate("fov", time);
    }

    public void play() {
        state = PlayingState.PRE_PLAY;
    }

    public void stop() {
        state = PlayingState.PRE_STOP;
    }

    public void pause() {
        state = PlayingState.PAUSED;
        pauseTime = Minecraft.getInstance().levelRenderer.getTicks();
    }

    public void addPosKey(float time, float x, float y, float z) {
        clip.addKey("position.x", Keyframe.create(time, x));
        clip.addKey("position.y", Keyframe.create(time, y));
        clip.addKey("position.z", Keyframe.create(time, z));
    }

    public void addRotKey(float time, float x, float y, float z) {
        clip.addKey("rotation.x", Keyframe.create(time, x));
        clip.addKey("rotation.y", Keyframe.create(time, y));
        clip.addKey("rotation.z", Keyframe.create(time, z));
    }

    public void addFovKey(float time, float fov) {
        clip.addKey("fov", Keyframe.create(time, fov));
    }

    public void removePosKey(int index) {
        clip.removeKey("position.x", index);
        clip.removeKey("position.y", index);
        clip.removeKey("position.z", index);
    }

    public void removeRotKey(int index) {
        clip.removeKey("rotation.x", index);
        clip.removeKey("rotation.y", index);
        clip.removeKey("rotation.z", index);
    }

    public void removeFovKey(int index) {
        clip.removeKey("fov", index);
    }

    public Clip clip() {
        return clip;
    }

    public void clip(Clip clip) {
        this.clip = clip;
    }

    private final Evaluator<Vector3f> posEvaluator = new Evaluator<>() {
        private final Vector3f vec = new Vector3f();

        @Override
        public String[] properties() {
            return new String[]{"position.x", "position.y", "position.z"};
        }

        @Override
        public Vector3f build(float... values) {
            return vec.set(values[0], values[1], values[2]);
        }
    };

    private final Evaluator<Vector3f> rotEvaluator = new Evaluator<>() {
        private final Vector3f vec = new Vector3f();

        @Override
        public String[] properties() {
            return new String[]{"rotation.x", "rotation.y", "rotation.z"};
        }

        @Override
        public Vector3f build(float... values) {
            return vec.set(values[0], values[1], values[2]);
        }
    };

    private enum PlayingState {
        PRE_PLAY,
        PLAY,
        PRE_STOP,
        STOP,
        PAUSED
    }
}
