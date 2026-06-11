package com.s.ecoflux.plant.tree;

/**
 * Interface defining species-specific tree growth parameters and behavior.
 *
 * <p>Structure: Provides tree type identifier, log/leaf block references, 2x2 flag,
 * ticks-per-stage, height range, stage count calculation, and a {@link #growStage}
 * default method for placing blocks per stage. The optional {@link #morphologyParams()}
 * method enables the parametric morphology pipeline.
 *
 * <p>Role in Ecoflux: Abstraction layer over tree species. Nine implementations
 * ({@code OakGrowthProfile}, {@code BirchGrowthProfile}, etc.) in the
 * {@code profiles/} package supply species-specific morphology parameters and
 * growth logic. Used by {@link TreeGrowthHandler} to drive staged growth.
 */

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
