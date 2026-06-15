package com.cp.ecoflux.plant;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.attachment.PlantQueueEntry;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.config.biome.BiomeRules;
import com.cp.ecoflux.config.plant.PathPlantEntry;
import com.cp.ecoflux.config.plant.PlantDefinition;
import com.cp.ecoflux.config.plant.PlantRegistry;
import com.cp.ecoflux.world.ChunkSamplingHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    /** How aggressively effective weights correct toward target composition. */
    private static final double DEVIATION_STRENGTH = 2.0;

    private PlantSpawner() {}

    public static String trySpawnPlant(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
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

        if (state.hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF)) {
            level.setBlock(pos.above(), state.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
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
            long age = (long) (Math.max(0L, gameTime - record.birthGameTime()) * com.cp.ecoflux.config.SuccessionSpeedConfig.getSpeedMultiplier());
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

    public static void ensureQueue(SuccessionChunkData chunkData, BiomeRules rules) {
        if (!chunkData.getPlantQueue().isEmpty() || chunkData.getCurrentPlantCount() >= chunkData.getMaxPlantCount()) {
            return;
        }

        chunkData.replacePlantQueue(buildWeightedQueue(rules, new Random(), chunkData));
    }

    public static void fillPlants(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            BiomeRules rules,
            int targetPlantCount,
            int maxNewPlants) {
        int clampedTarget = Mth.clamp(targetPlantCount, 0, chunkData.getMaxPlantCount());
        int guard = Math.max(8, clampedTarget * 4);
        int planted = 0;
        while (chunkData.getCurrentPlantCount() < clampedTarget && planted < maxNewPlants && guard-- > 0) {
            ensureQueue(chunkData, rules);
            int before = chunkData.getCurrentPlantCount();
            trySpawnPlant(level, chunk, chunkData, level.getGameTime() + guard);
            if (chunkData.getCurrentPlantCount() > before) {
                planted++;
            }
            if (chunkData.getCurrentPlantCount() == before && chunkData.getPlantQueue().isEmpty()) {
                break;
            }
        }
    }

    /**
     * Builds a weighted queue for a brand-new chunk (no existing plants).
     * Uses raw config weights since there's nothing to deviate from.
     */
    public static List<PlantQueueEntry> buildWeightedQueue(BiomeRules rules, Random random) {
        return buildWeightedQueue(rules, random, null);
    }

    /**
     * Builds a deviation-aware weighted queue. When {@code chunkData} is non-null,
     * effective weights are adjusted so the next batch of spawns pushes the chunk's
     * actual plant composition toward the target composition defined by config weights.
     */
    public static List<PlantQueueEntry> buildWeightedQueue(BiomeRules rules, Random random, SuccessionChunkData chunkData) {
        List<PathPlantEntry> plants = rules.plants();
        int queueCapacity = rules.queueCapacity();
        Map<ResourceLocation, Integer> effectiveWeights = computeEffectiveWeights(rules, chunkData);
        int totalWeight = effectiveWeights.values().stream().mapToInt(Integer::intValue).sum();

        List<PlantQueueEntry> queue = new ArrayList<>(queueCapacity);
        for (int i = 0; i < queueCapacity; i++) {
            PathPlantEntry entry = pickWeightedPlant(plants, effectiveWeights, totalWeight, random);
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

    /**
     * Computes deviation-adjusted effective weights per plant type.
     * Returns a map from plant_id → effective weight.
     *
     * <p>For each plant: effective = originalWeight × (1 + (targetShare − actualShare) × strength),
     * floored at 1. Under-represented plants get boosted; over-represented plants get suppressed.
     */
    public static Map<ResourceLocation, Integer> computeEffectiveWeights(BiomeRules rules, SuccessionChunkData chunkData) {
        List<PathPlantEntry> plants = rules.plants();
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();

        // Start with raw weights
        for (PathPlantEntry entry : plants) {
            result.put(entry.plantId(), entry.weight());
        }

        if (chunkData == null) {
            return result;
        }

        int totalWeight = result.values().stream().mapToInt(Integer::intValue).sum();
        int totalPlants = chunkData.getCurrentPlantCount();
        if (totalPlants == 0 || totalWeight == 0) {
            return result;
        }

        // Count current plants by vegetation ID
        Map<ResourceLocation, Integer> currentCounts = new LinkedHashMap<>();
        for (var record : chunkData.getVegetationRecords().values()) {
            currentCounts.merge(record.vegetationId(), 1, Integer::sum);
        }

        // Compute deviation-adjusted weights
        for (PathPlantEntry entry : plants) {
            double targetShare = (double) entry.weight() / totalWeight;
            double actualShare = (double) currentCounts.getOrDefault(entry.plantId(), 0) / totalPlants;
            double bias = 1.0 + (targetShare - actualShare) * DEVIATION_STRENGTH;
            int effectiveWeight = Math.max(1, (int) Math.round(entry.weight() * bias));
            result.put(entry.plantId(), effectiveWeight);
        }

        return result;
    }

    public static PlantQueueEntry toQueueEntry(PathPlantEntry entry, PlantDefinition plant) {
        return new PlantQueueEntry(plant.plantId(), plant.pointValue(), entry.weight(), plant.maxAgeTicks());
    }

    public static void forceRefillQueue(SuccessionChunkData chunkData, BiomeRules rules) {
        chunkData.replacePlantQueue(buildWeightedQueue(rules, new Random(), chunkData));
    }

    public static String getQueueSummary(SuccessionChunkData chunkData) {
        var queue = chunkData.getPlantQueue();
        if (queue.isEmpty()) {
            return "队列=空";
        }

        var counts = new LinkedHashMap<ResourceLocation, Integer>();
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

    public static PathPlantEntry pickWeightedPlant(List<PathPlantEntry> plants, Map<ResourceLocation, Integer> effectiveWeights, int totalWeight, Random random) {
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (PathPlantEntry plant : plants) {
            cursor += effectiveWeights.getOrDefault(plant.plantId(), plant.weight());
            if (roll < cursor) {
                return plant;
            }
        }
        return plants.get(plants.size() - 1);
    }

    /** Fallback for callers that don't use effective weights (e.g. old queue rebuilds). */
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
