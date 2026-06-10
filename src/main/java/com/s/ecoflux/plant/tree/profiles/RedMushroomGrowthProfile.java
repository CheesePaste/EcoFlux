package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class RedMushroomGrowthProfile implements TreeGrowthProfile {
    public static final RedMushroomGrowthProfile INSTANCE = new RedMushroomGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("red_mushroom");
    private static final int CAP_RADIUS = 2;

    private RedMushroomGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int minTrunkHeight() { return 3; }
    @Override public int maxTrunkHeight() { return 7; }
    @Override public int ticksPerStage() { return 2400; }
    @Override public Block logBlock() { return Blocks.MUSHROOM_STEM; }
    @Override public Block leavesBlock() { return Blocks.RED_MUSHROOM_BLOCK; }
    @Override public boolean is2x2() { return false; }

    @Override
    public int totalStagesForHeight(int resolvedHeight) {
        return resolvedHeight + 2; // stem + 2 cap stages
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        if (currentStage < resolvedHeight) {
            BlockPos check = saplingPos.above(currentStage + 1);
            if (check.getY() >= level.getMaxBuildHeight()) return false;
            BlockState s = level.getBlockState(check);
            return s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)
                    || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK)
                    || s.is(Blocks.MUSHROOM_STEM);
        }
        return true;
    }

    @Override
    public void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        if (currentStage < resolvedHeight) {
            placeStem(level, saplingPos, currentStage + 1);
        } else {
            int capStage = currentStage - resolvedHeight;
            placeCap(level, saplingPos, resolvedHeight, capStage);
        }
    }

    private void placeStem(ServerLevel level, BlockPos saplingPos, int y) {
        BlockPos stemPos = saplingPos.above(y);
        BlockState existing = level.getBlockState(stemPos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            level.setBlock(stemPos, Blocks.MUSHROOM_STEM.defaultBlockState(), 3);
        }
    }

    /**
     * Matches vanilla HugeRedMushroomFeature: 4-layer dome cap.
     * Bottom 3 layers are border-only rings (sides without corners).
     * Top layer is full 5x5.
     */
    private void placeCap(ServerLevel level, BlockPos saplingPos, int stemHeight, int capStage) {
        int r = CAP_RADIUS;

        switch (capStage) {
            case 0 -> {
                // bottom 2 dome layers: y=h-3 and y=h-2, border-only rings
                for (int dy = stemHeight - 3; dy <= stemHeight - 2; dy++) {
                    placeDomeRing(level, saplingPos, dy, r);
                }
            }
            case 1 -> {
                // upper dome ring: y=h-1, border-only
                placeDomeRing(level, saplingPos, stemHeight - 1, r);
                // top full layer: y=h, all positions
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        placeCapBlock(level, saplingPos.offset(dx, stemHeight, dz));
                    }
                }
            }
        }
    }

    /**
     * Places a ring of cap blocks: the 4 sides of the square without corners and without interior.
     * Matches vanilla: isXEdge != isZEdge condition.
     */
    private void placeDomeRing(ServerLevel level, BlockPos saplingPos, int y, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean xEdge = dx == -r || dx == r;
                boolean zEdge = dz == -r || dz == r;
                if (xEdge != zEdge) {
                    placeCapBlock(level, saplingPos.offset(dx, y, dz));
                }
            }
        }
    }

    private void placeCapBlock(ServerLevel level, BlockPos pos) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            level.setBlock(pos, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), 3);
        }
    }
}
