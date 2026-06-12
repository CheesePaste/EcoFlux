package com.s.ecoflux.succession;

/**
 * Chunk initialization: resolves the succession target for a newly loaded chunk.
 *
 * <p>Structure: static utility class with a single {@code resolveTarget()} method.
 * Samples the chunk's center biome and climate via {@code ChunkSamplingHelper},
 * queries {@code SuccessionConfigRegistry} for the best-matching succession path,
 * and populates all fields of {@code SuccessionChunkData} (path id, target/fallback
 * biomes, consuming value, plant count limit, weighted plant queue).
 *
 * <p>Role in Ecoflux: called by {@code SuccessionService.initializeChunk()} when a
 * chunk is first loaded. This is the entry point that determines what succession
 * path (if any) governs a given chunk.
 */

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.plant.PlantSpawner;
import com.s.ecoflux.world.ChunkSamplingHelper;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class SuccessionTargetResolver {
    private SuccessionTargetResolver() {
    }

    public static void resolveTarget(ChunkAccess chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<ResourceKey<Biome>> currentBiomeKey = ChunkSamplingHelper.sampleChunkCenterBiome(chunk);
        if (currentBiomeKey.isEmpty()) {
            EcofluxConstants.LOGGER.warn("无法解析区块 {} 中心点的群系", chunk.getPos());
            return;
        }

        ChunkSamplingHelper.ChunkClimateSample climateSample = ChunkSamplingHelper.sampleChunkClimate(
                chunk, currentBiomeKey.get());
        Optional<SuccessionPathDefinition> matchedPath = SuccessionConfigRegistry.findBestMatch(
                climateSample.biomeKey(),
                climateSample.temperature(),
                climateSample.downfall());

        chunkData.setCurrentBiome(climateSample.biomeKey());

        if (matchedPath.isPresent()) {
            SuccessionPathDefinition path = matchedPath.get();
            chunkData.setActivePathId(path.pathId());
            chunkData.setPreviousBiome(ChunkSamplingHelper.toBiomeKey(path.fallbackBiome()));
            chunkData.setTargetBiome(ChunkSamplingHelper.toBiomeKey(path.targetBiome()));
            chunkData.setConsumingValue(path.chunkRules().consuming());
            chunkData.setMaxPlantCount(path.chunkRules().maxPlantCount());
            chunkData.replacePlantQueue(PlantSpawner.buildWeightedQueue(
                    path, path.chunkRules().queueCapacity(), new java.util.Random(chunk.getPos().toLong())));
            return;
        }

        chunkData.setPreviousBiome(null);
    }
}
