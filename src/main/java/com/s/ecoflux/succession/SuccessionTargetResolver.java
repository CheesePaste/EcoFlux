package com.s.ecoflux.succession;

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

        chunkData.clearRuntimeState();
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
            EcofluxConstants.LOGGER.debug(
                    "已初始化 Ecoflux 区块 {}：路径={}，植物队列={} 个",
                    chunk.getPos(),
                    path.pathId(),
                    chunkData.getPlantQueue().size());
            return;
        }

        chunkData.clearRuntimeState();
        chunkData.setPreviousBiome(null);
        EcofluxConstants.LOGGER.debug(
                "已初始化 Ecoflux 区块 {}：群系 {} 没有匹配的演替路径（温度={}，降水={}）",
                chunk.getPos(),
                climateSample.biomeKey().location(),
                String.format("%.3f", climateSample.temperature()),
                String.format("%.3f", climateSample.downfall()));
    }
}
