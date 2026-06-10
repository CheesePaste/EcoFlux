package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
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
        return resolvedHeight + 2;
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        if (currentStage < resolvedHeight) {
            // Stem starts at +1 above mushroom to avoid destroying it
            BlockPos check = saplingPos.above(currentStage + 1);
            if (check.getY() >= level.getMaxBuildHeight()) return false;
            BlockState s = level.getBlockState(check);
            return s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)
                    || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM)
                    || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK)
                    || s.is(Blocks.MUSHROOM_STEM);
        }
        return true;
    }

    @Override
    public void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        if (currentStage < resolvedHeight) {
            placeStem(level, saplingPos, currentStage + 1); // offset +1 to skip mushroom itself
        } else {
            int capStage = currentStage - resolvedHeight;
            placeCap(level, saplingPos, resolvedHeight + 1, capStage); // cap start above stem
        }
    }

    private void placeStem(ServerLevel level, BlockPos saplingPos, int yAbove) {
        BlockPos stemPos = saplingPos.above(yAbove);
        BlockState existing = level.getBlockState(stemPos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM) || existing.is(Blocks.RED_MUSHROOM)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            level.setBlock(stemPos, Blocks.MUSHROOM_STEM.defaultBlockState(), 3);
        }
    }

    /**
     * Matches vanilla HugeRedMushroomFeature: 4-layer dome cap.
     * Bottom 3 layers (y=h-3..h-1): border rings, radius 2, where |x|==2 xor |z|==2.
     * Top layer (y=h): full 3x3 square, radius 1.
     * Sets HugeMushroomBlock face directions matching vanilla.
     *
     * stemHeight is the resolved height PLUS the +1 offset (so cap layers build above the stem).
     */
    private void placeCap(ServerLevel level, BlockPos saplingPos, int stemHeight, int capStage) {
        int capBaseY = stemHeight - 3; // bottom of dome layers
        switch (capStage) {
            case 0 -> {
                for (int dy = capBaseY; dy <= capBaseY + 1; dy++) {
                    placeDomeRing(level, saplingPos, dy, CAP_RADIUS, stemHeight);
                }
            }
            case 1 -> {
                placeDomeRing(level, saplingPos, capBaseY + 2, CAP_RADIUS, stemHeight);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        placeCapBlock(level, saplingPos.offset(dx, stemHeight, dz),
                                stemHeight, stemHeight, dx, dz);
                    }
                }
            }
        }
    }

    private void placeDomeRing(ServerLevel level, BlockPos saplingPos, int y, int r, int stemHeight) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean xEdge = dx == -r || dx == r;
                boolean zEdge = dz == -r || dz == r;
                if (xEdge != zEdge) {
                    placeCapBlock(level, saplingPos.offset(dx, y, dz),
                            y, stemHeight, dx, dz);
                }
            }
        }
    }

    /**
     * Places a red mushroom cap block with vanilla face directions.
     * Matches HugeRedMushroomFeature.makeCap() face logic:
     * UP = y >= stemHeight - 1 (top 2 layers)
     * WEST/EAST/NORTH/SOUTH = facing away from center (dx < 0, dx > 0, dz < 0, dz > 0)
     */
    private void placeCapBlock(ServerLevel level, BlockPos pos, int y, int stemHeight, int dx, int dz) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM) || existing.is(Blocks.RED_MUSHROOM)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            BlockState state = Blocks.RED_MUSHROOM_BLOCK.defaultBlockState()
                    .setValue(HugeMushroomBlock.UP, y >= stemHeight - 1)
                    .setValue(HugeMushroomBlock.WEST, dx < 0)
                    .setValue(HugeMushroomBlock.EAST, dx > 0)
                    .setValue(HugeMushroomBlock.NORTH, dz < 0)
                    .setValue(HugeMushroomBlock.SOUTH, dz > 0);
            level.setBlock(pos, state, 3);
        }
    }
}
