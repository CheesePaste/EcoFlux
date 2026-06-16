package com.cp.ecoflux.worldgen;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.api.data.ActiveVegetationRecord;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.api.config.PlantDefinition;
import com.cp.ecoflux.config.plant.PlantRegistry;
import com.cp.ecoflux.init.ModAttachments;
import com.cp.ecoflux.network.ModNetworking;
import com.cp.ecoflux.plant.TreeStructure;
import com.cp.ecoflux.api.data.VegetationLifecycleStage;
import com.cp.ecoflux.plant.VegetationTracker;
import com.cp.ecoflux.plant.adapters.TreeStructureAdapter;
import com.cp.ecoflux.api.adapter.VegetationTypeAdapter;
import com.cp.ecoflux.worldgen.feature.EcofluxTreeFeature;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Scans newly-loaded chunks for world-generation vegetation and registers it
 * with {@code VegetationTracker}.
 *
 * <p>Phase 1 (trees): SC trees are now generated during decoration by
 * {@link EcofluxTreeFeature} and consumed here via {@link #processDecorationTrees}.
 * No BFS-based post-hoc replacement needed.
 *
 * <p>Phase 1b (mushrooms): Detects huge mushrooms via {@link MushroomScanner}.
 * Phase 2 (plants/saplings): Detects simple plants via adapters.
 * Phase 3 (cap): Removes over-represented plants via {@link DensityCapper}.
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
        if (!net.neoforged.fml.ModList.get().isLoaded("dynamictrees")) {
            scTreeCount += processDecorationTrees(level, chunk, gameTime, chunkSeed, chunkData);
        }

        // Phase 1b + Phase 2 merged: heightmap-bounded single pass
        Optional<ResourceLocation> sourceBiomeId = chunkData.getCurrentBiome()
                .map(key -> key.location());
        Optional<ResourceLocation> sourcePathId = chunkData.getActivePathId();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                int scanMin = Math.max(minY, surfaceY - 5);
                int scanMax = Math.min(maxY - 1, surfaceY + 40);

                for (int y = scanMin; y <= scanMax; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    if (trackedPositions.contains(pos) || treeProcessed.contains(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);

                    // ── Mushroom stem detection (Phase 1b) ──
                    if (state.is(Blocks.MUSHROOM_STEM)) {
                        MushroomScanner.TreeComponent mushroom = MushroomScanner.extract(level, pos, chunk);
                        if (mushroom != null && !mushroom.leaves().isEmpty()) {
                            treeProcessed.addAll(mushroom.logs());
                            treeProcessed.addAll(mushroom.leaves());

                            BlockPos root = MushroomScanner.findLowest(mushroom.logs());
                            ResourceLocation mushroomType = MushroomScanner.inferCapType(level, mushroom.leaves());
                            Optional<PlantDefinition> plantDef = mushroomType != null
                                    ? PlantRegistry.INSTANCE.getDefinition(mushroomType)
                                    : Optional.empty();

                            if (plantDef.isPresent()) {
                                long maxAgeTicks = plantDef.get().maxAgeTicks();
                                Random ageRandom = new Random(root.asLong() ^ chunkSeed);
                                double lifespanVariation = 0.8 + ageRandom.nextDouble() * 0.4;
                                long variedMaxAge = (long) (maxAgeTicks * lifespanVariation);
                                long randomAge = ageRandom.nextLong(Math.max(1, variedMaxAge * 17 / 20));
                                long birthTime = gameTime - randomAge;
                                long expireTime = birthTime + variedMaxAge;

                                long[] stemArray = mushroom.logs().stream().mapToLong(BlockPos::asLong).toArray();
                                long[] capArray = mushroom.leaves().stream().mapToLong(BlockPos::asLong).toArray();
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
                        } else {
                            treeProcessed.addAll(mushroom != null ? mushroom.logs() : Set.of(pos));
                        }
                        continue;
                    }

                    // ── Simple plant / sapling detection (Phase 2) ──
                    Optional<VegetationTypeAdapter> adapter = VegetationTracker.INSTANCE.findAdapter(state);
                    if (adapter.isEmpty()) {
                        continue;
                    }

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

        // Phase 3: Cap to maxPlantCount
        int maxPlants = chunkData.getMaxPlantCount();
        int removedPlants = DensityCapper.cap(chunkData, maxPlants, chunkSeed);

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] scanChunk done: chunk {} sc_trees={} plants={} cap={} removed={}",
                chunk.getPos(), scTreeCount, plantCount, maxPlants, removedPlants);

        if (scTreeCount > 0 || plantCount > 0) {
            ModNetworking.syncChunkToTracking(level, chunk);
        }
    }

    // ── Phase 1: Decoration tree bridge ─────────────────────────────────

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
}
