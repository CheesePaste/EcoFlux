package com.s.ecoflux.init;

/**
 * Chunk load, unload, and tick event handlers for the succession lifecycle.
 *
 * <p>Structure: registers NeoForge event listeners. On chunk load it initializes
 * succession state and restores tree growth sessions. On level tick it drives
 * automatic succession stepping, accelerated prototype transitions, and tree
 * growth ticking.
 * <p>Role in Ecoflux: the main server-side tick driver; connects chunk events
 * to {@code SuccessionService}, {@code PrototypeChunkController}, and
 * {@code TreeGrowthHandler}.
 */

import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.EcofluxServerConfig;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import com.s.ecoflux.plant.tree.TreeGrowthHandler;
import com.s.ecoflux.prototype.PrototypeChunkController;
import com.s.ecoflux.succession.SuccessionService;
import com.s.ecoflux.world.ChunkTrackingState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class ModChunkEvents {
    private static final Map<ResourceKey<Level>, Map<Long, Long>> ACCELERATED_CHUNKS = new HashMap<>();
    private static final int ACCELERATED_TRANSITION_TICKS = 200;
    private static final int TREE_GROWTH_TICK_INTERVAL = 20;
    private static volatile boolean automaticProcessingEnabled;

    private ModChunkEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onLevelTick);
    }

    public static boolean isAutomaticProcessingEnabled() {
        return automaticProcessingEnabled;
    }

    public static void setAutomaticProcessingEnabled(boolean enabled) {
        automaticProcessingEnabled = enabled;
    }

    public static void syncChunkTracking(ServerLevel level, ChunkAccess chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        long chunkPosLong = chunk.getPos().toLong();
        if (SuccessionService.hasActivePath(chunkData)) {
            ChunkTrackingState.addTrackedChunk(level.dimension(), chunkPosLong);
        } else {
            ChunkTrackingState.removeTrackedChunk(level.dimension(), chunkPosLong);
        }
    }

    public static void startAcceleratedTransition(ServerLevel level, LevelChunk chunk) {
        ACCELERATED_CHUNKS
                .computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .put(chunk.getPos().toLong(), level.getGameTime());
        ChunkTrackingState.addTrackedChunk(level.dimension(), chunk.getPos().toLong());
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getCurrentBiome().isEmpty()) {
            SuccessionService.initializeChunk(chunk);
        }

        if (chunk instanceof LevelChunk levelChunk) {
            TreeGrowthHandler.INSTANCE.onChunkLoad(serverLevel, levelChunk);
        }

        syncChunkTracking(serverLevel, chunk);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkTrackingState.removeTrackedChunk(serverLevel.dimension(), event.getChunk().getPos().toLong());
        Map<Long, Long> acceleratedChunks = ACCELERATED_CHUNKS.get(serverLevel.dimension());
        if (acceleratedChunks != null) {
            acceleratedChunks.remove(event.getChunk().getPos().toLong());
            if (acceleratedChunks.isEmpty()) {
                ACCELERATED_CHUNKS.remove(serverLevel.dimension());
            }
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        processAcceleratedChunks(serverLevel);
        processTreeGrowth(serverLevel);
        if (!automaticProcessingEnabled) {
            return;
        }

        LinkedHashSet<Long> trackedChunks = ChunkTrackingState.getTrackedChunks(serverLevel.dimension());
        if (trackedChunks == null || trackedChunks.isEmpty()) {
            return;
        }

        Map<Long, Long> acceleratedChunks = ACCELERATED_CHUNKS.get(serverLevel.dimension());
        List<Long> snapshot = new ArrayList<>(trackedChunks);
        for (long chunkPosLong : snapshot) {
            if (acceleratedChunks != null && acceleratedChunks.containsKey(chunkPosLong)) {
                continue;
            }

            LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(
                    net.minecraft.world.level.ChunkPos.getX(chunkPosLong),
                    net.minecraft.world.level.ChunkPos.getZ(chunkPosLong));
            if (chunk == null) {
                ChunkTrackingState.removeTrackedChunk(serverLevel.dimension(), chunkPosLong);
                continue;
            }

            SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
            if (!SuccessionService.hasActivePath(chunkData)) {
                ChunkTrackingState.removeTrackedChunk(serverLevel.dimension(), chunkPosLong);
                continue;
            }

            SuccessionService.processChunkTick(serverLevel, chunk);
            if (!SuccessionService.hasActivePath(chunkData)) {
                ChunkTrackingState.removeTrackedChunk(serverLevel.dimension(), chunkPosLong);
            }
        }
    }

    private static void processAcceleratedChunks(ServerLevel level) {
        Map<Long, Long> acceleratedChunks = ACCELERATED_CHUNKS.get(level.dimension());
        if (acceleratedChunks == null || acceleratedChunks.isEmpty()) {
            return;
        }

        List<Map.Entry<Long, Long>> snapshot = new ArrayList<>(acceleratedChunks.entrySet());
        for (Map.Entry<Long, Long> entry : snapshot) {
            long chunkPosLong = entry.getKey();
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    net.minecraft.world.level.ChunkPos.getX(chunkPosLong),
                    net.minecraft.world.level.ChunkPos.getZ(chunkPosLong));
            if (chunk == null) {
                acceleratedChunks.remove(chunkPosLong);
                continue;
            }

            boolean done = PrototypeChunkController.processAcceleratedTick(
                    level,
                    chunk,
                    entry.getValue(),
                    ACCELERATED_TRANSITION_TICKS);
            if (done) {
                acceleratedChunks.remove(chunkPosLong);
                syncChunkTracking(level, chunk);
            }
        }

        if (acceleratedChunks.isEmpty()) {
            ACCELERATED_CHUNKS.remove(level.dimension());
        }
    }

    private static void processTreeGrowth(ServerLevel level) {
        if (!EcofluxServerConfig.gradualTreeGrowth()) {
            return;
        }
        long effectiveInterval = (long) Math.max(1, TREE_GROWTH_TICK_INTERVAL / SuccessionSpeedConfig.getSpeedMultiplier());
        if (level.getGameTime() % effectiveInterval != 0) {
            return;
        }
        TreeGrowthHandler.INSTANCE.tickAll(level);
    }
}
