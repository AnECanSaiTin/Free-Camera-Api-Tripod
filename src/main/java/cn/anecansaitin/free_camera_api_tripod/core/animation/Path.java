package cn.anecansaitin.free_camera_api_tripod.core.animation;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.joml.Vector3f;

import java.util.ArrayList;

public class Path {
    private final ArrayList<PathNode> nodes = new ArrayList<>();
    private final FloatArrayList arcLengthTable = new FloatArrayList();
    private float totalLength;

//    public Vector3f evaluate(float delta) {
//        float oneMinusT = 1.0f - delta;
//
//        return oneMinusT * oneMinusT * oneMinusT * p0 +
//                3 * oneMinusT * oneMinusT * delta * p1 +
//                3 * oneMinusT * delta * delta * p2 +
//                delta * delta * delta * p3;
//    }
}
