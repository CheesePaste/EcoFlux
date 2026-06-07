package com.s.ecoflux.plant.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public interface TreeGrowthProfile {
    ResourceLocation treeType();

    int totalStages();

    int ticksPerStage();

    boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage);

    void growStage(ServerLevel level, BlockPos saplingPos, int currentStage);
}
