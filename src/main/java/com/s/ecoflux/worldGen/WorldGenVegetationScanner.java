package com.s.ecoflux.worldGen;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.biome.BiomeRules;
import com.s.ecoflux.config.biome.BiomeRulesRegistry;
import com.s.ecoflux.config.plant.PathPlantEntry;
import com.s.ecoflux.config.plant.PlantDefinition;
import com.s.ecoflux.config.plant.PlantRegistry;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.network.ModNetworking;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import com.s.ecoflux.plant.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Scans newly-loaded chunks for world-generation vegetation and registers it
 * with {@code VegetationTracker}. This addresses the gap where plants and trees
 * placed by terrain decoration during world-gen are never intercepted.
 *
 * <p>Called once per chunk from {@code ModChunkEvents.onChunkLoad} when the
 * chunk is first initialized (currentBiome was empty before initialization).
 */
public final class WorldGenVegetationScanner {

    private WorldGenVegetationScanner() {}

    /**
     * Scans the chunk for existing vegetation and registers it for tracking.
     * Safe to call multiple times — skips already-tracked positions.
     */
    public static void scanChunk(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        long gameTime = level.getGameTime();
        long chunkSeed = chunk.getPos().toLong();
        Set<BlockPos> trackedPositions = chunkData.getVegetationRecords().keySet();
        Set<BlockPos> treeProcessed = new HashSet<>();

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int chunkX = chunk.getPos().getMinBlockX();
        int chunkZ = chunk.getPos().getMinBlockZ();

        int treeCount = 0;
        int plantCount = 0;

        // Phase 1: Detect mature trees (connected logs + leaves)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    if (trackedPositions.contains(pos) || treeProcessed.contains(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.LOGS)) {
                        continue;
                    }

                    TreeComponent tree = extractTreeComponent(level, pos, chunk);
                    if (tree == null || tree.leaves.isEmpty()) {
                        treeProcessed.addAll(tree != null ? tree.logs : Set.of(pos));
                        continue;
                    }

                    treeProcessed.addAll(tree.logs);
                    treeProcessed.addAll(tree.leaves);

                    BlockPos root = findLowest(tree.logs);
                    BlockState rootState = level.getBlockState(root);
                    ResourceLocation logId = BuiltInRegistries.BLOCK.getKey(rootState.getBlock());
                    Optional<PlantDefinition> plantDef = PlantRegistry.INSTANCE.getDefinition(logId);

                    if (plantDef.isEmpty()) {
                        continue;
                    }

                    long maxAgeTicks = plantDef.get().maxAgeTicks();
                    long matureDuration = maxAgeTicks / 3;
                    long randomAge = matureDuration > 1
                            ? new Random(root.asLong() ^ chunkSeed).nextLong(matureDuration)
                            : 0;
                    long birthTime = gameTime - randomAge;

                    long[] logArray = tree.logs.stream().mapToLong(BlockPos::asLong).toArray();
                    long[] leafArray = tree.leaves.stream().mapToLong(BlockPos::asLong).toArray();
                    TreeStructure treeStructure = new TreeStructure(logArray, leafArray);

                    ActiveVegetationRecord record = new ActiveVegetationRecord(
                            logId,
                            TreeStructureAdapter.TYPE_ID,
                            root.immutable(),
                            VegetationLifecycleStage.MATURE,
                            birthTime,
                            gameTime,
                            birthTime + maxAgeTicks,
                            plantDef.get().pointValue(),
                            plantDef.get().pointValue() + 1,
                            chunkData.getCurrentBiome().map(key -> key.location()).orElse(null),
                            chunkData.getActivePathId().orElse(null),
                            treeStructure);

