package com.s.ecoflux.plant;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.PlantQueueEntry;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.PlantDefinition;
import com.s.ecoflux.config.SuccessionPathDefinition;
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
    private PlantSpawner() {
    }

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

        Optional<PlantDefinition> plantDefinition = findPlantDefinition(path, nextEntry.get().plantId());
        if (plantDefinition.isEmpty()) {
            return "区块 " + chunk.getPos() + " 生成失败：路径中找不到植物 " + nextEntry.get().plantId() + "。";
        }

        Optional<BlockPos> spawnPos = ChunkSamplingHelper.findSpawnPos(
                level, chunk, chunkData, plantDefinition.get(), gameTime);
        if (spawnPos.isEmpty()) {
            EcofluxConstants.LOGGER.debug(
                    "区块 {} 跳过植物生成：找不到 {} 的合法位置",
                    chunk.getPos(),
                    nextEntry.get().plantId());
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
                chunkData.getActivePathId());

        EcofluxConstants.LOGGER.debug(
                "已在区块 {} 的 {} 种下植物 {}",
                chunk.getPos(),
                pos,
                nextEntry.get().plantId());
        return "已在区块 " + chunk.getPos() + " 的 " + pos + " 种下 " + nextEntry.get().plantId() + "。";
    }

    public static int pruneInvalidPlants(ServerLevel level, SuccessionChunkData chunkData, long gameTime) {
        var snapshot = List.copyOf(chunkData.getVegetationRecords().values());
        int removed = 0;
        for (var record : snapshot) {
            BlockState state = level.getBlockState(record.position());
            boolean expired = gameTime >= record.expireGameTime();
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
        int totalWeight = path.plants().stream().mapToInt(PlantDefinition::weight).sum();
        return buildWeightedQueue(path, queueCapacity, random, totalWeight);
    }

    public static List<PlantQueueEntry> buildWeightedQueue(
            SuccessionPathDefinition path,
            int queueCapacity,
            Random random,
            int totalWeight) {
        List<PlantQueueEntry> queue = new ArrayList<>(queueCapacity);
        for (int i = 0; i < queueCapacity; i++) {
            PlantDefinition plant = pickWeightedPlant(path.plants(), totalWeight, random);
            queue.add(toQueueEntry(plant));
        }
        return List.copyOf(queue);
    }

    public static Optional<PlantDefinition> findPlantDefinition(SuccessionPathDefinition path, ResourceLocation plantId) {
        return path.plants().stream()
                .filter(plant -> plant.plantId().equals(plantId))
                .findFirst();
    }

    public static PlantQueueEntry toQueueEntry(PlantDefinition plant) {
        return new PlantQueueEntry(plant.plantId(), plant.pointValue(), plant.weight(), plant.maxAgeTicks());
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

    public static PlantDefinition pickWeightedPlant(List<PlantDefinition> plants, int totalWeight, Random random) {
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (PlantDefinition plant : plants) {
            cursor += plant.weight();
            if (roll < cursor) {
                return plant;
            }
        }
        return plants.get(plants.size() - 1);
    }
}
