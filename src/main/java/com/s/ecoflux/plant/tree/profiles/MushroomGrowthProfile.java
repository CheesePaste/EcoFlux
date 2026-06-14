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

public record MushroomGrowthProfile(
        ResourceLocation treeType,
        int minTrunkHeight,
        int maxTrunkHeight,
        int ticksPerStage,
        Block capBlock,
        MushroomCapStyle capStyle)
        implements TreeGrowthProfile {

    public enum MushroomCapStyle { FLAT, DOMED }

    private static final int CAP_RADIUS = 2;

    @Override
    public Block logBlock() { return Blocks.MUSHROOM_STEM; }

    @Override
    public Block leavesBlock() { return capBlock; }

    @Override
    public boolean is2x2() { return false; }

    @Override
    public int resolveHeight(RandomSource random) {
        int range = maxTrunkHeight - minTrunkHeight;
        return minTrunkHeight + (range > 0 ? random.nextInt(range + 1) : 0);
    }

    @Override
    public int totalStagesForHeight(int resolvedHeight) {
        return resolvedHeight + 2;
    }

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
            placeMushroomStem(level, saplingPos, currentStage + 1);
        } else {
            int capStage = currentStage - resolvedHeight;
            if (capStyle == MushroomCapStyle.FLAT) {
                placeFlatCap(level, saplingPos, resolvedHeight + 1, capStage);
            } else {
                placeDomCap(level, saplingPos, resolvedHeight + 1, capStage);
            }
        }
    }

    // ── Stem ──────────────────────────────────────────────────────────────

    private static void placeMushroomStem(ServerLevel level, BlockPos saplingPos, int yAbove) {
        BlockPos stemPos = saplingPos.above(yAbove);
        BlockState existing = level.getBlockState(stemPos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM) || existing.is(Blocks.RED_MUSHROOM)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            level.setBlock(stemPos, Blocks.MUSHROOM_STEM.defaultBlockState(), 3);
        }
    }

    // ── Flat cap (brown mushroom) ─────────────────────────────────────────

    private void placeFlatCap(ServerLevel level, BlockPos saplingPos, int stemHeight, int capStage) {
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

    private void tryPlaceCapBlock(ServerLevel level, BlockPos pos, int r, int dx, int dz) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM) || existing.is(Blocks.RED_MUSHROOM)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            boolean onXEdge = dx == -r || dx == r;
            boolean onZEdge = dz == -r || dz == r;
            BlockState state = capBlock.defaultBlockState()
                    .setValue(HugeMushroomBlock.WEST, onXEdge && dx == -r || onZEdge && dx == 1 - r)
                    .setValue(HugeMushroomBlock.EAST, onXEdge && dx == r || onZEdge && dx == r - 1)
                    .setValue(HugeMushroomBlock.NORTH, onZEdge && dz == -r || onXEdge && dz == 1 - r)
                    .setValue(HugeMushroomBlock.SOUTH, onZEdge && dz == r || onXEdge && dz == r - 1);
            level.setBlock(pos, state, 3);
        }
    }

    // ── Dome cap (red mushroom) ───────────────────────────────────────────

    private void placeDomCap(ServerLevel level, BlockPos saplingPos, int stemHeight, int capStage) {
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
                        tryPlaceDomeCapBlock(level, saplingPos.offset(dx, stemHeight, dz),
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
                    tryPlaceDomeCapBlock(level, saplingPos.offset(dx, y, dz),
                            y, stemHeight, dx, dz);
                }
            }
        }
    }

    private void tryPlaceDomeCapBlock(ServerLevel level, BlockPos pos, int y, int stemHeight, int dx, int dz) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM) || existing.is(Blocks.RED_MUSHROOM)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            BlockState state = capBlock.defaultBlockState()
                    .setValue(HugeMushroomBlock.UP, y >= stemHeight - 1)
                    .setValue(HugeMushroomBlock.WEST, dx < 0)
                    .setValue(HugeMushroomBlock.EAST, dx > 0)
                    .setValue(HugeMushroomBlock.NORTH, dz < 0)
                    .setValue(HugeMushroomBlock.SOUTH, dz > 0);
            level.setBlock(pos, state, 3);
        }
    }
}
