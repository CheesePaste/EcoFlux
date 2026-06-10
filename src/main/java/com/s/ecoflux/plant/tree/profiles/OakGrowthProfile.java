package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.GrowthPlacement;
import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.TreeShapeUtils;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OakGrowthProfile implements TreeGrowthProfile {
    public static final OakGrowthProfile INSTANCE = new OakGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("oak");

    private OakGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int minTrunkHeight() { return 5; }
    @Override public int maxTrunkHeight() { return 8; }
    @Override public int ticksPerStage() { return 3600; }
    @Override public Block logBlock() { return Blocks.OAK_LOG; }
    @Override public Block leavesBlock() { return Blocks.OAK_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyParams.oak();
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
    public List<GrowthPlacement> growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        // Fallback path when skeleton/plan is null; no per-block animation tracking
        if (currentStage < resolvedHeight) {
            placeTrunkStage(level, saplingPos, currentStage, resolvedHeight, random);
        } else {
            placeCanopyStage(level, saplingPos, currentStage, totalStages, resolvedHeight, random);
        }
        return List.of();
    }

    private void placeTrunkStage(ServerLevel level, BlockPos saplingPos, int stage,
                                 int resolvedHeight, RandomSource random) {
        BlockPos trunkPos = saplingPos.above(stage + 1);
        TreeShapeUtils.tryPlaceLog(level, trunkPos, logBlock());

        double progress = (double) stage / resolvedHeight;
        double radius = 0.8 + progress * 1.2;

        if (radius >= 1.0) {
            TreeShapeUtils.placeLeafDisc(level, trunkPos, radius, leavesBlock(),
                    trunkPos, 0.20, random);
        }

        if (stage >= resolvedHeight / 2 && random.nextDouble() < 0.25) {
            placeBranch(level, saplingPos, trunkPos, random);
        }
    }

    private void placeBranch(ServerLevel level, BlockPos saplingPos, BlockPos trunkPos, RandomSource random) {
        int dx = random.nextInt(3) - 1;
        int dz = random.nextInt(3) - 1;
        if (dx == 0 && dz == 0) dx = 1;
        BlockPos branchPos = trunkPos.offset(dx, random.nextInt(2) - 1, dz);
        if (TreeShapeUtils.tryPlaceLog(level, branchPos, logBlock())) {
            TreeShapeUtils.placeLeafDisc(level, branchPos, 1.5, leavesBlock(),
                    trunkPos, 0.15, random);
        }
    }

    private void placeCanopyStage(ServerLevel level, BlockPos saplingPos, int stage,
                                  int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStage = stage - resolvedHeight;
        int canopyDepth = totalStages - resolvedHeight;
        BlockPos trunkTop = saplingPos.above(resolvedHeight);
        int y = canopyStage;
        BlockPos layerCenter = trunkTop.above(y);
        double radius = TreeShapeUtils.oakCanopyRadius(y, canopyDepth);

        if (radius > 0) {
            TreeShapeUtils.placeLeafDisc(level, layerCenter, radius, leavesBlock(),
                    trunkTop, 0.18, random);
        }

        if (canopyStage == canopyDepth - 1) {
            TreeShapeUtils.placeLeafDisc(level, layerCenter.above(), 1.0, leavesBlock(),
                    trunkTop, 0.10, random);
        }
    }
}
