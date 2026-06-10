package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.TreeShapeUtils;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BirchGrowthProfile implements TreeGrowthProfile {
    public static final BirchGrowthProfile INSTANCE = new BirchGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("birch");

    private BirchGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int minTrunkHeight() { return 6; }
    @Override public int maxTrunkHeight() { return 10; }
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

    @Override
    public void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStart = resolvedHeight;
        if (currentStage < canopyStart) {
            placeTrunkStage(level, saplingPos, currentStage, random);
        } else {
            placeCanopyStage(level, saplingPos, currentStage, totalStages, resolvedHeight, random);
        }
    }

    private void placeTrunkStage(ServerLevel level, BlockPos saplingPos, int stage, RandomSource random) {
        BlockPos trunkPos = saplingPos.above(stage + 1);
        TreeShapeUtils.tryPlaceLog(level, trunkPos, logBlock());
    }

    private void placeCanopyStage(ServerLevel level, BlockPos saplingPos, int stage,
                                  int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStage = stage - resolvedHeight;
        int canopyDepth = totalStages - resolvedHeight;
        BlockPos trunkTop = saplingPos.above(resolvedHeight);
        int y = canopyStage;
        BlockPos layerCenter = trunkTop.above(y);
        double radius = TreeShapeUtils.birchCanopyRadius(y, canopyDepth);

        if (radius > 0) {
            TreeShapeUtils.placeLeafDisc(level, layerCenter, radius, leavesBlock(),
                    trunkTop, 0.15, random);
        }

        if (canopyStage == canopyDepth - 1) {
            BlockPos tip = layerCenter.above();
            TreeShapeUtils.placeLeafDisc(level, tip, 0.6, leavesBlock(), trunkTop, 0.10, random);
            if (random.nextDouble() < 0.4) {
                TreeShapeUtils.placeLeaf(level, tip.above(), leavesBlock(), 1);
            }
        }

        if (canopyStage >= 1 && random.nextDouble() < 0.3) {
            int dropY = y - 1;
            if (dropY >= 0) {
                TreeShapeUtils.placeLeaf(level, layerCenter.below(), leavesBlock(),
                        TreeShapeUtils.computeLeafDistance(layerCenter.below(), trunkTop));
            }
        }
    }
}
