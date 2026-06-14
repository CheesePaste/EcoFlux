package com.s.ecoflux.worldgen;

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
import com.s.ecoflux.worldgen.feature.EcofluxTreeFeature;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Scans newly-loaded chunks for world-generation vegetation and registers it
 * with {@code VegetationTracker}.
 *
 * <p>Phase 1 (trees): SC trees are now generated during decoration by
 * {@link EcofluxTreeFeature} and consumed here via {@link #processDecorationTrees}.
 * No BFS-based post-hoc replacement needed.
 *
 * <p>Phase 1b (mushrooms): Detects huge mushrooms in the chunk.
 * Phase 2 (plants/saplings): Detects simple plants via adapters.
 * Phase 3 (cap): Removes over-represented plants to respect maxPlantCount.
 *
 * <p>Called once per chunk from {@code ModChunkEvents.onChunkLoad} when the
 * chunk is first initialized.
 */
public final class WorldGenVegetationScanner {

    private WorldGenVegetationScanner() {}

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

        int scTreeCount = 0;
        int plantCount = 0;

        // Phase 1: Consume SC trees placed by EcofluxTreeFeature during decoration
        scTreeCount += processDecorationTrees(level, chunk, gameTime, chunkSeed, chunkData);

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
                    Random ageRandom = new Random(root.asLong() ^ chunkSeed);
                    double lifespanVariation = 0.8 + ageRandom.nextDouble() * 0.4;
                    long variedMaxAge = (long) (maxAgeTicks * lifespanVariation);
                    long randomAge = ageRandom.nextLong(Math.max(1, variedMaxAge * 17 / 20));
                    long birthTime = gameTime - randomAge;
                    long expireTime = birthTime + variedMaxAge;

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
                            expireTime,
                            plantDef.get().pointValue(),
                            plantDef.get().pointValue() + 1,
                            chunkData.getCurrentBiome().map(key -> key.location()).orElse(null),
                            chunkData.getActivePathId().orElse(null),
                            mushroomStructure);

                    chunkData.trackVegetation(record);
                    scTreeCount++;
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

                    // Skip tree-structure blocks (handled by Phase 1 / decoration)
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

                    // Randomize birth time across 0%–90% of lifespan so world-gen plants
                    // appear at all life stages. Add ±20% lifespan variation to spread
                    // out death events and prevent mass simultaneous lag spikes.
                    if (record.lifeStage() != VegetationLifecycleStage.MATURE
                            && record.lifeStage() != VegetationLifecycleStage.GROWING) {
                        long maxAgeTicks = plantDef.get().maxAgeTicks();
                        Random ageRandom = new Random(pos.asLong() ^ chunkSeed);
                        double lifespanVariation = 0.8 + ageRandom.nextDouble() * 0.4;
                        long variedMaxAge = (long) (maxAgeTicks * lifespanVariation);
                        long randomAge = ageRandom.nextLong(Math.max(1, variedMaxAge * 9 / 10));
                        long birthTime = gameTime - randomAge;
                        record = new ActiveVegetationRecord(
                                record.vegetationId(),
                                record.adapterType(),
                                record.position(),
                                record.lifeStage(),
                                Math.max(0, birthTime),
                                gameTime,
                                birthTime + variedMaxAge,
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
            allRecords.sort(Comparator.<ActiveVegetationRecord>comparingDouble(
                    r -> deviation.getOrDefault(r.vegetationId(), 0.0)).reversed());
            int toRemove = totalBeforeCap - maxPlants;
            Random random = new Random(chunkSeed ^ 0x5EED);
            for (int i = 0; i < toRemove && !allRecords.isEmpty(); i++) {
                int poolSize = Math.min(allRecords.size(), Math.max(1, toRemove * 2));
                int idx = random.nextInt(poolSize);
                ActiveVegetationRecord record = allRecords.get(idx);
                chunkData.removeVegetation(record.position());
                allRecords.remove(idx);
                removedPlants++;
            }
        }

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] scanChunk done: chunk {} sc_trees={} plants={} cap={} removed={}",
                chunk.getPos(), scTreeCount, plantCount, maxPlants, removedPlants);

        if (scTreeCount > 0 || plantCount > 0) {
            ModNetworking.syncChunkToTracking(level, chunk);
        }
    }

    // ── Phase 1: Decoration tree bridge ─────────────────────────────────

    /**
     * Consumes tree placements stored by {@link EcofluxTreeFeature} during decoration
     * and registers them as tracked vegetation in the chunk. Eliminates the need
     * for BFS-based tree detection.
     */
    private static int processDecorationTrees(ServerLevel level, LevelChunk chunk,
                                               long gameTime, long chunkSeed,
                                               SuccessionChunkData chunkData) {
        long chunkKey = chunk.getPos().toLong();
        List<EcofluxTreeFeature.PendingTreePlacement> pending =
                EcofluxTreeFeature.PENDING_TREES.remove(chunkKey);
        if (pending == null || pending.isEmpty()) return 0;

        int count = 0;
        for (var placement : pending) {
            Optional<PlantDefinition> plantDefOpt = PlantRegistry.INSTANCE.getDefinition(placement.speciesId());
            if (plantDefOpt.isEmpty()) continue;
            PlantDefinition plantDef = plantDefOpt.get();

            long maxAgeTicks = plantDef.maxAgeTicks();
            Random ageRandom = new Random(placement.root().asLong() ^ chunkSeed);
            double lifespanVariation = 0.8 + ageRandom.nextDouble() * 0.4;
            long variedMaxAge = (long) (maxAgeTicks * lifespanVariation);
            long randomAge = ageRandom.nextLong(Math.max(1, variedMaxAge * 17 / 20));
            long birthTime = gameTime - randomAge;

            ActiveVegetationRecord record = new ActiveVegetationRecord(
                    placement.speciesId(),
                    TreeStructureAdapter.TYPE_ID,
                    placement.root(),
                    VegetationLifecycleStage.MATURE,
                    birthTime,
                    gameTime,
                    birthTime + variedMaxAge,
                    plantDef.pointValue(),
                    plantDef.pointValue() + 1,
                    chunkData.getCurrentBiome().map(key -> key.location()).orElse(null),
                    chunkData.getActivePathId().orElse(null),
                    placement.structure());

            chunkData.trackVegetation(record);
            count++;
        }

        return count;
    }

    // ── Deviation map for Phase 3 cap ──────────────────────────────────

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

    // ── Mushroom detection ─────────────────────────────────────────────

    private static final int MAX_BFS_SIZE = 4096;

    @javax.annotation.Nullable
    private static BlockState getBlockStateIfLoaded(ServerLevel level, BlockPos pos) {
        LevelChunk c = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (c == null) return null;
        return c.getBlockState(pos);
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

    private static TreeComponent extractMushroomComponent(ServerLevel level, BlockPos startStem, LevelChunk chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        Set<BlockPos> stems = new HashSet<>();
        Queue<BlockPos> stemQueue = new ArrayDeque<>();
        stems.add(startStem);
        stemQueue.add(startStem);

        while (!stemQueue.isEmpty()) {
            BlockPos pos = stemQueue.poll();
            for (BlockPos neighbor : neighbors26(pos)) {
                if (stems.contains(neighbor)) continue;
                if (neighbor.getY() < minY || neighbor.getY() >= maxY) continue;
                BlockState ns = getBlockStateIfLoaded(level, neighbor);
                if (ns == null) continue;
                if (ns.is(Blocks.MUSHROOM_STEM)) {
                    stems.add(neighbor);
                    stemQueue.add(neighbor);
                    if (stems.size() > MAX_BFS_SIZE) return null;
                }
            }
        }

        Set<BlockPos> caps = new HashSet<>();
        for (BlockPos stem : stems) {
            for (BlockPos neighbor : neighbors26(stem)) {
                if (stems.contains(neighbor) || caps.contains(neighbor)) continue;
                if (neighbor.getY() < minY || neighbor.getY() >= maxY) continue;
                BlockState ns = getBlockStateIfLoaded(level, neighbor);
                if (ns == null) continue;
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
