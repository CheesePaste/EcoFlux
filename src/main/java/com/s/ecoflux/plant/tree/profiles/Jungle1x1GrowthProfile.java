package com.s.ecoflux.plant.tree.profiles;

/**
 * Jungle 1x1 tree growth profile (singleton).
 *
 * <p>Structure: single-sapling variant of jungle, clustered ellipsoid canopy,
 * 12-18 block height, 4200 ticks/stage, 5-9 branches, 4 sub-clusters,
 * 3-block clear trunk. Smaller and faster than the 2x2 variant.
 * Delegates to the morphology system via {@link MorphologyPresets#jungle1x1()}.
 * <p>Role in Ecoflux: defines the parametric growth parameters for 1x1 jungle trees
 * in the ecological succession tree lifecycle system.
 */

import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class Jungle1x1GrowthProfile extends AbstractTreeGrowthProfile {
    public static final Jungle1x1GrowthProfile INSTANCE = new Jungle1x1GrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("jungle_1x1");

    private Jungle1x1GrowthProfile() {}

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int ticksPerStage() { return 4200; }
    @Override public Block logBlock() { return Blocks.JUNGLE_LOG; }
    @Override public Block leavesBlock() { return Blocks.JUNGLE_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyPresets.jungle1x1();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        return singleTrunkCanGrowStage(level, saplingPos, currentStage, totalStages, resolvedHeight);
    }
}
