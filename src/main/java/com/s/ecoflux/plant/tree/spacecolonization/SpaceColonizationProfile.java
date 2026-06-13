package com.s.ecoflux.plant.tree.spacecolonization;

import com.s.ecoflux.plant.tree.TreeGrowthHandler;
import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.TreeGrowthSession;
import com.s.ecoflux.plant.tree.TreeShapeUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public record SpaceColonizationProfile(
        ResourceLocation treeType,
        int ticksPerStage,
        Block logBlock,
        Block leavesBlock,
        boolean is2x2,
        SpaceColonizationParams scParams,
        @Nullable PostGrowHook postGrowHook
) implements TreeGrowthProfile {

    @FunctionalInterface
    public interface PostGrowHook {
        void run(ServerLevel level, BlockPos saplingPos, int currentStage,
                 int totalStages, int resolvedHeight, RandomSource random, Block logBlock);
    }

    @Override
    public boolean is2x2() {
        return is2x2;
    }

    @Override
    public int totalStagesForHeight(int resolvedHeight) {
        return scParams.totalStagesForHeight(resolvedHeight);
    }

    @Override
    public int resolveHeight(RandomSource random) {
        return scParams.resolveTrunkHeight(random);
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        int checkY = saplingPos.getY() + Math.min(currentStage + 2, resolvedHeight + 1);
        if (checkY >= level.getMaxBuildHeight()) return false;

        if (is2x2) {
            for (BlockPos tp : TreeShapeUtils.trunk2x2Positions(saplingPos, checkY)) {
                BlockState s = level.getBlockState(tp);
                if (!s.isAir() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.REPLACEABLE)) return false;
            }
            return true;
        }

        BlockState state = level.getBlockState(new BlockPos(saplingPos.getX(), checkY, saplingPos.getZ()));
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE);
    }

    @Override
    public void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        TreeGrowthSession session = TreeGrowthHandler.INSTANCE.findSessionForSapling(level, saplingPos);
        if (session == null) return;

        List<BlockPos> logPositions = session.stageLogPositions(currentStage);
        List<BlockPos> leafPositions = session.stageLeafPositions(currentStage);

        for (BlockPos logPos : logPositions) {
            TreeShapeUtils.tryPlaceLog(level, logPos, logBlock, Direction.Axis.Y);
        }

        for (BlockPos leafPos : leafPositions) {
            BlockState existing = level.getBlockState(leafPos);
            if (existing.isAir() || existing.is(BlockTags.LEAVES) || existing.is(BlockTags.REPLACEABLE)) {
                level.setBlock(leafPos, leavesBlock.defaultBlockState()
                        .setValue(LeavesBlock.DISTANCE, 1)
                        .setValue(LeavesBlock.PERSISTENT, true), 3);
            }
        }

        if (postGrowHook != null) {
            postGrowHook.run(level, saplingPos, currentStage, totalStages, resolvedHeight, random, logBlock);
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
