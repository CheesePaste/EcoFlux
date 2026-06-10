package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.GrowthPlacement;
import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.TreeShapeUtils;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class DarkOakGrowthProfile implements TreeGrowthProfile {
    public static final DarkOakGrowthProfile INSTANCE = new DarkOakGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("dark_oak");

    private DarkOakGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int minTrunkHeight() { return 6; }
    @Override public int maxTrunkHeight() { return 10; }
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

    @Override
    public List<GrowthPlacement> growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStart = resolvedHeight;
        if (currentStage < canopyStart) {
            placeTrunkStage(level, saplingPos, currentStage, random);
        } else {
            placeCanopyStage(level, saplingPos, currentStage, totalStages, resolvedHeight, random);
        }
        return List.of();
    }

    private void placeTrunkStage(ServerLevel level, BlockPos saplingPos, int stage, RandomSource random) {
        int y = saplingPos.getY() + stage + 1;
        for (BlockPos tp : TreeShapeUtils.trunk2x2Positions(saplingPos, y)) {
            TreeShapeUtils.tryPlaceLog(level, tp, logBlock());
        }
    }

    private void placeCanopyStage(ServerLevel level, BlockPos saplingPos, int stage,
                                  int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStage = stage - resolvedHeight;
        int canopyDepth = totalStages - resolvedHeight;
        BlockPos trunkTop = saplingPos.atY(saplingPos.getY() + resolvedHeight);
        int y = canopyStage;
        BlockPos layerCenter = trunkTop.above(y);

        double radius;
        if (canopyStage == 0 || canopyStage == canopyDepth - 1) {
            radius = 1.5;
        } else {
            radius = 2.5 + 0.5 * (1.0 - Math.abs(canopyStage - canopyDepth / 2.0) / (canopyDepth / 2.0));
        }

        TreeShapeUtils.placeLeafDisc(level, layerCenter, radius, leavesBlock(),
                trunkTop, 0.10, random);

        if (canopyStage == canopyDepth - 1) {
            TreeShapeUtils.placeLeafDisc(level, layerCenter.above(), 0.8, leavesBlock(),
                    trunkTop, 0.05, random);
        }
    }
}
