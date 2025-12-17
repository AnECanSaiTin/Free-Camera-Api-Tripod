package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class CmdCamera extends SavedData {
        public static final SavedDataType<CmdCamera> TYPE = new SavedDataType<>(
            "cmd_camera",
            CmdCamera::new,
            RecordCodecBuilder.create(instance -> instance.group(
                            Codec.unboundedMap(Codec.STRING, CameraState.CODEC)
                                    .xmap(HashMap::new, Function.identity())
                                    .fieldOf("states")
                                    .forGetter(cmdCamera -> cmdCamera.states))
                    .apply(instance, CmdCamera::new)
            )
    );

    private final HashMap<String, CameraState> states;

    public CmdCamera() {
        states = new HashMap<>();
    }

    public CmdCamera(HashMap<String, CameraState> states) {
        this.states = states;
    }

    @Nullable
    public CameraState get(String id) {
        return states.get(id);
    }

    public boolean update(String id, Consumer<CameraState> modifier) {
        CameraState state = states.get(id);

        if (state == null) {
            return false;
        }

        setDirty();
        modifier.accept(state);
        return true;
    }

    public void updateOrCreate(String id, Consumer<CameraState> modifier) {
        setDirty();
        CameraState state = states.computeIfAbsent(id, k -> new CameraState());
        modifier.accept(state);
    }

    public void create(String id) {
        setDirty();
        CameraState state = new CameraState();
        states.put(id, state);
    }

    public Set<String> getAllId() {
        return states.keySet();
    }

    public boolean remove(String id) {
        setDirty();
        return states.remove(id) != null;
    }

    public boolean has(String id) {
        return states.containsKey(id);
    }

    public static CmdCamera instance() {
        return ServerLifecycleHooks.getCurrentServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
