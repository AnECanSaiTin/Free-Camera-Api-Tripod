package cn.anecansaitin.free_camera_api_tripod.core.chunk_loader;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.*;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID)
public class CameraTicketController {
    public static final TicketController TICKET_CONTROLLER = new TicketController(Identifier.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "camera_chunk"), (level, helper) -> {
        for (UUID uuid : helper.getEntityTickets().keySet()) {
            helper.removeAllTickets(uuid);
        }
    });

    private static final Map<UUID, Set<ChunkPos>> ENTITY_TICKETS = new HashMap<>();

    @SubscribeEvent
    public static void register(RegisterTicketControllersEvent event) {
        event.register(TICKET_CONTROLLER);
    }

    public static void addChunk(ServerLevel level, Player owner, int x, int z) {
        Set<ChunkPos> tickets = ENTITY_TICKETS.computeIfAbsent(owner.getUUID(), uuid -> new HashSet<>());
        boolean add = tickets.add(new ChunkPos(x, z));

        if (!add) {
            return;
        }

        TICKET_CONTROLLER.forceChunk(level, owner, x, z, true, true);
    }

    public static void removeAllChunk(ServerPlayer owner) {
        Set<ChunkPos> tickets = ENTITY_TICKETS.get(owner.getUUID());

        if (tickets == null) {
            return;
        }

        for (ChunkPos ticket : tickets) {
            TICKET_CONTROLLER.forceChunk(owner.level(), owner, ticket.x, ticket.z, false, true);
        }
    }
}