                    chunkData.trackVegetation(record);
                    treeCount++;
                }
            }
        }

        // Phase 1b: Detect huge mushrooms (stem + cap blocks)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    if (trackedPositions.contains(pos) || treeProcessed.contains(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.MUSHROOM_STEM)) {
                        continue;
                    }

                    TreeComponent mushroom = extractMushroomComponent(level, pos, chunk);
                    if (mushroom == null || mushroom.leaves.isEmpty()) {
                        treeProcessed.addAll(mushroom != null ? mushroom.logs : Set.of(pos));
                        continue;
                    }

                    treeProcessed.addAll(mushroom.logs);
                    treeProcessed.addAll(mushroom.leaves);

                    BlockPos root = findLowest(mushroom.logs);
                    ResourceLocation mushroomType = inferMushroomCapType(level, mushroom.leaves);
                    Optional<PlantDefinition> plantDef = mushroomType != null
                            ? PlantRegistry.INSTANCE.getDefinition(mushroomType)
                            : Optional.empty();

                    if (plantDef.isEmpty()) {
                        continue;
                    }

                    long maxAgeTicks = plantDef.get().maxAgeTicks();
                    long matureDuration = maxAgeTicks / 3;
                    long randomAge = matureDuration > 1
                            ? new Random(root.asLong() ^ chunkSeed).nextLong(matureDuration)
                            : 0;
                    long birthTime = gameTime - randomAge;

                    long[] stemArray = mushroom.logs.stream().mapToLong(BlockPos::asLong).toArray();
                    long[] capArray = mushroom.leaves.stream().mapToLong(BlockPos::asLong).toArray();
                    TreeStructure mushroomStructure = new TreeStructure(stemArray, capArray);

                    ActiveVegetationRecord record = new ActiveVegetationRecord(
                            mushroomType,
                            TreeStructureAdapter.TYPE_ID,
                            root.immutable(),
                            VegetationLifecycleStage.MATURE,
                            birthTime,
                            gameTime,
                            birthTime + maxAgeTicks,
                            plantDef.get().pointValue(),
                            plantDef.get().pointValue() + 1,
                            chunkData.getCurrentBiome().map(key -> key.location()).orElse(null),
                            chunkData.getActivePathId().orElse(null),
                            mushroomStructure);

                    chunkData.trackVegetation(record);
                    treeCount++;
                }
            }
        }

        // Phase 2: Detect simple plants and saplings
        Optional<ResourceLocation> sourceBiomeId = chunkData.getCurrentBiome()
                .map(key -> key.location());
        Optional<ResourceLocation> sourcePathId = chunkData.getActivePathId();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    if (trackedPositions.contains(pos) || treeProcessed.contains(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    Optional<VegetationTypeAdapter> adapter = VegetationTracker.INSTANCE.findAdapter(state);
                    if (adapter.isEmpty()) {
                        continue;
                    }

                    // Skip tree-structure blocks (already handled in Phase 1)
                    if (adapter.get() instanceof TreeStructureAdapter) {
                        continue;
                    }

                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    Optional<PlantDefinition> plantDef = PlantRegistry.INSTANCE.getDefinition(blockId);
                    if (plantDef.isEmpty()) {
                        continue;
                    }

                    ActiveVegetationRecord record = adapter.get().captureBirth(
                            level, pos, state, gameTime,
                            sourceBiomeId, sourcePathId, plantDef.get());

                    // Randomize birth time so world-gen plants don't all age/die at once.
                    // For gradual growth, shift birth time back to put plants in a
                    // pseudorandom stage. Non-gradual mode already sets MATURE.
                    if (record.lifeStage() != VegetationLifecycleStage.MATURE
                            && record.lifeStage() != VegetationLifecycleStage.GROWING) {
                        long maxAgeTicks = plantDef.get().maxAgeTicks();
                        // Place the plant somewhere in its mid-life (20%–80% of maxAge)
                        long offset = maxAgeTicks / 5;
                        long range = Math.max(1, maxAgeTicks * 3 / 5);
                        long randomAge = offset + new Random(pos.asLong() ^ chunkSeed).nextLong(range);
                        long birthTime = gameTime - randomAge;
                        record = new ActiveVegetationRecord(
                                record.vegetationId(),
                                record.adapterType(),
                                record.position(),
                                record.lifeStage(),
                                Math.max(0, birthTime),
                                gameTime,
                                birthTime + maxAgeTicks,
                                record.basePointValue(),
                                record.currentPointValue(),
                                record.sourceBiomeId(),
                                record.sourcePathId(),
                                record.treeStructure());
                    }

                    chunkData.trackVegetation(record);

                    // Handle upper half of double-height plants
                    if (state.hasProperty(DoublePlantBlock.HALF)
                            && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
                        BlockPos upperPos = pos.above();
                        BlockState upperState = level.getBlockState(upperPos);
                        if (VegetationTracker.INSTANCE.findAdapter(upperState).isPresent()) {
                            ActiveVegetationRecord upperRecord = new ActiveVegetationRecord(
                                    record.vegetationId(),
                                    record.adapterType(),
                                    upperPos.immutable(),
                                    record.lifeStage(),
                                    record.birthGameTime(),
                                    record.lastObservedGameTime(),
                                    record.expireGameTime(),
                                    0,
                                    0,
                                    record.sourceBiomeId(),
                                    record.sourcePathId(),
                                    null);
                            chunkData.trackVegetation(upperRecord);
                        }
                    }

                    plantCount++;
                }
            }
        }

        // Phase 3: Cap to maxPlantCount, prioritizing removal of over-represented types
        int totalBeforeCap = chunkData.getCurrentPlantCount();
        int maxPlants = chunkData.getMaxPlantCount();
        int removedPlants = 0;
        if (maxPlants > 0 && totalBeforeCap > maxPlants) {
            Map<ResourceLocation, Double> deviation = buildDeviationMap(chunkData, chunkSeed);
            List<ActiveVegetationRecord> allRecords = new ArrayList<>(chunkData.getVegetationRecords().values());
            // Sort by deviation descending: over-represented plants removed first
            allRecords.sort(Comparator.<ActiveVegetationRecord>comparingDouble(
                    r -> deviation.getOrDefault(r.vegetationId(), 0.0)).reversed());
            int toRemove = totalBeforeCap - maxPlants;
            Random random = new Random(chunkSeed ^ 0x5EED);
            for (int i = 0; i < toRemove && !allRecords.isEmpty(); i++) {
                // Pick from the most over-represented with some randomness
                int poolSize = Math.min(allRecords.size(), Math.max(1, toRemove * 2));
                int idx = random.nextInt(poolSize);
                ActiveVegetationRecord record = allRecords.get(idx);
                chunkData.removeVegetation(record.position());
                allRecords.remove(idx);
                removedPlants++;
            }
        }

        if (treeCount > 0 || plantCount > 0) {
            EcofluxConstants.LOGGER.info(
                    "[Ecoflux] World-gen scan chunk {}: {} trees, {} plants registered (cap={}, removed={})",
                    chunk.getPos(), treeCount, plantCount, maxPlants, removedPlants);
            ModNetworking.syncChunkToTracking(level, chunk);
        }
    }

    /**
     * Computes how over- or under-represented each plant type is relative to biome rules.
     * Positive = over-represented (should be removed first). Negative = under-represented.
     */
    private static Map<ResourceLocation, Double> buildDeviationMap(SuccessionChunkData chunkData, long seed) {
        Map<ResourceLocation, Double> deviation = new HashMap<>();
        Optional<BiomeRules> rulesOpt = chunkData.getActiveBiomeRulesId()
                .flatMap(BiomeRulesRegistry::getRules);
        if (rulesOpt.isEmpty()) {
            return deviation;
        }
        BiomeRules rules = rulesOpt.get();

        int totalWeight = rules.plants().stream().mapToInt(PathPlantEntry::weight).sum();
        int totalPlants = chunkData.getCurrentPlantCount();
        if (totalWeight == 0 || totalPlants == 0) {
            return deviation;
        }

        Map<ResourceLocation, Integer> currentCounts = new HashMap<>();
        for (var record : chunkData.getVegetationRecords().values()) {
            currentCounts.merge(record.vegetationId(), 1, Integer::sum);
        }

        for (PathPlantEntry entry : rules.plants()) {
            double targetShare = (double) entry.weight() / totalWeight;
            double actualShare = (double) currentCounts.getOrDefault(entry.plantId(), 0) / totalPlants;
            deviation.put(entry.plantId(), actualShare - targetShare);
        }
        return deviation;
    }

    // ── Tree detection ────────────────────────────────────────────────────

    /**
     * Extracts a connected tree component (logs + adjacent leaves) starting from a log block.
     * Returns null if the component is trivial (single log with no leaves).
     */
    private static TreeComponent extractTreeComponent(ServerLevel level, BlockPos startLog, LevelChunk chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        // BFS through connected logs
        Set<BlockPos> logs = new HashSet<>();
        Queue<BlockPos> logQueue = new ArrayDeque<>();
        logs.add(startLog);
        logQueue.add(startLog);

        while (!logQueue.isEmpty()) {
            BlockPos pos = logQueue.poll();
            for (BlockPos neighbor : neighbors26(pos)) {
                if (logs.contains(neighbor)) continue;
                // Restrict to current chunk to avoid cascading chunk loads
                if (!inChunk(neighbor, chunkMinX, chunkMaxX, minY, maxY, chunkMinZ, chunkMaxZ)) continue;
                BlockState ns = level.getBlockState(neighbor);
                if (ns.is(BlockTags.LOGS)) {
                    logs.add(neighbor);
                    logQueue.add(neighbor);
                }
            }
        }

        // BFS through leaves adjacent to log component
        Set<BlockPos> leaves = new HashSet<>();
        Queue<BlockPos> leafQueue = new ArrayDeque<>();
        for (BlockPos log : logs) {
            for (BlockPos neighbor : neighbors26(log)) {
                if (logs.contains(neighbor) || leaves.contains(neighbor)) continue;
                if (!inChunk(neighbor, chunkMinX, chunkMaxX, minY, maxY, chunkMinZ, chunkMaxZ)) continue;
                BlockState ns = level.getBlockState(neighbor);
                if (ns.is(BlockTags.LEAVES)) {
                    leaves.add(neighbor);
                    leafQueue.add(neighbor);
                }
            }
        }

        while (!leafQueue.isEmpty()) {
            BlockPos pos = leafQueue.poll();
            for (BlockPos neighbor : neighbors26(pos)) {
                if (logs.contains(neighbor) || leaves.contains(neighbor)) continue;
                if (!inChunk(neighbor, chunkMinX, chunkMaxX, minY, maxY, chunkMinZ, chunkMaxZ)) continue;
                BlockState ns = level.getBlockState(neighbor);
                if (ns.is(BlockTags.LEAVES)) {
                    leaves.add(neighbor);
                    leafQueue.add(neighbor);
                }
            }
        }

        return new TreeComponent(logs, leaves);
    }

    private static BlockPos findLowest(Set<BlockPos> logs) {
        BlockPos lowest = null;
        for (BlockPos log : logs) {
            if (lowest == null || log.getY() < lowest.getY()) {
                lowest = log;
            }
        }
        return lowest;
    }

    private static boolean inChunk(BlockPos pos, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() < maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static List<BlockPos> neighbors26(BlockPos pos) {
        List<BlockPos> result = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    result.add(pos.offset(dx, dy, dz));
                }
            }
        }
        return result;
    }

    private record TreeComponent(Set<BlockPos> logs, Set<BlockPos> leaves) {}

    /**
     * Extracts a connected huge mushroom component (stems + adjacent cap blocks).
     * Returns null if the component is trivial (single stem with no caps).
     */
    private static TreeComponent extractMushroomComponent(ServerLevel level, BlockPos startStem, LevelChunk chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        Set<BlockPos> stems = new HashSet<>();
        Queue<BlockPos> stemQueue = new ArrayDeque<>();
        stems.add(startStem);
        stemQueue.add(startStem);

        while (!stemQueue.isEmpty()) {
            BlockPos pos = stemQueue.poll();
            for (BlockPos neighbor : neighbors26(pos)) {
                if (stems.contains(neighbor)) continue;
                if (!inChunk(neighbor, chunkMinX, chunkMaxX, minY, maxY, chunkMinZ, chunkMaxZ)) continue;
                if (level.getBlockState(neighbor).is(Blocks.MUSHROOM_STEM)) {
                    stems.add(neighbor);
                    stemQueue.add(neighbor);
                }
            }
        }

        Set<BlockPos> caps = new HashSet<>();
        for (BlockPos stem : stems) {
            for (BlockPos neighbor : neighbors26(stem)) {
                if (stems.contains(neighbor) || caps.contains(neighbor)) continue;
                if (!inChunk(neighbor, chunkMinX, chunkMaxX, minY, maxY, chunkMinZ, chunkMaxZ)) continue;
                BlockState ns = level.getBlockState(neighbor);
                if (ns.is(Blocks.BROWN_MUSHROOM_BLOCK) || ns.is(Blocks.RED_MUSHROOM_BLOCK)) {
                    caps.add(neighbor);
                }
            }
        }

        return new TreeComponent(stems, caps);
    }

    @javax.annotation.Nullable
    private static ResourceLocation inferMushroomCapType(ServerLevel level, Set<BlockPos> caps) {
        int brown = 0;
        int red = 0;
        for (BlockPos pos : caps) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.BROWN_MUSHROOM_BLOCK)) {
                brown++;
            } else if (state.is(Blocks.RED_MUSHROOM_BLOCK)) {
                red++;
            }
        }
        if (brown >= red && brown > 0) {
            return ResourceLocation.withDefaultNamespace("brown_mushroom");
        }
        if (red > brown) {
            return ResourceLocation.withDefaultNamespace("red_mushroom");
        }
        return null;
    }
}
