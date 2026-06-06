package com.s.ecoflux.succession;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.SuccessionPathDefinition;
import com.s.ecoflux.plant.VegetationLifecycleStage;
import net.minecraft.util.Mth;

public final class SuccessionEvaluator {
    public static final int DEFAULT_DAY_TICKS = 24000;

    private SuccessionEvaluator() {
    }

    public static String evaluate(
            SuccessionChunkData chunkData,
            SuccessionPathDefinition path,
            long gameTime,
            boolean ignoreInterval) {
        int evaluationInterval = path.chunkRules().resolvedEvaluationIntervalTicks(DEFAULT_DAY_TICKS);
        if (!ignoreInterval && gameTime - chunkData.getLastEvaluationGameTime() < evaluationInterval) {
            return "跳过评估：等待评估间隔。";
        }

        int totalVegetationPoints = chunkData.getTotalVegetationPoints();
        boolean hasAgingVegetation = hasAgingVegetation(chunkData);
        if (!hasAgingVegetation) {
            chunkData.setLastEvaluationGameTime(gameTime);
            EcofluxConstants.LOGGER.info(
                    "演替评估：植被积分={}，消耗阈值={}，进度={}，状态=等待植被衰老",
                    totalVegetationPoints,
                    chunkData.getConsumingValue(),
                    String.format("%.2f", chunkData.getProgress()));
            return String.format(
                    "已评估：植被尚未衰老，进度保持 %.2f。",
                    chunkData.getProgress());
        }

        double delta = totalVegetationPoints >= chunkData.getConsumingValue()
                ? path.chunkRules().positiveProgressStep()
                : -path.chunkRules().negativeProgressStep();
        double nextProgress = Mth.clamp(chunkData.getProgress() + delta, -1.0D, 1.0D);
        chunkData.setLastEvaluationGameTime(gameTime);
        chunkData.setProgress(nextProgress);

        EcofluxConstants.LOGGER.info(
                "演替评估：植被积分={}，消耗阈值={}，进度={}",
                totalVegetationPoints,
                chunkData.getConsumingValue(),
                String.format("%.2f", nextProgress));

        return String.format(
                "已评估：植被积分=%d，消耗阈值=%d，进度=%.2f。",
                totalVegetationPoints,
                chunkData.getConsumingValue(),
                nextProgress);
    }

    public static boolean hasAgingVegetation(SuccessionChunkData chunkData) {
        return chunkData.getVegetationRecords().values().stream()
                .anyMatch(record -> record.lifeStage() == VegetationLifecycleStage.AGING);
    }
}
