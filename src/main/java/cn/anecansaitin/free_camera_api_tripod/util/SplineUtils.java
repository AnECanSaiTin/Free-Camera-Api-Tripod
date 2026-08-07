package cn.anecansaitin.free_camera_api_tripod.util;

import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SplineUtils {
    private static final float[] GAUSS_X = {
        -0.9061798459f, -0.5384693101f, 0.0f, 0.5384693101f, 0.9061798459f
    };
    private static final float[] GAUSS_W = {
        0.2369268850f, 0.4786286705f, 0.5688888889f, 0.4786286705f, 0.2369268850f
    };

    /// 计算三阶贝塞尔曲线的长度
    public static float bezierLength(Vector3fc p1, Vector3fc c1, Vector3fc c2, Vector3fc p2) {
        float sum = 0.0f;

        for (int i = 0; i < GAUSS_X.length; i++) {
            float t = 0.5f * GAUSS_X[i] + 0.5f;
            Vector3f derivative = bezierDerivative(p1, c1, c2, p2, t);
            sum += GAUSS_W[i] * derivative.length();
        }

        return 0.5f * sum;
    }

    /// 计算三阶贝塞尔曲线在参数t处的导数 (切向量)
    private static Vector3f bezierDerivative(Vector3fc p1, Vector3fc c1, Vector3fc c2, Vector3fc p2, float t) {
        float rest = 1.0f - t;
        
        Vector3f v1 = new Vector3f(c1).sub(p1);
        Vector3f v2 = new Vector3f(c2).sub(c1);
        Vector3f v3 = new Vector3f(p2).sub(c2);

        v1.mul(3.0f * rest * rest);
        v2.mul(6.0f * rest * t);
        v3.mul(3.0f * t * t);

        return v1.add(v2).add(v3);
    }

    /// 计算三阶贝塞尔曲线在参数t处的位置
    public static Vector3f bezier(Vector3fc p1, Vector3fc c1, Vector3fc c2, Vector3fc p2, float t, Vector3f dest) {
        float rest = 1.0f - t;
        float rest2 = rest * rest;
        float rest3 = rest2 * rest;
        float t2 = t * t;
        float t3 = t2 * t;

        dest.set(p1).mul(rest3)
                .add(new Vector3f(c1).mul(3.0f * rest2 * t))
                .add(new Vector3f(c2).mul(3.0f * rest * t2))
                .add(new Vector3f(p2).mul(t3));

        return dest;
    }

    /// 计算 Catmull-Rom 曲线的长度
    public static float catmullRomLength(Vector3fc p1, Vector3fc p2, Vector3fc p3, Vector3fc p4) {
        float sum = 0.0f;

        for (int i = 0; i < GAUSS_X.length; i++) {
            float t = 0.5f * GAUSS_X[i] + 0.5f;
            Vector3f derivative = catmullRomDerivative(p1, p2, p3, p4, t);
            sum += GAUSS_W[i] * derivative.length();
        }

        return 0.5f * sum;
    }

    /// 计算 Catmull-Rom 曲线在参数t处导数 (切向量)
    private static Vector3f catmullRomDerivative(Vector3fc p1, Vector3fc p2, Vector3fc p3, Vector3fc p4, float t) {
        Vector3f A = new Vector3f(p3).sub(p1);
        Vector3f B = new Vector3f(p3).mul(4)
                .sub(new Vector3f(p4))
                .add(new Vector3f(p1).mul(2)
                        .sub(new Vector3f(p2).mul(5)));
        Vector3f C = new Vector3f(p2).mul(3)
                .sub(new Vector3f(p1))
                .sub(new Vector3f(p3).mul(3))
                .add(new Vector3f(p4));

        return A.add(B.mul(2.0f * t))
                .add(C.mul(3.0f * t * t))
                .mul(0.5f);
    }

    public static Vector3f catmullRom(Vector3fc p1, Vector3fc p2, Vector3fc p3, Vector3fc p4, float t, Vector3f dest) {
        return dest.set(
                Mth.catmullrom(t, p1.x(), p2.x(), p3.x(), p4.x()),
                Mth.catmullrom(t, p1.y(), p2.y(), p3.y(), p4.y()),
                Mth.catmullrom(t, p1.z(), p2.z(), p3.z(), p4.z())
        );
    }
}