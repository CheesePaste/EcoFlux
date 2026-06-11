package com.s.ecoflux.plant.tree.profiles;

/**
 * Giant brown mushroom growth profile (singleton).
 *
 * <p>Structure: mushroom-shaped with a stem (4-7 blocks) and a brown cap
 * of radius 2, 2400 ticks/stage. Uses custom block types
 * ({@code MUSHROOM_STEM}, {@code BROWN_MUSHROOM_BLOCK}) with
 * {@link net.minecraft.world.level.block.HugeMushroomBlock} face properties.
 * Does NOT use the parametric morphology system; uses legacy
 * {@link #growStage} with {@link #placeStem} and {@link #placeCap}.
 * <p>Role in Ecoflux: defines growth parameters for giant brown mushrooms
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

public final class BrownMushroomGrowthProfile extends AbstractTreeGrowthProfile {
    public static final BrownMushroomGrowthProfile INSTANCE = new BrownMushroomGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("brown_mushroom");
    private static final int CAP_RADIUS = 2;

    private BrownMushroomGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int minTrunkHeight() { return 4; }
    @Override public int maxTrunkHeight() { return 7; }
    @Override public int ticksPerStage() { return 2400; }
    @Override public Block logBlock() { return Blocks.MUSHROOM_STEM; }
    @Override public Block leavesBlock() { return Blocks.BROWN_MUSHROOM_BLOCK; }
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
        BlockPos capCenter = saplingPos.above(stemHeight);
        int r = CAP_RADIUS;

        switch (capStage) {
            case 0 -> {
                tryPlaceCapBlock(level, capCenter, r, 0, 0);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        if (Math.abs(dx) == 1 && Math.abs(dz) == 1) continue;
                        tryPlaceCapBlock(level, capCenter.offset(dx, 0, dz), r, dx, dz);
                    }
                }
            }
            case 1 -> {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) == r && Math.abs(dz) == r) continue;
                        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1
                                && !(Math.abs(dx) == 1 && Math.abs(dz) == 1)) continue;
                        tryPlaceCapBlock(level, capCenter.offset(dx, 0, dz), r, dx, dz);
                    }
                }
            }
        }
    }

    private boolean tryPlaceCapBlock(ServerLevel level, BlockPos pos, int r, int dx, int dz) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM) || existing.is(Blocks.RED_MUSHROOM)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            boolean onXEdge = dx == -r || dx == r;
            boolean onZEdge = dz == -r || dz == r;
            BlockState state = Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState()
                    .setValue(HugeMushroomBlock.WEST, onXEdge && dx == -r || onZEdge && dx == 1 - r)
                    .setValue(HugeMushroomBlock.EAST, onXEdge && dx == r || onZEdge && dx == r - 1)
                    .setValue(HugeMushroomBlock.NORTH, onZEdge && dz == -r || onXEdge && dz == 1 - r)
                    .setValue(HugeMushroomBlock.SOUTH, onZEdge && dz == r || onXEdge && dz == r - 1);
            level.setBlock(pos, state, 3);
            return true;
        }
        return false;
    }
}
