package com.s.ecoflux.world;

import com.s.ecoflux.attachment.ActivePlantRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.PlantDefinition;
import com.s.ecoflux.config.PlantSpawnRules;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

public final class ChunkSamplingHelper {
    private ChunkSamplingHelper() {
    }

    public static Optional<ResourceKey<Biome>> sampleChunkCenterBiome(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int sampleY = sampleSurfaceY(chunk);
        return chunk.getNoiseBiome(
                        QuartPos.fromBlock(chunkPos.getMiddleBlockX()),
                        QuartPos.fromBlock(sampleY),
                        QuartPos.fromBlock(chunkPos.getMiddleBlockZ()))
                .unwrapKey();
    }

    public static ChunkClimateSample sampleChunkClimate(ChunkAccess chunk, ResourceKey<Biome> biomeKey) {
        ChunkPos chunkPos = chunk.getPos();
        int sampleY = sampleSurfaceY(chunk);
        BlockPos samplePos = chunkPos.getMiddleBlockPosition(sampleY);
        Biome biome = chunk.getNoiseBiome(
                        QuartPos.fromBlock(samplePos.getX()),
                        QuartPos.fromBlock(samplePos.getY()),
                        QuartPos.fromBlock(samplePos.getZ()))
                .value();
        return new ChunkClimateSample(biomeKey, biome.getBaseTemperature(), biome.getModifiedClimateSettings().downfall());
    }

    public static int sampleSurfaceY(ChunkAccess chunk) {
        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        return Math.max(minY, Math.min(surface, maxY));
    }

    public static Optional<BlockPos> findSpawnPos(
            ServerLevel level,
            ChunkAccess chunk,
            SuccessionChunkData chunkData,
            PlantDefinition plant,
            long gameTime) {
        PlantSpawnRules spawnRules = plant.spawnRules();
        Block block = BuiltInRegistries.BLOCK.getOptional(plant.plantId()).orElse(null);
        if (block == null) {
            return Optional.empty();
        }

        long seed = chunk.getPos().toLong()
                ^ (gameTime * 0x9E3779B97F4A7C15L)
                ^ ((long) plant.plantId().hashCode() << 32)
                ^ ((long) chunkData.getCurrentPlantCount() * 0xBF58476D1CE4E5B9L);
        Random random = new Random(seed);
        for (int attempt = 0; attempt < 64; attempt++) {
            int localX = random.nextInt(16);
            int localZ = random.nextInt(16);
            int worldX = chunk.getPos().getBlockX(localX);
            int worldZ = chunk.getPos().getBlockZ(localZ);
            Optional<BlockPos> placePos = findSpawnPosAtColumn(level, chunkData, block, spawnRules, worldX, worldZ);
            if (placePos.isPresent()) {
                return placePos;
            }
        }

        return Optional.empty();
    }

    public static boolean canPlantAt(
            ServerLevel level,
            SuccessionChunkData chunkData,
            Block block,
            PlantSpawnRules spawnRules,
            BlockPos placePos) {
        if (!level.isEmptyBlock(placePos)) {
            return false;
        }
        if (spawnRules.requireSky() && !level.canSeeSky(placePos)) {
            return false;
        }
        if (!isAllowedBaseBlock(level.getBlockState(placePos.below()), spawnRules)) {
            return false;
        }
        if (countNearbyTrackedPlants(chunkData, placePos, 4) >= spawnRules.maxLocalDensity()) {
            return false;
        }

        return block.defaultBlockState().canSurvive(level, placePos);
    }

    public static boolean isAllowedBaseBlock(BlockState baseState, PlantSpawnRules spawnRules) {
        ResourceLocation baseBlockId = BuiltInRegistries.BLOCK.getKey(baseState.getBlock());
        return spawnRules.allowedBaseBlocks().isEmpty() || spawnRules.allowedBaseBlocks().contains(baseBlockId);
    }

    public static int countNearbyTrackedPlants(SuccessionChunkData chunkData, BlockPos center, int radius) {
        int radiusSquared = radius * radius;
        int count = 0;
        for (ActivePlantRecord record : chunkData.getActivePlants().values()) {
            if (record.position().distSqr(center) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    public static @Nullable ResourceKey<Biome> toBiomeKey(@Nullable ResourceLocation biomeId) {
        return biomeId == null ? null : ResourceKey.create(Registries.BIOME, biomeId);
    }

    private static Optional<BlockPos> findSpawnPosAtColumn(
            ServerLevel level,
            SuccessionChunkData chunkData,
            Block block,
            PlantSpawnRules spawnRules,
            int worldX,
            int worldZ) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
        for (int y = surfaceY - 1; y <= surfaceY + 2; y++) {
            BlockPos placePos = new BlockPos(worldX, y, worldZ);
            if (canPlantAt(level, chunkData, block, spawnRules, placePos)) {
                return Optional.of(placePos);
            }
        }

        return Optional.empty();
    }

    public record ChunkClimateSample(ResourceKey<Biome> biomeKey, double temperature, double downfall) {
    }
}
