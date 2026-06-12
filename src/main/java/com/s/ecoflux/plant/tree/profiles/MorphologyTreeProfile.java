package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.TreeShapeUtils;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record MorphologyTreeProfile(
        ResourceLocation treeType,
        int ticksPerStage,
        Block logBlock,
        Block leavesBlock,
        boolean is2x2,
        MorphologyParams morphologyParams,
        CanGrowStageStrategy canGrowStageStrategy,
        GrowStageHook growStageHook)
        implements TreeGrowthProfile {

    @FunctionalInterface
    public interface CanGrowStageStrategy {
        boolean check(ServerLevel level, BlockPos saplingPos, int currentStage,
                      int totalStages, int resolvedHeight);
    }

    @FunctionalInterface
    public interface GrowStageHook {
        void postGrow(ServerLevel level, BlockPos saplingPos, int currentStage,
                      int totalStages, int resolvedHeight, RandomSource random, Block logBlock);
    }

    // ── Strategy implementations ──────────────────────────────────────────

    public static final CanGrowStageStrategy SINGLE_TRUNK = (level, saplingPos, currentStage, totalStages, resolvedHeight) -> {
        BlockPos checkPos = saplingPos.above(currentStage + 2);
        if (checkPos.getY() >= level.getMaxBuildHeight()) return false;
        BlockState state = level.getBlockState(checkPos);
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    };

    public static final CanGrowStageStrategy TRUNK_2X2 = (level, saplingPos, currentStage, totalStages, resolvedHeight) -> {
        BlockPos checkPos = saplingPos.above(currentStage + 2);
        if (checkPos.getY() >= level.getMaxBuildHeight()) return false;
        for (BlockPos tp : TreeShapeUtils.trunk2x2Positions(saplingPos, checkPos.getY())) {
            BlockState s = level.getBlockState(tp);
            if (!s.isAir() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) return false;
        }
        return true;
    };

    public static final CanGrowStageStrategy ACACIA_3X3 = (level, saplingPos, currentStage, totalStages, resolvedHeight) -> {
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
    };

    // ── TreeGrowthProfile implementation ──────────────────────────────────

    @Override
    public MorphologyParams morphologyParams() {
        return morphologyParams;
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        return canGrowStageStrategy.check(level, saplingPos, currentStage, totalStages, resolvedHeight);
    }

    @Override
    public void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        if (growStageHook != null) {
            growStageHook.postGrow(level, saplingPos, currentStage, totalStages, resolvedHeight, random, logBlock);
        }
    }

    // ── Mangrove prop roots ───────────────────────────────────────────────

    public static void placePropRoots(ServerLevel level, BlockPos basePos, int currentStage,
                                      int totalStages, int resolvedHeight, RandomSource random, Block logBlock) {
        if (currentStage >= 2 || resolvedHeight < 6) return;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (random.nextFloat() > 0.6f) continue;
            BlockPos rootPos = basePos.relative(dir);
            if (rootPos.getY() <= level.getMinBuildHeight() + 1) continue;
            BlockState existing = level.getBlockState(rootPos);
            if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)) {
                TreeShapeUtils.tryPlaceLog(level, rootPos, logBlock, dir.getAxis());
            }
            BlockPos downPos = rootPos.below();
            if (random.nextFloat() > 0.7f) continue;
            BlockState downExisting = level.getBlockState(downPos);
            if (downExisting.isAir() || downExisting.is(BlockTags.REPLACEABLE) || downExisting.is(BlockTags.LEAVES)) {
                TreeShapeUtils.tryPlaceLog(level, downPos, logBlock, Direction.Axis.Y);
            }
        }
    }
}
