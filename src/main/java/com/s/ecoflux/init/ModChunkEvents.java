package com.s.ecoflux.init;

import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.EcofluxServerConfig;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import com.s.ecoflux.plant.tree.TreeGrowthHandler;
import com.s.ecoflux.succession.SuccessionService;
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
    private static final int TREE_GROWTH_TICK_INTERVAL = 20;
    private static volatile boolean globalAutoEnabled;
    /** All loaded chunks (for global-auto iteration). */
    private static final Map<ResourceKey<Level>, LinkedHashSet<Long>> ALL_LOADED_CHUNKS = new HashMap<>();
    /** Per-chunk auto set (prototype accelerate). */
    private static final Map<ResourceKey<Level>, LinkedHashSet<Long>> AUTO_CHUNKS = new HashMap<>();
    /** Chunks with tree-structure records that need lifecycle observation. */
    private static final Map<ResourceKey<Level>, LinkedHashSet<Long>> TREE_OBSERVE_CHUNKS = new HashMap<>();

    private ModChunkEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onLevelTick);
    }

    // ── Global auto ──────────────────────────────────────────────────────

    public static boolean isGlobalAutoEnabled() {
        return globalAutoEnabled;
    }

    public static void setGlobalAutoEnabled(boolean enabled) {
        globalAutoEnabled = enabled;
    }

    // ── Per-chunk auto ───────────────────────────────────────────────────

    public static void enableAutoForChunk(ServerLevel level, LevelChunk chunk) {
        AUTO_CHUNKS
                .computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>())
                .add(chunk.getPos().toLong());
    }

    public static boolean isAutoEnabledForChunk(ServerLevel level, LevelChunk chunk) {
        LinkedHashSet<Long> chunks = AUTO_CHUNKS.get(level.dimension());
        return chunks != null && chunks.contains(chunk.getPos().toLong());
    }

    // ── Events ───────────────────────────────────────────────────────────

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getCurrentBiome().isEmpty()) {
            SuccessionService.initializeChunk(chunk);
        }

        ALL_LOADED_CHUNKS
                .computeIfAbsent(serverLevel.dimension(), k -> new LinkedHashSet<>())
                .add(chunk.getPos().toLong());

        if (chunk instanceof LevelChunk levelChunk) {
            TreeGrowthHandler.INSTANCE.onChunkLoad(serverLevel, levelChunk);
        }
    }

    /** Called by TreeGrowthHandler when a tree finishes growing. */
    public static void markChunkHasTreeStructure(ServerLevel level, long chunkPos) {
        TREE_OBSERVE_CHUNKS
                .computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>())
                .add(chunkPos);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        long pos = event.getChunk().getPos().toLong();
        removeFrom(ALL_LOADED_CHUNKS, serverLevel.dimension(), pos);
        removeFrom(AUTO_CHUNKS, serverLevel.dimension(), pos);
        removeFrom(TREE_OBSERVE_CHUNKS, serverLevel.dimension(), pos);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        processTreeGrowth(serverLevel);

        // Per-chunk auto
        LinkedHashSet<Long> autoChunks = AUTO_CHUNKS.get(serverLevel.dimension());
        if (autoChunks != null && !autoChunks.isEmpty()) {
            processChunkSet(serverLevel, autoChunks);
            if (autoChunks.isEmpty()) {
                AUTO_CHUNKS.remove(serverLevel.dimension());
            }
        }

        // Global auto — iterate all loaded chunks
        if (globalAutoEnabled) {
            LinkedHashSet<Long> allChunks = ALL_LOADED_CHUNKS.get(serverLevel.dimension());
            if (allChunks != null && !allChunks.isEmpty()) {
                processChunkSet(serverLevel, allChunks);
                if (allChunks.isEmpty()) {
                    ALL_LOADED_CHUNKS.remove(serverLevel.dimension());
                }
            }
        }
    }

    private static void processChunkSet(ServerLevel level, LinkedHashSet<Long> liveSet) {
        List<Long> snapshot = new ArrayList<>(liveSet);
        for (long chunkPosLong : snapshot) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    net.minecraft.world.level.ChunkPos.getX(chunkPosLong),
                    net.minecraft.world.level.ChunkPos.getZ(chunkPosLong));
            if (chunk == null) {
                liveSet.remove(chunkPosLong);
                continue;
            }

            SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
            if (!SuccessionService.hasActivePath(chunkData)) {
                liveSet.remove(chunkPosLong);
                continue;
            }

            SuccessionService.processChunkTick(level, chunk);
            if (!SuccessionService.hasActivePath(chunkData)) {
                liveSet.remove(chunkPosLong);
            }
        }
    }

    // ── Tree growth ──────────────────────────────────────────────────────

    private static void processTreeGrowth(ServerLevel level) {
        if (!EcofluxServerConfig.gradualTreeGrowth()) {
            return;
        }
        long effectiveInterval = (long) Math.max(1, TREE_GROWTH_TICK_INTERVAL / SuccessionSpeedConfig.getSpeedMultiplier());
        if (level.getGameTime() % effectiveInterval != 0) {
            return;
        }
        TreeGrowthHandler.INSTANCE.tickAll(level);

        // Observe tree-structure records for aging/death, independent of auto mode
        LinkedHashSet<Long> treeChunks = TREE_OBSERVE_CHUNKS.get(level.dimension());
        if (treeChunks != null && !treeChunks.isEmpty()) {
            List<Long> treeSnapshot = new ArrayList<>(treeChunks);
            for (long chunkPosLong : treeSnapshot) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        net.minecraft.world.level.ChunkPos.getX(chunkPosLong),
                        net.minecraft.world.level.ChunkPos.getZ(chunkPosLong));
                if (chunk == null) {
                    treeChunks.remove(chunkPosLong);
                    continue;
                }
                SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
                boolean hasTreeRecords = false;
                for (ActiveVegetationRecord record : new ArrayList<>(chunkData.getVegetationRecords().values())) {
                    if (com.s.ecoflux.plant.TreeStructureAdapter.TYPE_ID.equals(record.adapterType())) {
                        hasTreeRecords = true;
                        com.s.ecoflux.plant.VegetationTracker.INSTANCE.observeTracked(level, chunk, record.position());
                    }
                }
                if (!hasTreeRecords) {
                    treeChunks.remove(chunkPosLong);
                }
            }
            if (treeChunks.isEmpty()) {
                TREE_OBSERVE_CHUNKS.remove(level.dimension());
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void removeFrom(Map<ResourceKey<Level>, LinkedHashSet<Long>> map,
                                   ResourceKey<Level> dim, long chunkPos) {
        LinkedHashSet<Long> chunks = map.get(dim);
        if (chunks != null) {
            chunks.remove(chunkPos);
            if (chunks.isEmpty()) {
                map.remove(dim);
            }
        }
    }
}
