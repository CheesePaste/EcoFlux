package com.s.ecoflux.plant.tree.profiles;

/**
 * Birch tree growth profile (singleton).
 *
 * <p>Structure: tall slender ellipsoid canopy, 10-16 block trunk height,
 * 2400 ticks/stage (~20 real minutes), nearly vertical trunk,
 * 0-3 short branches near the top, 4-block clear trunk.
 * Fastest growing tree in the Ecoflux system.
 * Delegates to the morphology system via {@link MorphologyPresets#birch()}.
 * <p>Role in Ecoflux: defines the parametric growth parameters for birch trees
 * in the ecological succession tree lifecycle system.
 */

import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class BirchGrowthProfile extends AbstractTreeGrowthProfile {
    public static final BirchGrowthProfile INSTANCE = new BirchGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("birch");

    private BirchGrowthProfile() {}

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int ticksPerStage() { return 2400; }
    @Override public Block logBlock() { return Blocks.BIRCH_LOG; }
    @Override public Block leavesBlock() { return Blocks.BIRCH_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyPresets.birch();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        return singleTrunkCanGrowStage(level, saplingPos, currentStage, totalStages, resolvedHeight);
    }
}
