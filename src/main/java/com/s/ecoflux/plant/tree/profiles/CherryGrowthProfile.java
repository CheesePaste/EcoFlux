package com.s.ecoflux.plant.tree.profiles;

/**
 * Cherry tree growth profile (singleton).
 *
 * <p>Structure: wide ellipsoid canopy, 8-14 block height,
 * 3600 ticks/stage, 4-8 spreading branches, pink-tinted wood and leaves
 * (cherry log and cherry leaves). Ornamental tree profile.
 * Delegates to the morphology system via {@link MorphologyPresets#cherry()}.
 * <p>Role in Ecoflux: defines the parametric growth parameters for cherry trees
 * in the ecological succession tree lifecycle system.
 */

import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class CherryGrowthProfile extends AbstractTreeGrowthProfile {
    public static final CherryGrowthProfile INSTANCE = new CherryGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("cherry");

    private CherryGrowthProfile() {}

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int ticksPerStage() { return 3600; }
    @Override public Block logBlock() { return Blocks.CHERRY_LOG; }
    @Override public Block leavesBlock() { return Blocks.CHERRY_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyPresets.cherry();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        return singleTrunkCanGrowStage(level, saplingPos, currentStage, totalStages, resolvedHeight);
    }
}
