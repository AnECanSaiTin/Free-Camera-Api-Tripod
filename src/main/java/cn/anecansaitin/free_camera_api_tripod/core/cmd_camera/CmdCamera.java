package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Clip;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Evaluator;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Path;
import cn.anecansaitin.free_camera_api_tripod.core.animation.PathNode;
import cn.anecansaitin.freecameraapi.api.CameraModifier;
import cn.anecansaitin.freecameraapi.api.CameraPlugin;
import cn.anecansaitin.freecameraapi.api.Plugin;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;

@NullMarked
@Plugin(value = "cmd_camera", modid = FreeCameraApiTripod.MODID)
public class CmdCamera implements CameraPlugin {
    public static CmdCamera INSTANCE;
    private final CameraModifier modifier;
    private Clip clip;
    private Path path;
    private PlayingState state = PlayingState.STOP;
    private int startTime = 0;
    private int pauseTime = 0;
    private int pauseDuration = 0;
    private final Vector3f cache = new Vector3f();

    private boolean editing = false;
    // todo 这个不应该放在这里
    //todo 计划分三个类，播放、编辑、展示信息
    private Selected selectedPathNode = new Selected(0, Selected.Type.NODE);

    public CmdCamera(CameraModifier modifier) {
        INSTANCE = this;
        this.modifier = modifier
                .enableFov()
                .enablePos()
                .enableGlobalMode()
                .enableRotation();

        clip = new Clip();
        clip.addCurve("position", new Curve());
        clip.addCurve("rotation.x", new Curve());
        clip.addCurve("rotation.y", new Curve());
        clip.addCurve("rotation.z", new Curve());
        clip.addCurve("fov", new Curve());
        addPosKey(0f, 0f);
        addFovKey(0f, 70f);

        path = new Path();
//        path.node(PathNode.bezier(new Vector3f(3.147f, 58f, -6.496f), new Vector3f(0, -2.2f, 0)));
//        path.node(PathNode.bezier(new Vector3f(3.484f, 58f, -17.726f), new Vector3f(3, 0, 0)));
//        path.node(PathNode.bezier(new Vector3f(9.629f, 58f, -17.581f), new Vector3f(-1.5f, -2.3f, 0)));
//        path.node(PathNode.bezier(new Vector3f(11.308f, 58f, -9.503f), new Vector3f(0, -3, 4.12f)));
        path.node(PathNode.catmullRom(new Vector3f(3.147f, 58f, -6.496f)));
        path.node(PathNode.catmullRom(new Vector3f(3.484f, 58f, -17.726f)));
        path.node(PathNode.catmullRom(new Vector3f(9.629f, 58f, -17.581f)));
        path.node(PathNode.catmullRom(new Vector3f(11.308f, 58f, -9.503f)));
        path.node(PathNode.catmullRom(new Vector3f(18.308f, 58f, -9.503f)));
        addPosKey(5f, /*(float) path.totalLength()*/1);
    }

    @Override
    public void update(float deltaTime) {
        switch (state) {
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
        float distance = clip.evaluate("position", time);
        return path.evaluate(cache, distance);
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

    public void addPosKey(float time, float distance) {
        clip.addKey("position", Keyframe.create(time, distance));
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
        clip.removeKey("position", index);
    }

    public void removeRotKey(int index) {
        clip.removeKey("rotation.x", index);
        clip.removeKey("rotation.y", index);
        clip.removeKey("rotation.z", index);
    }

    public void removeFovKey(int index) {
        clip.removeKey("fov", index);
    }

    public void addPosPath(PathNode node) {
        path.node(node);
    }

    public void insertPosPath(int index, PathNode node) {
        path.insertNode(index, node);
    }

    public boolean removePosPath(int index) {
        boolean result = path.removeNode(index);

        if (result && selectedPathNode.index() >= path.size()) {
            selectedPathNode = new Selected(path.size() - 1, Selected.Type.NODE);
        }

        return result;
    }

    public Clip clip() {
        return clip;
    }

    public void clip(Clip clip) {
        this.clip = clip;
    }

    public Path path() {
        return path;
    }

    public void path(Path path) {
        this.path = path;
    }

    public Selected selectedPathNode() {
        return selectedPathNode;
    }

    public boolean selectedPathNode(Selected selected) {
        if (selected.index() < 0 || selected.index() >= path.size()) {
            return false;
        }

        this.selectedPathNode = selected;
        return true;
    }

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
