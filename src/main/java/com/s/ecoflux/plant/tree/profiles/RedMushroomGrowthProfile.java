package com.s.ecoflux.plant.tree.profiles;

/**
 * Giant red mushroom growth profile (singleton).
 *
 * <p>Structure: mushroom-shaped with a stem (3-7 blocks) and a domed red cap
 * of radius 2, 2400 ticks/stage. The cap uses dome rings
 * ({@link #placeDomeRing}) spread across 3 vertical layers for a rounded top.
 * Uses custom block types ({@code MUSHROOM_STEM}, {@code RED_MUSHROOM_BLOCK})
 * with {@link net.minecraft.world.level.block.HugeMushroomBlock} face properties.
 * Does NOT use the parametric morphology system; uses legacy
 * {@link #growStage} with {@link #placeStem} and {@link #placeCap}.
 * <p>Role in Ecoflux: defines growth parameters for giant red mushrooms
 * in the ecological succession tree lifecycle system.
 */

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class RedMushroomGrowthProfile extends AbstractTreeGrowthProfile {
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
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        if (currentStage < resolvedHeight) {
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
            placeStem(level, saplingPos, currentStage + 1);
        } else {
            int capStage = currentStage - resolvedHeight;
            placeCap(level, saplingPos, resolvedHeight + 1, capStage);
        }
    }

    private void placeStem(ServerLevel level, BlockPos saplingPos, int yAbove) {
        placeMushroomStem(level, saplingPos, yAbove);
    }

    private void placeCap(ServerLevel level, BlockPos saplingPos, int stemHeight, int capStage) {
        int capBaseY = stemHeight - 3;
        switch (capStage) {
            case 0 -> {
                for (int dy = capBaseY; dy <= capBaseY + 1; dy++) {
                    placeDomeRing(level, saplingPos, dy, stemHeight);
                }
            }
            case 1 -> {
                placeDomeRing(level, saplingPos, capBaseY + 2, stemHeight);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        tryPlaceCapBlock(level, saplingPos.offset(dx, stemHeight, dz),
                                stemHeight, stemHeight, dx, dz);
                    }
                }
            }
        }
    }

    private void placeDomeRing(ServerLevel level, BlockPos saplingPos, int y, int stemHeight) {
        for (int dx = -CAP_RADIUS; dx <= CAP_RADIUS; dx++) {
            for (int dz = -CAP_RADIUS; dz <= CAP_RADIUS; dz++) {
                boolean xEdge = dx == -CAP_RADIUS || dx == CAP_RADIUS;
                boolean zEdge = dz == -CAP_RADIUS || dz == CAP_RADIUS;
                if (xEdge != zEdge) {
                    tryPlaceCapBlock(level, saplingPos.offset(dx, y, dz),
                            y, stemHeight, dx, dz);
                }
            }
        }
    }

    private boolean tryPlaceCapBlock(ServerLevel level, BlockPos pos, int y, int stemHeight, int dx, int dz) {
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
            return true;
        }
        return false;
    }
}
