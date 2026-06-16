package com.cp.ecoflux.succession;

import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.config.biome.BiomeRules;
import com.cp.ecoflux.config.biome.BiomeRulesRegistry;
import com.cp.ecoflux.config.EcofluxServerConfig;
import com.cp.ecoflux.config.succession.SuccessionConfigRegistry;
import com.cp.ecoflux.config.succession.SuccessionPathDefinition;
import com.cp.ecoflux.config.SuccessionSpeedConfig;
import com.cp.ecoflux.init.ModAttachments;
import com.cp.ecoflux.plant.PlantSpawner;
import com.cp.ecoflux.plant.VegetationTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SuccessionService {
    private SuccessionService() {
    }

    public static void initializeChunk(ChunkAccess chunk) {
        chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA).clearRuntimeState();
        SuccessionTargetResolver.resolveTarget(chunk);
    }

    public static boolean hasActivePath(SuccessionChunkData chunkData) {
        return chunkData.getActivePathId().isPresent();
    }

    public static String describeChunk(LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        return String.format(
                "区块=%s 当前群系=%s 演替路径=%s 目标群系=%s 进度=%.2f 贡献积分=%d/%d 贡献植被=%d/%d %s",
                chunk.getPos(),
                chunkData.getCurrentBiome().map(key -> key.location().toString()).orElse("未设置"),
                chunkData.getActivePathId().map(ResourceLocation::toString).orElse("无"),
                chunkData.getTargetBiome().map(key -> key.location().toString()).orElse("无"),
                chunkData.getProgress(),
                chunkData.getContributingVegetationPoints(),
                chunkData.getConsumingValue(),
                chunkData.countContributingVegetation(),
                chunkData.getVegetationRecords().size(),
                PlantSpawner.getQueueSummary(chunkData));
    }

    public static String step(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = SuccessionConfigRegistry.getPath(
                chunkData.getActivePathId().orElse(null));
        if (pathOptional.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有激活的演替路径。";
        }
        Optional<BiomeRules> rules = getBiomeRules(chunkData);
        if (rules.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有群系规则。";
        }
        return doPipeline(level, chunk, chunkData, pathOptional.get(), rules.get(), true, true, true);
    }

    public static String pruneChunk(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        int removed = PlantSpawner.pruneInvalidPlants(level, chunkData, level.getGameTime());
        return "已清理区块 " + chunk.getPos() + " 的植物：移除 " + removed + " 个。";
    }

    public static String spawnInChunk(ServerLevel level, LevelChunk chunk) {
        if (EcofluxServerConfig.disablePlantSpawning()) {
            return "区块 " + chunk.getPos() + "：植物生成已禁用。";
        }
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = SuccessionConfigRegistry.getPath(
                chunkData.getActivePathId().orElse(null));
        if (pathOptional.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有激活的演替路径。";
        }
        Optional<BiomeRules> rules = getBiomeRules(chunkData);
        if (rules.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有群系规则。";
        }
        PlantSpawner.pruneInvalidPlants(level, chunkData, level.getGameTime());
        PlantSpawner.ensureQueue(chunkData, rules.get());
        return PlantSpawner.trySpawnPlant(level, chunk, chunkData, level.getGameTime());
    }

    public static String evaluateChunk(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = SuccessionConfigRegistry.getPath(
                chunkData.getActivePathId().orElse(null));
        if (pathOptional.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有激活的演替路径。";
        }
        SuccessionPathDefinition path = pathOptional.get();
        String result = SuccessionEvaluator.evaluate(chunkData, path, level.getGameTime(), true);
        if (chunkData.getProgress() >= 1.0D) {
            result = result + " " + BiomeTransitionService.applyTransition(level, chunk, chunkData);
        } else if (SuccessionEvaluator.shouldRegress(chunkData)) {
            result = result + " " + BiomeTransitionService.applyRegression(level, chunk, chunkData, path);
        }
        return "已评估区块 " + chunk.getPos() + "：" + result;
    }

    public static String forceTransition(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        return BiomeTransitionService.applyTransition(level, chunk, chunkData);
    }

    public static String processChunkTick(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = SuccessionConfigRegistry.getPath(
                chunkData.getActivePathId().orElse(null));
        if (pathOptional.isEmpty()) {
            return "自动演替跳过区块 " + chunk.getPos() + "：没有激活的演替路径。";
        }

        Optional<BiomeRules> rules = getBiomeRules(chunkData);
        if (rules.isEmpty()) {
            return "自动演替跳过区块 " + chunk.getPos() + "：没有群系规则。";
        }

        SuccessionPathDefinition path = pathOptional.get();
        long gameTime = level.getGameTime();
        float speed = SuccessionSpeedConfig.getSpeedMultiplier();
        long chunkHash = Math.abs(chunk.getPos().x * 31L + chunk.getPos().z);

        long effectiveInterval = Math.max(5L, (long) (EcofluxServerConfig.pruneIntervalTicks() / speed));
        boolean processElapsed = (gameTime + chunkHash) % effectiveInterval == 0L;

        boolean spawnElapsed;
        if (chunkData.getNextSpawnGameTime() <= 0L) {
            chunkData.setNextSpawnGameTime(gameTime + randomSpawnIntervalTicks(chunk.getPos().toLong() ^ gameTime));
            spawnElapsed = false;
        } else if (gameTime >= chunkData.getNextSpawnGameTime()) {
            spawnElapsed = true;
        } else {
            spawnElapsed = false;
        }

        if (!processElapsed && !spawnElapsed) {
            return "自动演替跳过区块 " + chunk.getPos() + "：等待处理间隔。";
        }

        return doPipeline(level, chunk, chunkData, path, rules.get(), processElapsed, spawnElapsed, false);
    }

    private static String doPipeline(ServerLevel level, LevelChunk chunk,
                                      SuccessionChunkData chunkData,
                                      SuccessionPathDefinition path,
                                      BiomeRules rules,
                                      boolean shouldObserve,
                                      boolean shouldSpawn,
                                      boolean ignoreInterval) {
        long gameTime = level.getGameTime();
        List<String> messages = new ArrayList<>();

        int pruned = PlantSpawner.pruneInvalidPlants(level, chunkData, gameTime);
        messages.add("已清理 " + pruned + " 个植物。");

        if (shouldSpawn && !EcofluxServerConfig.disablePlantSpawning()) {
            chunkData.setNextSpawnGameTime(gameTime + randomSpawnIntervalTicks(chunk.getPos().toLong() ^ gameTime));
            if (chunkData.getCurrentPlantCount() < chunkData.getMaxPlantCount()) {
                PlantSpawner.ensureQueue(chunkData, rules);
                messages.add(PlantSpawner.trySpawnPlant(level, chunk, chunkData, gameTime));
            }
        }

        if (shouldObserve) {
            messages.add(VegetationTracker.INSTANCE.observeChunk(level, chunk));

            String evalResult = SuccessionEvaluator.evaluate(chunkData, path, gameTime, ignoreInterval);
            messages.add(evalResult);

            // Push panel delta to players with UI open
            com.cp.ecoflux.network.ModNetworking.pushPanelDeltaToTracking(level, chunk);

            if (chunkData.getProgress() >= 1.0D) {
                messages.add(BiomeTransitionService.applyTransition(level, chunk, chunkData));
            } else if (SuccessionEvaluator.shouldRegress(chunkData)) {
                messages.add(BiomeTransitionService.applyRegression(level, chunk, chunkData, path));
            }
        }

        return String.join(" ", messages);
    }

    private static Optional<BiomeRules> getBiomeRules(SuccessionChunkData chunkData) {
        return chunkData.getActiveBiomeRulesId()
                .flatMap(BiomeRulesRegistry::getRules);
    }

    private static long randomSpawnIntervalTicks(long seed) {
        int min = EcofluxServerConfig.spawnIntervalMinTicks();
        int max = Math.max(min, EcofluxServerConfig.spawnIntervalMaxTicks());
        float speed = SuccessionSpeedConfig.getSpeedMultiplier();
        long raw = (min == max) ? min : min + new Random(seed).nextLong(max - min + 1L);
        return Math.max(5L, (long) (raw / speed));
    }
}
