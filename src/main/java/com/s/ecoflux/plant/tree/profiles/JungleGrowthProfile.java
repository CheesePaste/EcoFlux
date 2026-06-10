package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.GrowthPlacement;
import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.TreeShapeUtils;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import java.util.List;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class JungleGrowthProfile implements TreeGrowthProfile {
    public static final JungleGrowthProfile INSTANCE = new JungleGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("jungle");

    private JungleGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int minTrunkHeight() { return 10; }
    @Override public int maxTrunkHeight() { return 15; }
    @Override public int ticksPerStage() { return 4800; }
    @Override public Block logBlock() { return Blocks.JUNGLE_LOG; }
    @Override public Block leavesBlock() { return Blocks.JUNGLE_LEAVES; }
    @Override public boolean is2x2() { return true; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyParams.jungle();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        BlockPos checkPos = saplingPos.above(currentStage + 2);
        if (checkPos.getY() >= level.getMaxBuildHeight()) return false;
        if (is2x2()) {
            for (BlockPos tp : TreeShapeUtils.trunk2x2Positions(saplingPos, checkPos.getY())) {
                BlockState s = level.getBlockState(tp);
                if (!s.isAir() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) return false;
            }
            return true;
        }
        BlockState state = level.getBlockState(checkPos);
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }

    @Override
    public List<GrowthPlacement> growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStart = resolvedHeight;
        if (currentStage < canopyStart) {
            placeTrunkStage(level, saplingPos, currentStage, resolvedHeight, random);
        } else {
            placeCanopyStage(level, saplingPos, currentStage, totalStages, resolvedHeight, random);
        }
        return List.of();
    }

    private void placeTrunkStage(ServerLevel level, BlockPos saplingPos, int stage,
                                 int resolvedHeight, RandomSource random) {
        int y = saplingPos.getY() + stage + 1;
        for (BlockPos tp : TreeShapeUtils.trunk2x2Positions(saplingPos, y)) {
            TreeShapeUtils.tryPlaceLog(level, tp, logBlock());
        }

        double progress = (double) stage / resolvedHeight;
        if (progress > 0.5 && random.nextDouble() < 0.30) {
            List<BlockPos> branches = TreeShapeUtils.generateBranchPositions(
                    saplingPos, resolvedHeight, random, 2, 2);
            for (BlockPos bp : branches) {
                if (TreeShapeUtils.tryPlaceLog(level, bp, logBlock())) {
                    TreeShapeUtils.placeLeafDisc(level, bp, 1.8, leavesBlock(), bp, 0.20, random);
                }
            }
        }
    }

    private void placeCanopyStage(ServerLevel level, BlockPos saplingPos, int stage,
                                  int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStage = stage - resolvedHeight;
        int canopyDepth = totalStages - resolvedHeight;
        double ratio = (double) canopyStage / canopyDepth;
        BlockPos trunkTop = saplingPos.atY(saplingPos.getY() + resolvedHeight);
        int y = canopyStage;
        BlockPos layerCenter = trunkTop.above(y);

        double radius = 3.0 + ratio * 1.5;
        if (canopyStage == canopyDepth - 1) radius = 1.0;

        TreeShapeUtils.placeLeafDisc(level, layerCenter, radius, leavesBlock(),
                trunkTop, 0.22, random);
    }
}
