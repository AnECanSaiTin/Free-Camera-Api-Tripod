package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.CameraControlClaims;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 相机控制声明的仲裁、捕获线性化与线程约束（主设计 §7.5）。
class CoreCameraControlClaimsTest {

    private static final LocalizedMessage OWNER = LocalizedMessage.literal("test editor");
    private static final WorldPose POSE_A = new WorldPose(Vec3.ZERO, Rotation.NONE, 70f);
    private static final WorldPose POSE_B = new WorldPose(new Vec3(1, 2, 3),
            new Rotation(0.1, 0.2, 0.3), 90f);

    @Test
    void higherPriorityWinsAndTiesKeepRegistrationOrder() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        CameraControlClaims.Claim low = claims.register(-5, OWNER, () -> POSE_A);
        CameraControlClaims.Claim highFirst = claims.register(7, OWNER, () -> POSE_A);
        CameraControlClaims.Claim highSecond = claims.register(7, OWNER, () -> POSE_B);

        assertNotEquals(low.id(), highFirst.id());
        assertEquals(highFirst, claims.winner().orElseThrow());

        claims.release(highFirst);
        assertEquals(highSecond, claims.winner().orElseThrow());

        claims.release(highSecond);
        assertEquals(low, claims.winner().orElseThrow());

        claims.release(low);
        assertTrue(claims.winner().isEmpty());
    }

    @Test
    void winnerDoesNotCallProviderAndEachCaptureCallsOnce() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        AtomicInteger calls = new AtomicInteger();
        CameraControlClaims.Claim claim = claims.register(0, OWNER, () -> {
            calls.incrementAndGet();
            return POSE_A;
        });

        assertTrue(claims.winner().isPresent());
        assertEquals(0, calls.get());

        CameraControlClaims.ClaimedPose first = claims.winningPose().orElseThrow();
        CameraControlClaims.ClaimedPose second = claims.winningPose().orElseThrow();

        assertEquals(2, calls.get());
        assertEquals(claim, first.claim());
        assertEquals(POSE_A, first.pose());
        assertEquals(POSE_A, second.pose());
    }

    @Test
    void releaseRemovesClaimAndRejectsRepeat() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        AtomicInteger calls = new AtomicInteger();
        CameraControlClaims.Claim claim = claims.register(0, OWNER, () -> {
            calls.incrementAndGet();
            return POSE_A;
        });

        claims.winningPose().orElseThrow();
        claims.release(claim);

        assertTrue(claims.winner().isEmpty());
        assertTrue(claims.winningPose().isEmpty());
        assertEquals(1, calls.get());
        assertThrows(IllegalArgumentException.class, () -> claims.release(claim));
    }

    @Test
    void equalTokenFromAnotherTableOrExactCopyCannotReleaseClaim() {
        CoreCameraControlClaims first = new CoreCameraControlClaims();
        CoreCameraControlClaims second = new CoreCameraControlClaims();
        CameraControlClaims.Claim firstClaim = first.register(5, OWNER, () -> POSE_A);
        CameraControlClaims.Claim secondClaim = second.register(5, OWNER, () -> POSE_B);
        CameraControlClaims.Claim copied = new CameraControlClaims.Claim(
                firstClaim.id(), firstClaim.priority(), firstClaim.owner());

        assertEquals(firstClaim, secondClaim);
        assertThrows(IllegalArgumentException.class, () -> first.release(secondClaim));
        assertThrows(IllegalArgumentException.class, () -> first.release(copied));
        assertEquals(firstClaim, first.winner().orElseThrow());
        assertSame(POSE_A, first.winningPose().orElseThrow().pose());
    }

    @Test
    void forgedOrUnknownReleaseDoesNotAffectOtherClaims() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        CameraControlClaims.Claim real = claims.register(5, OWNER, () -> POSE_A);
        CameraControlClaims.Claim forged = new CameraControlClaims.Claim(
                real.id(), 99, LocalizedMessage.literal("forged"));
        CameraControlClaims.Claim unknown = new CameraControlClaims.Claim(
                9999, 0, OWNER);

        assertThrows(IllegalArgumentException.class, () -> claims.release(forged));
        assertThrows(IllegalArgumentException.class, () -> claims.release(unknown));
        assertEquals(real, claims.winner().orElseThrow());
    }

    @Test
    void providerFailurePropagatesWithoutChangingWinner() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        RuntimeException boom = new IllegalStateException("provider boom");
        CameraControlClaims.Claim claim = claims.register(0, OWNER, () -> {
            throw boom;
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, claims::winningPose);

        assertSame(boom, thrown);
        assertEquals(claim, claims.winner().orElseThrow());

        claims.release(claim);
        assertTrue(claims.winningPose().isEmpty());
    }

    @Test
    void nullPoseIsRejected() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        CameraControlClaims.Claim claim = claims.register(0, OWNER, () -> null);

        assertThrows(NullPointerException.class, claims::winningPose);
        assertEquals(claim, claims.winner().orElseThrow());
    }

    @Test
    void nestedCaptureIsRejected() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        CameraControlClaims.Claim claim = claims.register(0, OWNER, () -> {
            assertTrue(claims.winner().isPresent());
            IllegalStateException nested = assertThrows(IllegalStateException.class,
                    claims::winningPose);
            assertTrue(nested.getMessage().contains("Nested"));
            return POSE_A;
        });

        CameraControlClaims.ClaimedPose captured = claims.winningPose().orElseThrow();

        assertEquals(claim, captured.claim());
        assertEquals(POSE_A, captured.pose());
    }

    @Test
    void releaseDuringCaptureAffectsNextCaptureOnly() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        CameraControlClaims.Claim[] holder = new CameraControlClaims.Claim[1];
        holder[0] = claims.register(0, OWNER, () -> {
            claims.release(holder[0]);
            return POSE_A;
        });

        CameraControlClaims.ClaimedPose captured = claims.winningPose().orElseThrow();

        assertEquals(holder[0], captured.claim());
        assertEquals(POSE_A, captured.pose());
        assertTrue(claims.winner().isEmpty());
        assertTrue(claims.winningPose().isEmpty());
    }

    @Test
    void registrationDuringCaptureAffectsNextCaptureOnly() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        AtomicReference<CameraControlClaims.Claim> added = new AtomicReference<>();
        CameraControlClaims.Claim first = claims.register(0, OWNER, () -> {
            added.set(claims.register(10, OWNER, () -> POSE_B));
            return POSE_A;
        });

        CameraControlClaims.ClaimedPose firstCapture = claims.winningPose().orElseThrow();

        assertEquals(first, firstCapture.claim());
        assertEquals(POSE_A, firstCapture.pose());

        CameraControlClaims.ClaimedPose secondCapture = claims.winningPose().orElseThrow();

        assertEquals(added.get(), secondCapture.claim());
        assertEquals(POSE_B, secondCapture.pose());
    }

    @Test
    void callsFromOtherThreadsAreRejected() throws InterruptedException {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();
        AtomicInteger calls = new AtomicInteger();
        CameraControlClaims.Claim claim = claims.register(0, OWNER, () -> {
            calls.incrementAndGet();
            return POSE_A;
        });

        assertTrue(runOnOtherThread(claims::winner) instanceof IllegalStateException);
        assertTrue(runOnOtherThread(claims::winningPose) instanceof IllegalStateException);
        assertTrue(runOnOtherThread(() -> claims.release(claim)) instanceof IllegalStateException);
        assertTrue(runOnOtherThread(() -> claims.register(1, OWNER, () -> POSE_B))
                instanceof IllegalStateException);

        assertEquals(0, calls.get());
        assertEquals(claim, claims.winner().orElseThrow());
        assertEquals(POSE_A, claims.winningPose().orElseThrow().pose());
    }

    @Test
    void registrationRejectsNullInputs() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();

        assertThrows(NullPointerException.class,
                () -> claims.register(0, null, () -> POSE_A));
        assertThrows(NullPointerException.class,
                () -> claims.register(0, OWNER, null));
        assertTrue(claims.winner().isEmpty());
    }

    @Test
    void emptyTableReturnsEmptyAndRejectsNullClaim() {
        CoreCameraControlClaims claims = new CoreCameraControlClaims();

        assertEquals(Optional.empty(), claims.winner());
        assertEquals(Optional.empty(), claims.winningPose());
        assertThrows(NullPointerException.class, () -> claims.release(null));
    }

    /// 在独立线程执行动作并返回其抛出的异常；没有异常时返回 `null`。
    private static Throwable runOnOtherThread(Runnable action) throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable failure) {
                thrown.set(failure);
            }
        }, "camera-claims-test");

        thread.start();
        thread.join();

        return thrown.get();
    }
}
