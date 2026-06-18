package com.cp.ecoflux.api.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

public interface TreeGrowthProfile {
    ResourceLocation treeType();

    int ticksPerStage();

    Block logBlock();

    Block leavesBlock();

    boolean is2x2();

    int totalStagesForHeight(int resolvedHeight);

    int resolveHeight(RandomSource random);

    boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                         int totalStages, int resolvedHeight);

    default void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                  int totalStages, int resolvedHeight, RandomSource random) {
    }
}
