package com.s.ecoflux.succession;

import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.plant.PlantSpawner;
import com.s.ecoflux.plant.VegetationTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SuccessionService {
    private SuccessionService() {
    }

    public static void initializeChunk(ChunkAccess chunk) {
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

        SuccessionPathDefinition path = pathOptional.get();
        long gameTime = level.getGameTime();
        List<String> messages = new ArrayList<>();

        int pruned = PlantSpawner.pruneInvalidPlants(level, chunkData, gameTime);
        messages.add("已清理 " + pruned + " 个植物。");

        PlantSpawner.ensureQueue(chunkData, path);
        messages.add(PlantSpawner.trySpawnPlant(level, chunk, chunkData, path, gameTime));

        messages.add(VegetationTracker.INSTANCE.observeChunk(level, chunk));

        String evalResult = SuccessionEvaluator.evaluate(chunkData, path, gameTime, true);
        messages.add(evalResult);

        if (chunkData.getProgress() >= 1.0D) {
            messages.add(BiomeTransitionService.applyTransition(level, chunk, chunkData));
        } else if (SuccessionEvaluator.shouldRegress(chunkData)) {
            messages.add(BiomeTransitionService.applyRegression(level, chunk, chunkData, path));
        }

        return String.join(" ", messages);
    }

    public static String pruneChunk(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        int removed = PlantSpawner.pruneInvalidPlants(level, chunkData, level.getGameTime());
        return "已清理区块 " + chunk.getPos() + " 的植物：移除 " + removed + " 个。";
    }

    public static String spawnInChunk(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = SuccessionConfigRegistry.getPath(
                chunkData.getActivePathId().orElse(null));
        if (pathOptional.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有激活的演替路径。";
        }

        PlantSpawner.pruneInvalidPlants(level, chunkData, level.getGameTime());
        PlantSpawner.ensureQueue(chunkData, pathOptional.get());
        return PlantSpawner.trySpawnPlant(level, chunk, chunkData, pathOptional.get(), level.getGameTime());
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

        SuccessionPathDefinition path = pathOptional.get();
        long gameTime = level.getGameTime();
        if (gameTime % path.chunkRules().processingIntervalTicks() != 0L) {
            return "自动演替跳过区块 " + chunk.getPos() + "：等待处理间隔。";
        }

        List<String> messages = new ArrayList<>();

        int pruned = PlantSpawner.pruneInvalidPlants(level, chunkData, gameTime);
        messages.add("已清理 " + pruned + " 个植物。");

        PlantSpawner.ensureQueue(chunkData, path);
        messages.add(PlantSpawner.trySpawnPlant(level, chunk, chunkData, path, gameTime));

        messages.add(VegetationTracker.INSTANCE.observeChunk(level, chunk));

        String evalResult = SuccessionEvaluator.evaluate(chunkData, path, gameTime, false);
        messages.add(evalResult);

        if (chunkData.getProgress() >= 1.0D) {
            messages.add(BiomeTransitionService.applyTransition(level, chunk, chunkData));
        } else if (SuccessionEvaluator.shouldRegress(chunkData)) {
            messages.add(BiomeTransitionService.applyRegression(level, chunk, chunkData, path));
        }

        return String.join(" ", messages);
    }
}
