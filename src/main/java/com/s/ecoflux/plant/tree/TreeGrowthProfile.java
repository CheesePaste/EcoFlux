package com.s.ecoflux.plant.tree;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public interface TreeGrowthProfile {
    ResourceLocation treeType();

    int totalStages();

    int ticksPerStage();

    boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage);

    /**
     * Execute one growth stage, placing blocks directly in the world.
     * @return placements with animation type hints for client-side visual effects
     */
    List<GrowthPlacement> growStage(ServerLevel level, BlockPos saplingPos, int currentStage);
}
