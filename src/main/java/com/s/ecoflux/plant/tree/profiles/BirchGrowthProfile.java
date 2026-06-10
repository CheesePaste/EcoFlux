package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BirchGrowthProfile implements TreeGrowthProfile {
    public static final BirchGrowthProfile INSTANCE = new BirchGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("birch");

    private BirchGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int ticksPerStage() { return 2400; }
    @Override public Block logBlock() { return Blocks.BIRCH_LOG; }
    @Override public Block leavesBlock() { return Blocks.BIRCH_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyParams.birch();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        BlockPos checkPos = saplingPos.above(currentStage + 2);
        if (checkPos.getY() >= level.getMaxBuildHeight()) return false;
        BlockState state = level.getBlockState(checkPos);
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }


}
