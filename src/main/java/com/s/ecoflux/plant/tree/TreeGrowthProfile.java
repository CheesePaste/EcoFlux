package com.s.ecoflux.plant.tree;

import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

public interface TreeGrowthProfile {
    ResourceLocation treeType();

    int minTrunkHeight();

    int maxTrunkHeight();

    int ticksPerStage();

    Block logBlock();

    Block leavesBlock();

    boolean is2x2();

    default MorphologyParams morphologyParams() {
        return null;
    }

    default int totalStagesForHeight(int resolvedHeight) {
        MorphologyParams mp = morphologyParams();
        if (mp != null) return mp.totalStagesForHeight(resolvedHeight);
        return resolvedHeight + 2;
    }

    default int resolveHeight(RandomSource random) {
        int range = maxTrunkHeight() - minTrunkHeight();
        return minTrunkHeight() + (range > 0 ? random.nextInt(range + 1) : 0);
    }

    boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                         int totalStages, int resolvedHeight);

    void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                   int totalStages, int resolvedHeight, RandomSource random);
}
