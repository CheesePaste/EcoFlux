package com.s.ecoflux.plant.tree.profiles;

/**
 * Jungle 2x2 tree growth profile (singleton).
 *
 * <p>Structure: 2x2 trunk (requires 4 saplings), 15-22 block height,
 * 4800 ticks/stage (~64 real minutes total), large ellipsoid canopy
 * with 5 sub-clusters, 7-12 long branches with secondary branching,
 * 4-block clear trunk. Largest canopy in the system.
 * Delegates to the morphology system via {@link MorphologyPresets#jungle()}.
 * Overrides {@link #canGrowStage} to check all 2x2 trunk positions.
 * <p>Role in Ecoflux: defines the parametric growth parameters for 2x2 jungle trees
 * in the ecological succession tree lifecycle system.
 */

import com.s.ecoflux.plant.tree.TreeShapeUtils;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class JungleGrowthProfile extends AbstractTreeGrowthProfile {
    public static final JungleGrowthProfile INSTANCE = new JungleGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("jungle");

    private JungleGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int ticksPerStage() { return 4800; }
    @Override public Block logBlock() { return Blocks.JUNGLE_LOG; }
    @Override public Block leavesBlock() { return Blocks.JUNGLE_LEAVES; }
    @Override public boolean is2x2() { return true; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyPresets.jungle();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        BlockPos checkPos = saplingPos.above(currentStage + 2);
        if (checkPos.getY() >= level.getMaxBuildHeight()) return false;
        for (BlockPos tp : TreeShapeUtils.trunk2x2Positions(saplingPos, checkPos.getY())) {
            BlockState s = level.getBlockState(tp);
            if (!s.isAir() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) return false;
        }
        return true;
    }
}
