package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.TreeShapeUtils;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class DarkOakGrowthProfile implements TreeGrowthProfile {
    public static final DarkOakGrowthProfile INSTANCE = new DarkOakGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("dark_oak");

    private DarkOakGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int ticksPerStage() { return 3600; }
    @Override public Block logBlock() { return Blocks.DARK_OAK_LOG; }
    @Override public Block leavesBlock() { return Blocks.DARK_OAK_LEAVES; }
    @Override public boolean is2x2() { return true; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyParams.darkOak();
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
