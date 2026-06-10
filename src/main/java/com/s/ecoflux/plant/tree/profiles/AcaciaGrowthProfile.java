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

public final class AcaciaGrowthProfile implements TreeGrowthProfile {
    public static final AcaciaGrowthProfile INSTANCE = new AcaciaGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("acacia");

    private AcaciaGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int minTrunkHeight() { return 5; }
    @Override public int maxTrunkHeight() { return 10; }
    @Override public int ticksPerStage() { return 3600; }
    @Override public Block logBlock() { return Blocks.ACACIA_LOG; }
    @Override public Block leavesBlock() { return Blocks.ACACIA_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyParams.acacia();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        int checkY = saplingPos.getY() + currentStage + 2;
        if (checkY >= level.getMaxBuildHeight()) return false;
        BlockPos checkCenter = saplingPos.atY(checkY);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos cp = checkCenter.offset(dx, 0, dz);
                BlockState s = level.getBlockState(cp);
                if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) return true;
            }
        }
        return false;
    }

    @Override
    public void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStart = resolvedHeight;
        if (currentStage < canopyStart) {
            placeTrunkStage(level, saplingPos, currentStage, resolvedHeight, random);
        } else {
            placeCanopyStage(level, saplingPos, currentStage, totalStages, resolvedHeight, random);
        }
    }

    private void placeTrunkStage(ServerLevel level, BlockPos saplingPos, int stage,
                                 int resolvedHeight, RandomSource random) {
        BlockPos currentTop = saplingPos.above(stage);
        int targetY = saplingPos.getY() + stage + 1;

        int dx = 0, dz = 0;
        if (random.nextDouble() < 0.25) {
            dx = random.nextInt(3) - 1;
            dz = random.nextInt(3) - 1;
        }

        BlockPos trunkPos = new BlockPos(currentTop.getX() + dx, targetY, currentTop.getZ() + dz);
        TreeShapeUtils.tryPlaceLog(level, trunkPos, logBlock());

        if (stage >= resolvedHeight / 3 && random.nextDouble() < 0.35) {
            int bdx = random.nextInt(3) - 1;
            int bdz = random.nextInt(3) - 1;
            if (bdx == 0 && bdz == 0) bdx = 1;
            int length = 1 + random.nextInt(2);
            BlockPos lastBranch = trunkPos;
            for (int l = 1; l <= length; l++) {
                BlockPos bp = trunkPos.offset(bdx * l, random.nextInt(2), bdz * l);
                if (TreeShapeUtils.tryPlaceLog(level, bp, logBlock())) {
                    lastBranch = bp;
                }
            }
            TreeShapeUtils.placeLeafDisc(level, lastBranch, 1.8, leavesBlock(),
                    trunkPos, 0.30, random);
        }
    }

    private void placeCanopyStage(ServerLevel level, BlockPos saplingPos, int stage,
                                  int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStage = stage - resolvedHeight;
        int canopyDepth = totalStages - resolvedHeight;

        BlockPos topTrunk = findTopTrunk(level, saplingPos, resolvedHeight);
        int y = canopyStage;
        BlockPos layerCenter = topTrunk.above(y);

        double radius = 2.0 + canopyStage * 0.8;
        if (canopyStage == canopyDepth - 1) radius = 0.8;

        TreeShapeUtils.placeLeafDisc(level, layerCenter, radius, leavesBlock(),
                topTrunk, 0.35, random);

        if (canopyStage == 1 && random.nextDouble() < 0.5) {
            int sideDx = random.nextInt(3) - 1;
            int sideDz = random.nextInt(3) - 1;
            if (sideDx == 0 && sideDz == 0) sideDx = 2;
            BlockPos sideCluster = layerCenter.offset(sideDx * 2, 0, sideDz * 2);
            TreeShapeUtils.placeLeafDisc(level, sideCluster, 1.5, leavesBlock(),
                    topTrunk, 0.30, random);
        }
    }

    private BlockPos findTopTrunk(ServerLevel level, BlockPos saplingPos, int resolvedHeight) {
        for (int y = resolvedHeight; y >= 1; y--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos check = saplingPos.offset(dx, y, dz);
                    if (level.getBlockState(check).is(BlockTags.LOGS)) {
                        return check;
                    }
                }
            }
        }
        return saplingPos.above(resolvedHeight);
    }
}
