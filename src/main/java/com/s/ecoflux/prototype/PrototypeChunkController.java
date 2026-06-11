package com.s.ecoflux.prototype;

/**
 * Accelerated demo-mode controller for the 10-second succession showcase.
 *
 * <p>Structure: static utility class with {@code accelerate()} to kick off a
 * sped-up succession run on a chunk, and {@code processAcceleratedTick()} to
 * advance it each tick. Internally manages synthetic vegetation lifecycle stages
 * (born, growing, mature, aging) and plant population growth mapped to progress
 * (0.0-1.0), culminating in a biome transition at progress 1.0.
 *
 * <p>Role in Ecoflux: provides a visually compelling demonstration of the full
 * succession cycle condensed into ~10 seconds. All standard succession logic has
 * been extracted to the {@code succession/} service layer; this class only houses
 * the accelerated timeline and synthetic stage progression.
 */

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.init.ModChunkEvents;
import com.s.ecoflux.network.ModNetworking;
import com.s.ecoflux.plant.PlantSpawner;
import com.s.ecoflux.plant.VegetationLifecycleStage;
import com.s.ecoflux.succession.BiomeTransitionService;
import com.s.ecoflux.succession.SuccessionService;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PrototypeChunkController {
    public static final ResourceLocation PROTOTYPE_PATH_ID = EcofluxConstants.id("plains_to_forest");
    private static final int ACCELERATED_SYNC_INTERVAL_TICKS = 5;
    private static final int ACCELERATED_PLANT_INTERVAL_TICKS = 12;
    private static final long SIMPLE_PLANT_BORN_TICKS = 200L;
    private static final long SIMPLE_PLANT_GROWING_START_TICKS = 200L;
    private static final long SIMPLE_PLANT_MATURE_START_TICKS = 1200L;
    private static final long SIMPLE_PLANT_AGING_START_TICKS = 48000L;
    private static final long SIMPLE_PLANT_EXPIRE_TICKS = 72000L;

    private PrototypeChunkController() {
    }

    public static String accelerate(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getCurrentBiome().isEmpty()) {
            SuccessionService.initializeChunk(chunk);
        }

        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有可加速的演替路径。";
        }

        PlantSpawner.pruneInvalidPlants(level, chunkData, level.getGameTime());
        PlantSpawner.ensureQueue(chunkData, pathOptional.get());
        if (chunkData.getVegetationRecords().isEmpty()) {
            PlantSpawner.fillPlants(level, chunk, chunkData, pathOptional.get(), 2, 2);
        }

        if (chunkData.getVegetationRecords().isEmpty()) {
            return "区块 " + chunk.getPos() + " 无法启动加速：没有可追踪的小草。";
        }

        chunkData.setProgress(0.0D);
        setAcceleratedVegetationStage(chunkData, level.getGameTime(), 0.0D);
        ModNetworking.syncChunkToTracking(level, chunk);
        ModChunkEvents.startAcceleratedTransition(level, chunk);
        return "已启动区块 " + chunk.getPos() + " 的 10 秒完整加速演替：小草会依次经历出生、生长、成熟、衰老，然后转化为森林。";
    }

    public static boolean processAcceleratedTick(ServerLevel level, LevelChunk chunk, long startGameTime, int durationTicks) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return true;
        }

        long elapsedTicks = Math.max(0L, level.getGameTime() - startGameTime);
        double progress = Mth.clamp((double) elapsedTicks / (double) Math.max(1, durationTicks), 0.0D, 1.0D);
        if (chunkData.getVegetationRecords().isEmpty()) {
            PlantSpawner.ensureQueue(chunkData, pathOptional.get());
            PlantSpawner.fillPlants(level, chunk, chunkData, pathOptional.get(), 2, 1);
        }

        int targetPlantCount = acceleratedTargetPlantCount(chunkData, progress);
        if (elapsedTicks % ACCELERATED_PLANT_INTERVAL_TICKS == 0L) {
            PlantSpawner.fillPlants(level, chunk, chunkData, pathOptional.get(), targetPlantCount, 1);
        }
        setAcceleratedVegetationStage(chunkData, level.getGameTime(), progress);
        chunkData.setProgress(Math.min(0.99D, progress));
        if (elapsedTicks % ACCELERATED_SYNC_INTERVAL_TICKS == 0L) {
            ModNetworking.syncChunkToTracking(level, chunk);
        }

        if (progress < 1.0D) {
            return false;
        }

        chunkData.setProgress(1.0D);
        ModNetworking.syncChunkToTracking(level, chunk);
        BiomeTransitionService.applyTransition(level, chunk, chunkData);
        return true;
    }

    private static Optional<SuccessionPathDefinition> getActivePath(SuccessionChunkData chunkData, net.minecraft.world.level.ChunkPos chunkPos) {
        Optional<ResourceLocation> activePathId = chunkData.getActivePathId();
        if (activePathId.isEmpty()) {
            return Optional.empty();
        }

        Optional<SuccessionPathDefinition> pathOptional = SuccessionConfigRegistry.getPath(activePathId.get());
        if (pathOptional.isEmpty()) {
            EcofluxConstants.LOGGER.warn("区块 {} 期间，演替路径 {} 已不存在", chunkPos, activePathId.get());
        }
        return pathOptional;
    }

    private static void setAcceleratedVegetationStage(SuccessionChunkData chunkData, long gameTime, double totalProgress) {
        AcceleratedVisualStage visualStage = acceleratedVisualStage(totalProgress);
        long syntheticAge = visualStage.syntheticAge();
        long syntheticBirthTime = Math.max(0L, gameTime - syntheticAge);
        List<ActiveVegetationRecord> vegetationSnapshot = List.copyOf(chunkData.getVegetationRecords().values());
        for (ActiveVegetationRecord record : vegetationSnapshot) {
            chunkData.trackVegetation(new ActiveVegetationRecord(
                    record.vegetationId(),
                    record.adapterType(),
                    record.category(),
                    record.position(),
                    visualStage.stage(),
                    syntheticBirthTime,
                    gameTime,
                    syntheticBirthTime + SIMPLE_PLANT_EXPIRE_TICKS,
                    record.basePointValue(),
                    visualStage.pointValue(record.basePointValue()),
                    record.sourceBiomeId(),
                    record.sourcePathId(),
                    record.treeStructure()));
        }
    }

    private static AcceleratedVisualStage acceleratedVisualStage(double totalProgress) {
        if (totalProgress < 0.20D) {
            double localProgress = totalProgress / 0.20D;
            return new AcceleratedVisualStage(
                    VegetationLifecycleStage.BORN,
                    Math.round(localProgress * (SIMPLE_PLANT_BORN_TICKS - 1L)),
                    0);
        }
        if (totalProgress < 0.50D) {
            double localProgress = (totalProgress - 0.20D) / 0.30D;
            long age = SIMPLE_PLANT_GROWING_START_TICKS
                    + Math.round(localProgress * (SIMPLE_PLANT_MATURE_START_TICKS - SIMPLE_PLANT_GROWING_START_TICKS - 1L));
            return new AcceleratedVisualStage(VegetationLifecycleStage.GROWING, age, 1);
        }
        if (totalProgress < 0.80D) {
            double localProgress = (totalProgress - 0.50D) / 0.30D;
            long age = SIMPLE_PLANT_MATURE_START_TICKS
                    + Math.round(localProgress * (SIMPLE_PLANT_AGING_START_TICKS - SIMPLE_PLANT_MATURE_START_TICKS - 1L));
            return new AcceleratedVisualStage(VegetationLifecycleStage.MATURE, age, 2);
        }

        double localProgress = (totalProgress - 0.80D) / 0.20D;
        long age = SIMPLE_PLANT_AGING_START_TICKS
                + Math.round(localProgress * (SIMPLE_PLANT_EXPIRE_TICKS - SIMPLE_PLANT_AGING_START_TICKS - 1L));
        return new AcceleratedVisualStage(VegetationLifecycleStage.AGING, age, 1);
    }

    private static int acceleratedTargetPlantCount(SuccessionChunkData chunkData, double totalProgress) {
        int maxPlantCount = chunkData.getMaxPlantCount();
        if (totalProgress < 0.20D) {
            return Math.max(1, maxPlantCount / 4);
        }
        if (totalProgress < 0.45D) {
            return Math.max(2, maxPlantCount / 2);
        }
        if (totalProgress < 0.70D) {
            return Math.max(3, (maxPlantCount * 3) / 4);
        }
        return maxPlantCount;
    }

    private record AcceleratedVisualStage(VegetationLifecycleStage stage, long syntheticAge, int pointBonus) {
        int pointValue(int basePointValue) {
            return Math.max(1, basePointValue + pointBonus);
        }
    }
}
