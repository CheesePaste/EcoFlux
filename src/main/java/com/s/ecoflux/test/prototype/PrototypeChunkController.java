package com.s.ecoflux.test.prototype;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.biome.BiomeRules;
import com.s.ecoflux.config.biome.BiomeRulesRegistry;
import com.s.ecoflux.config.succession.SuccessionConfigRegistry;
import com.s.ecoflux.config.succession.SuccessionPathDefinition;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.init.ModChunkEvents;
import com.s.ecoflux.plant.PlantSpawner;
import com.s.ecoflux.plant.VegetationTracker;
import com.s.ecoflux.succession.SuccessionService;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PrototypeChunkController {
    public static final ResourceLocation PROTOTYPE_PATH_ID = EcofluxConstants.id("plains_to_forest");

    private PrototypeChunkController() {
    }

    public static String accelerate(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);

        if (chunkData.getCurrentBiome().isEmpty()) {
            SuccessionService.initializeChunk(chunk);
        }

        Optional<SuccessionPathDefinition> pathOptional = SuccessionConfigRegistry.getPath(
                chunkData.getActivePathId().orElse(null));
        if (pathOptional.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有可用的演替路径。";
        }

        Optional<BiomeRules> rules = chunkData.getActiveBiomeRulesId()
                .flatMap(BiomeRulesRegistry::getRules);
        if (rules.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有群系规则。";
        }

        long gameTime = level.getGameTime();

        PlantSpawner.pruneInvalidPlants(level, chunkData, gameTime);
        PlantSpawner.ensureQueue(chunkData, rules.get());
        String spawnResult = PlantSpawner.trySpawnPlant(level, chunk, chunkData, gameTime);
        String observeResult = VegetationTracker.INSTANCE.observeChunk(level, chunk);

        ModChunkEvents.enableAutoForChunk(level, chunk);

        EcofluxConstants.LOGGER.info(
                "[Ecoflux] Per-chunk auto enabled for chunk={}, path={}, biome={}",
                chunk.getPos(),
                chunkData.getActivePathId().map(ResourceLocation::toString).orElse("无"),
                chunkData.getCurrentBiome().map(key -> key.location().toString()).orElse("未设置"));

        return String.join(" ",
                "已启动区块 " + chunk.getPos() + " 的自动演替。",
                spawnResult,
                observeResult,
                SuccessionService.describeChunk(chunk));
    }
}
