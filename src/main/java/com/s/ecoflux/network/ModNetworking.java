package com.s.ecoflux.network;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.plant.VegetationTracker;
import java.util.List;
import net.minecraft.core.BlockPos;
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
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk.getPos(), buildChunkSyncPayload(level, chunk));
    }

    /**
     * Send growth animation triggers to all players tracking the chunk.
     * Called after each tree growth stage places blocks.
     */
    public static void sendGrowthAnimation(ServerLevel level, LevelChunk chunk, List<com.s.ecoflux.plant.tree.GrowthPlacement> placements) {
        List<GrowthAnimationSyncPayload.GrowthAnimEntry> entries = placements.stream()
                .map(p -> new GrowthAnimationSyncPayload.GrowthAnimEntry(p.pos(), p.animType(), p.delayTicks()))
                .toList();
        if (entries.isEmpty()) return;
        GrowthAnimationSyncPayload payload = new GrowthAnimationSyncPayload(chunk.getPos(), entries);
        EcofluxConstants.LOGGER.info("[Ecoflux] SENDING growth anim packet: chunk={}, entries={}",
                chunk.getPos(), entries.size());
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk.getPos(), payload);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(
                VegetationVisualChunkSyncPayload.TYPE,
                VegetationVisualChunkSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleClientSync(payload)));
        registrar.playToClient(
                GrowthAnimationSyncPayload.TYPE,
                GrowthAnimationSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleGrowthAnimSync(payload)));
    }

    private static void onChunkSent(ChunkWatchEvent.Sent event) {
        syncChunkToPlayer(event.getPlayer(), event.getLevel(), event.getChunk());
    }

    private static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
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

    private static void handleGrowthAnimSync(GrowthAnimationSyncPayload payload) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        ClientHooks.handleGrowthAnim(payload);
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

        private static void handleGrowthAnim(GrowthAnimationSyncPayload payload) {
            EcofluxConstants.LOGGER.info("[Ecoflux] CLIENT RECEIVED growth anim packet: chunk={}, entries={}",
                    payload.chunkPos(), payload.entries().size());
            com.s.ecoflux.client.growth.ClientGrowthAnimationManager mgr =
                    com.s.ecoflux.client.growth.ClientGrowthAnimationManager.INSTANCE;
            for (GrowthAnimationSyncPayload.GrowthAnimEntry entry : payload.entries()) {
                EcofluxConstants.LOGGER.info("[Ecoflux] CLIENT adding anim: pos={}, type={}, delay={}",
                        entry.pos(), entry.animType(), entry.delayTicks());
                mgr.addSingle(entry.pos(), entry.animType(), entry.delayTicks());
            }
            EcofluxConstants.LOGGER.info("[Ecoflux] CLIENT total active anims after add: {}",
                    mgr.activeCount());
        }
    }
}
