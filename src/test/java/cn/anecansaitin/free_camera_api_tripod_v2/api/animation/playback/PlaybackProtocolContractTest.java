package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import com.mojang.serialization.DataResult;
import net.minecraft.util.Unit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// R5 网络播放请求契约的结构不变量。
///
/// 本批只交付协议类型与编解码接口，没有具体 `PlaybackProtocolCodec` 实现；
/// 测试覆盖动作校验、来源分支、覆盖容器隔离、执行请求转换与规模上限校验。
class PlaybackProtocolContractTest {

    @Test
    void startKeepsOverridesAndValidatesPlaybackParameters() {
        PlaybackSource source = new PlaybackSource.Local(new ResourceId("animations/demo"));
        RigPlaybackOverrides overrides = RigPlaybackOverrides.INHERIT;

        PlaybackAction.Start start = new PlaybackAction.Start(
                source, Map.of(new RigId("rig"), overrides), 2.0f, true, 5.0f);

        assertEquals(source, start.source());
        assertEquals(1, start.rigOverrides().size());
        assertTrue(start.overriddenRigs().contains(new RigId("rig")));

        assertThrows(IllegalArgumentException.class, () -> new PlaybackAction.Start(
                source, Map.of(), 0f, false, 0f));
        assertThrows(IllegalArgumentException.class, () -> new PlaybackAction.Start(
                source, Map.of(), 1f, false, -1f));
        assertThrows(IllegalArgumentException.class, () -> new PlaybackAction.Start(
                source, Map.of(), Float.NaN, false, 0f));
    }

    @Test
    void actionBranchesAndSourceKindsStayDistinct() {
        CameraAnimation animation = document();
        PlaybackAction.Start start = new PlaybackAction.Start(
                new PlaybackSource.Embedded(animation), Map.of(), 1f, false, 0f);

        assertTrue(start.start().isPresent());
        assertFalse(start.stop());

        PlaybackAction.Stop stop = new PlaybackAction.Stop();

        assertTrue(stop.stop());
        assertTrue(stop.start().isEmpty());
        assertEquals(List.of(), stop.overriddenRigs());

        assertThrows(NullPointerException.class,
                () -> new PlaybackSource.Embedded(null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaybackSource.Local(new ResourceId(" ")));
    }

    @Test
    void embeddedRequestMustUseExactAnimationWhileLocalIsResolvedByResourceService() {
        CameraAnimation animation = document();
        CameraAnimation another = document();

        PlaybackAction.Start embedded = new PlaybackAction.Start(
                new PlaybackSource.Embedded(animation), Map.of(), 1f, false, 0f);

        assertEquals(animation, embedded.toRequest(animation).animation());
        assertThrows(IllegalArgumentException.class, () -> embedded.toRequest(another));

        PlaybackAction.Start local = new PlaybackAction.Start(
                new PlaybackSource.Local(new ResourceId("animations/demo")), Map.of(), 1.5f, false, 2f);

        assertEquals(another, local.toRequest(another).animation());
        assertEquals(1.5f, local.toRequest(another).speed());
    }

    @Test
    void limitsValidateBoundsAndProtocolVersionIsStable() {
        assertEquals(1, PlaybackProtocol.CURRENT_VERSION);

        PlaybackLimits limits = new PlaybackLimits(1024, 8, 4096);

        assertEquals(1024, limits.maxDocumentBytes());
        assertEquals(8, limits.maxRigOverrides());

        assertThrows(IllegalArgumentException.class, () -> new PlaybackLimits(0, 8, 4096));
        assertThrows(IllegalArgumentException.class, () -> new PlaybackLimits(1024, -1, 4096));
        assertThrows(IllegalArgumentException.class, () -> new PlaybackLimits(4096, 8, 1024));
    }

    @Test
    void encodeSuccessCarriesNonNullUnitThroughStandardResult() {
        PlaybackProtocolCodec codec = new PlaybackProtocolCodec() {
            @Override
            public DataResult<Unit> encode(
                    net.minecraft.network.RegistryFriendlyByteBuf buffer,
                    PlaybackAction action, PlaybackLimits limits) {
                return DataResult.success(Unit.INSTANCE);
            }

            @Override
            public DataResult<PlaybackAction> decode(
                    net.minecraft.network.RegistryFriendlyByteBuf buffer, PlaybackLimits limits) {
                return DataResult.error(() -> "decode not used");
            }
        };

        DataResult<Unit> result = codec.encode(
                null, new PlaybackAction.Stop(), new PlaybackLimits(1024, 8, 4096));

        assertEquals(Unit.INSTANCE, result.result().orElseThrow());
    }

    // region 辅助

    private static CameraAnimation document() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));

        return CameraAnimation.create("doc", rig, new TrackId("source"));
    }

    // endregion
}
