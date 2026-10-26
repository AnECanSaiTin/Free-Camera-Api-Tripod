package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.CameraControlClaims;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// 相机控制声明的默认仲裁实现（主设计 §7.5）。
///
/// ## 仲裁
///
/// 声明按显式优先级仲裁：数值越大越优先；同优先级按登记顺序，先登记者获胜。释放当前
/// 获胜声明后立即重新仲裁，下一次查询返回剩余声明中的获胜者；不存在声明时返回空，宿主
/// 据此回退到正式播放状态与 `cameraSource.at(t)`，本类不读取播放状态。
/// [winner] 只读取仲裁结果，不调用 [PoseProvider]；需要姿态时必须用 [winningPose] 在同
/// 一次捕获中取得声明与姿态。
///
/// ## 捕获线性化
///
/// [winningPose] 先冻结获胜声明，再对该声明的 provider 调用一次，因此 provider 执行期间
/// 发生的释放或更高优先级登记只影响**下一次**捕获，本次结果仍返回冻结时的声明与姿态。
/// provider 返回 `null`、抛出异常或尝试嵌套 [winningPose] 都不会被吞掉：
///
/// - 返回 `null` 抛 [NullPointerException]；
/// - 运行时异常原样向外传播，宿主应转成该帧准备失败；
/// - 嵌套捕获抛 [IllegalStateException]，防止一次帧准备重复调用 provider。
///
/// 捕获期间允许同线程释放或登记声明；它们改变后续仲裁，但不会撤销已经冻结的本次结果。
/// 释放后的声明立即从仲裁表移除，其 provider 不会再次被调用；实现不缓存姿态，因此也不存在
/// 复用释放前结果的路径。
///
/// ## 线程
///
/// 构造时记录当前线程为宿主线程；[register]、[release]、[winner]、[winningPose] 只能在该
/// 线程调用，其他线程直接抛 [IllegalStateException]，不静默排队。provider 由
/// [winningPose] 在同一线程同步调用。
@NullMarked
public final class CoreCameraControlClaims implements CameraControlClaims {

    private final Thread ownerThread;
    private final Map<Long, Registration> claims = new LinkedHashMap<>();
    private final Set<Claim> issuedClaims = Collections.newSetFromMap(new IdentityHashMap<>());

    private long nextClaimId;
    private boolean capturing;

    /// 以当前线程为宿主线程创建仲裁表。
    public CoreCameraControlClaims() {
        this.ownerThread = Thread.currentThread();
    }

    @Override
    public Claim register(int priority, LocalizedMessage owner, PoseProvider poseProvider) {
        requireOwnerThread();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(poseProvider, "poseProvider");

        if (nextClaimId < 0L) {
            throw new IllegalStateException("Camera control claim id space is exhausted");
        }

        Claim claim = new Claim(nextClaimId++, priority, owner);
        claims.put(claim.id(), new Registration(claim, poseProvider));
        issuedClaims.add(claim);
        return claim;
    }

    @Override
    public void release(Claim claim) {
        requireOwnerThread();
        Objects.requireNonNull(claim, "claim");

        Registration registered = claims.get(claim.id());

        // 令牌按“本表发出的那个对象”验证归属：值相等的复制品或其它表的首条令牌
        // 都不能释放本表声明。
        if (!issuedClaims.contains(claim)
                || registered == null
                || registered.claim() != claim) {
            throw new IllegalArgumentException(
                    "Unknown or already released camera control claim: " + claim);
        }

        claims.remove(claim.id());
        issuedClaims.remove(claim);
    }

    @Override
    public Optional<Claim> winner() {
        requireOwnerThread();
        return Optional.ofNullable(winnerRegistration()).map(Registration::claim);
    }

    @Override
    public Optional<ClaimedPose> winningPose() {
        requireOwnerThread();

        if (capturing) {
            throw new IllegalStateException(
                    "Nested camera control pose capture is not allowed");
        }

        Registration winner = winnerRegistration();

        if (winner == null) {
            return Optional.empty();
        }

        capturing = true;

        try {
            WorldPose pose = Objects.requireNonNull(winner.provider().pose(),
                    "CameraControlClaims.PoseProvider returned null");
            return Optional.of(new ClaimedPose(winner.claim(), pose));
        } finally {
            capturing = false;
        }
    }

    /// 当前获胜登记；`LinkedHashMap` 的插入顺序即稳定登记顺序。
    private Registration winnerRegistration() {
        Registration best = null;

        for (Registration registration : claims.values()) {
            if (best == null || registration.claim().priority() > best.claim().priority()) {
                best = registration;
            }
        }

        return best;
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "CameraControlClaims must be called on its owner thread");
        }
    }

    /// 登记令牌与姿态提供器的内部绑定。
    private record Registration(Claim claim, PoseProvider provider) {

        private Registration {
            Objects.requireNonNull(claim, "claim");
            Objects.requireNonNull(provider, "provider");
        }
    }
}
