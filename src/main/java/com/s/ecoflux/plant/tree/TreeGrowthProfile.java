package com.s.ecoflux.plant.tree;

import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

public interface TreeGrowthProfile {
    ResourceLocation treeType();

    default int minTrunkHeight() {
        MorphologyParams mp = morphologyParams();
        return mp != null ? mp.minTrunkHeight() : 5;
    }

    default int maxTrunkHeight() {
        MorphologyParams mp = morphologyParams();
        return mp != null ? mp.maxTrunkHeight() : 8;
    }

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
        MorphologyParams mp = morphologyParams();
        if (mp != null) return mp.resolveHeight(random);
        int range = maxTrunkHeight() - minTrunkHeight();
        return minTrunkHeight() + (range > 0 ? random.nextInt(range + 1) : 0);
    }

    boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                         int totalStages, int resolvedHeight);

    default void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                  int totalStages, int resolvedHeight, RandomSource random) {
    }
}
