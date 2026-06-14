package com.s.ecoflux.network;

/**
 * NeoForge network setup: registers custom payload handlers and wires chunk-watch
 * events for vegetation visual sync.
 *
 * <p>Structure: static utility class with {@code register()} to bind the
 * {@code VegetationVisualChunkSyncPayload} handler on the mod event bus, and
 * chunk watch/unwatch listeners on the NeoForge event bus.
 * {@code syncChunkToTracking()} sends the current vegetation visual state to all
 * clients tracking a given chunk.
 *
 * <p>Role in Ecoflux: the single entry point for all network communication.
 * Ensures that whenever a client starts or stops watching a chunk, the server
 * pushes the latest visual lifecycle data so plants render with correct scale
 * and tint on the client side.
 */

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.plant.VegetationTracker;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private static final String NETWORK_VERSION = "1";

    private ModNetworking() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(ModNetworking::onChunkSent);
        NeoForge.EVENT_BUS.addListener(ModNetworking::onChunkUnwatch);
    }

    public static void syncChunkToTracking(ServerLevel level, LevelChunk chunk) {
        if (!com.s.ecoflux.config.EcofluxServerConfig.enableVisualSystem()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk.getPos(), buildChunkSyncPayload(level, chunk));
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(
                VegetationVisualChunkSyncPayload.TYPE,
                VegetationVisualChunkSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleClientSync(payload)));
    }

    private static void onChunkSent(ChunkWatchEvent.Sent event) {
        if (!com.s.ecoflux.config.EcofluxServerConfig.enableVisualSystem()) {
            return;
        }
        syncChunkToPlayer(event.getPlayer(), event.getLevel(), event.getChunk());
    }

    private static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        if (!com.s.ecoflux.config.EcofluxServerConfig.enableVisualSystem()) {
            return;
        }
        PacketDistributor.sendToPlayer(
                event.getPlayer(),
                new VegetationVisualChunkSyncPayload(
                        event.getLevel().dimension().location(),
                        event.getPos(),
                        java.util.List.of()));
    }

    private static void syncChunkToPlayer(ServerPlayer player, ServerLevel level, LevelChunk chunk) {
        PacketDistributor.sendToPlayer(player, buildChunkSyncPayload(level, chunk));
    }

    private static VegetationVisualChunkSyncPayload buildChunkSyncPayload(ServerLevel level, LevelChunk chunk) {
        return new VegetationVisualChunkSyncPayload(
                level.dimension().location(),
                chunk.getPos(),
                VegetationTracker.INSTANCE.buildVisualSyncEntries(chunk, level.getGameTime()));
    }

    private static void handleClientSync(VegetationVisualChunkSyncPayload payload) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        ClientHooks.handle(payload);
    }

    private static final class ClientHooks {
        private ClientHooks() {
        }

        private static void handle(VegetationVisualChunkSyncPayload payload) {
            com.s.ecoflux.client.visual.VisualLifecycleClientRuntime.INSTANCE.syncVegetationChunk(
                    payload.dimensionId(),
                    payload.chunkPos(),
                    payload.entries());
        }
    }
}
