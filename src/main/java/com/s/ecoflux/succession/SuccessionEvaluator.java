package com.s.ecoflux.succession;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.SuccessionPathDefinition;
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

        int contributingPoints = chunkData.getContributingVegetationPoints();
        int consumingValue = chunkData.getConsumingValue();
        long contributingCount = chunkData.countContributingVegetation();

        if (!chunkData.hasContributingVegetation()) {
            chunkData.setLastEvaluationGameTime(gameTime);
            EcofluxConstants.LOGGER.info(
                    "演替评估：植被积分={}，消耗阈值={}，进度={}，状态=等待植被成长",
                    contributingPoints,
                    consumingValue,
                    String.format("%.2f", chunkData.getProgress()));
            return String.format(
                    "已评估：无成长阶段植被（已追踪=%d），进度保持 %.2f。",
                    chunkData.getVegetationRecords().size(),
                    chunkData.getProgress());
        }

        double delta = contributingPoints >= consumingValue
                ? path.chunkRules().positiveProgressStep()
                : -path.chunkRules().negativeProgressStep();
        double nextProgress = Mth.clamp(chunkData.getProgress() + delta, -1.0D, 1.0D);
        chunkData.setLastEvaluationGameTime(gameTime);
        chunkData.setProgress(nextProgress);

        EcofluxConstants.LOGGER.info(
                "演替评估：贡献积分={}，消耗阈值={}，贡献植被数={}，方向={}，进度={}",
                contributingPoints,
                consumingValue,
                contributingCount,
                delta >= 0 ? "正向" : "回退",
                String.format("%.2f", nextProgress));

        return String.format(
                "已评估：贡献积分=%d，消耗阈值=%d，贡献植被=%d，方向=%s，进度=%.2f。",
                contributingPoints,
                consumingValue,
                contributingCount,
                delta >= 0 ? "正向" : "回退",
                nextProgress);
    }

    public static boolean shouldRegress(SuccessionChunkData chunkData) {
        return chunkData.getProgress() <= -1.0D;
    }
}
