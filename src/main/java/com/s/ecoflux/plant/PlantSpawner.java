package com.s.ecoflux.plant;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.PlantQueueEntry;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.PathPlantEntry;
import com.s.ecoflux.config.PlantDefinition;
import com.s.ecoflux.config.PlantRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import com.s.ecoflux.config.SuccessionSpeedConfig;
import com.s.ecoflux.world.ChunkSamplingHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PlantSpawner {
    private PlantSpawner() {}

    public static String trySpawnPlant(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            SuccessionPathDefinition path,
            long gameTime) {
        if (chunkData.getCurrentPlantCount() >= chunkData.getMaxPlantCount()) {
            return "区块 " + chunk.getPos() + " 跳过生成：植物数量已达上限。";
        }

        Optional<PlantQueueEntry> nextEntry = chunkData.pollPlant();
        if (nextEntry.isEmpty()) {
            return "区块 " + chunk.getPos() + " 跳过生成：植物队列为空。";
        }

        Optional<PlantDefinition> plantDefinition = PlantRegistry.INSTANCE.getDefinition(nextEntry.get().plantId());
        if (plantDefinition.isEmpty()) {
            return "区块 " + chunk.getPos() + " 生成失败：植物注册表中找不到 " + nextEntry.get().plantId() + "。";
        }

        Optional<BlockPos> spawnPos = ChunkSamplingHelper.findSpawnPos(
                level, chunk, chunkData, plantDefinition.get(), gameTime);
        if (spawnPos.isEmpty()) {
            chunkData.enqueuePlant(nextEntry.get());
            return "区块 " + chunk.getPos() + " 跳过生成：找不到 " + nextEntry.get().plantId() + " 的合法位置。";
        }

        Block block = BuiltInRegistries.BLOCK.getOptional(nextEntry.get().plantId()).orElse(null);
        if (block == null) {
            EcofluxConstants.LOGGER.warn("植物 {} 不是已注册方块", nextEntry.get().plantId());
            return "区块 " + chunk.getPos() + " 生成失败：方块 " + nextEntry.get().plantId() + " 未注册。";
        }

        BlockState state = block.defaultBlockState();
        BlockPos pos = spawnPos.get();
        if (!state.canSurvive(level, pos) || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            chunkData.enqueuePlant(nextEntry.get());
            return "区块 " + chunk.getPos() + " 生成失败：世界拒绝在 " + pos + " 放置。";
        }

        VegetationTracker.INSTANCE.trackAt(
                level,
                chunk,
                pos,
                chunkData.getCurrentBiome().map(ResourceKey::location),
                chunkData.getActivePathId(),
                plantDefinition.get());

        return "已在区块 " + chunk.getPos() + " 的 " + pos + " 种下 " + nextEntry.get().plantId() + "。";
    }

    public static int pruneInvalidPlants(ServerLevel level, SuccessionChunkData chunkData, long gameTime) {
        var snapshot = List.copyOf(chunkData.getVegetationRecords().values());
        int removed = 0;
        for (var record : snapshot) {
            BlockState state = level.getBlockState(record.position());
            long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * SuccessionSpeedConfig.getSpeedMultiplier());
            long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());
            boolean expired = age >= totalLifetime;
            boolean missing = state.isAir()
                    || VegetationTracker.INSTANCE.findAdapter(state).isEmpty();
            if (!expired && !missing) {
                continue;
            }

            if (expired && !state.isAir()) {
                level.removeBlock(record.position(), false);
            }

            chunkData.removeVegetation(record.position());
            removed++;
        }
        return removed;
    }

    public static void ensureQueue(SuccessionChunkData chunkData, SuccessionPathDefinition path) {
        if (!chunkData.getPlantQueue().isEmpty() || chunkData.getCurrentPlantCount() >= chunkData.getMaxPlantCount()) {
            return;
        }

        int capacity = path.chunkRules().queueCapacity();
        chunkData.replacePlantQueue(buildWeightedQueue(path, capacity, new Random()));
    }

    public static void fillPlants(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            SuccessionPathDefinition path,
            int targetPlantCount,
            int maxNewPlants) {
        int clampedTarget = Mth.clamp(targetPlantCount, 0, chunkData.getMaxPlantCount());
        int guard = Math.max(8, clampedTarget * 4);
        int planted = 0;
        while (chunkData.getCurrentPlantCount() < clampedTarget && planted < maxNewPlants && guard-- > 0) {
            ensureQueue(chunkData, path);
            int before = chunkData.getCurrentPlantCount();
            trySpawnPlant(level, chunk, chunkData, path, level.getGameTime() + guard);
            if (chunkData.getCurrentPlantCount() > before) {
                planted++;
            }
            if (chunkData.getCurrentPlantCount() == before && chunkData.getPlantQueue().isEmpty()) {
                break;
            }
        }
    }

    public static List<PlantQueueEntry> buildWeightedQueue(
            SuccessionPathDefinition path,
            int queueCapacity,
            Random random) {
        int totalWeight = path.plants().stream().mapToInt(PathPlantEntry::weight).sum();
        return buildWeightedQueue(path, queueCapacity, random, totalWeight);
    }

    public static List<PlantQueueEntry> buildWeightedQueue(
            SuccessionPathDefinition path,
            int queueCapacity,
            Random random,
            int totalWeight) {
        List<PlantQueueEntry> queue = new ArrayList<>(queueCapacity);
        for (int i = 0; i < queueCapacity; i++) {
            PathPlantEntry entry = pickWeightedPlant(path.plants(), totalWeight, random);
            PlantDefinition def = PlantRegistry.INSTANCE.getDefinition(entry.plantId())
                    .orElse(null);
            if (def == null) {
                EcofluxConstants.LOGGER.warn("植物 {} 未在注册表中找到，跳过队列生成。", entry.plantId());
                continue;
            }
            queue.add(toQueueEntry(entry, def));
        }
        return List.copyOf(queue);
    }

    public static Optional<PlantDefinition> findPlantDefinition(SuccessionPathDefinition path, ResourceLocation plantId) {
        return PlantRegistry.INSTANCE.getDefinition(plantId);
    }

    public static PlantQueueEntry toQueueEntry(PathPlantEntry entry, PlantDefinition plant) {
        return new PlantQueueEntry(plant.plantId(), plant.pointValue(), entry.weight(), plant.maxAgeTicks());
    }

    public static void forceRefillQueue(SuccessionChunkData chunkData, SuccessionPathDefinition path) {
        int capacity = path.chunkRules().queueCapacity();
        chunkData.replacePlantQueue(buildWeightedQueue(path, capacity, new Random()));
    }

    public static String getQueueSummary(SuccessionChunkData chunkData) {
        var queue = chunkData.getPlantQueue();
        if (queue.isEmpty()) {
            return "队列=空";
        }

        var counts = new java.util.LinkedHashMap<ResourceLocation, Integer>();
        for (PlantQueueEntry entry : queue) {
            counts.merge(entry.plantId(), 1, Integer::sum);
        }

        String breakdown = counts.entrySet().stream()
                .map(e -> e.getKey() + "x" + e.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        int totalWeight = queue.stream().mapToInt(PlantQueueEntry::weight).sum();
        return String.format("队列=%d项(总权重=%d)[%s]", queue.size(), totalWeight, breakdown);
    }

    public static PathPlantEntry pickWeightedPlant(List<PathPlantEntry> plants, int totalWeight, Random random) {
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (PathPlantEntry plant : plants) {
            cursor += plant.weight();
            if (roll < cursor) {
                return plant;
            }
        }
        return plants.get(plants.size() - 1);
    }
}
