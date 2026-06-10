package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BrownMushroomGrowthProfile implements TreeGrowthProfile {
    public static final BrownMushroomGrowthProfile INSTANCE = new BrownMushroomGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("brown_mushroom");

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
    public int totalStagesForHeight(int resolvedHeight) {
        return resolvedHeight + 3; // stem + 3 cap stages
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
            placeStemStage(level, saplingPos, currentStage);
        } else {
            int capStage = currentStage - resolvedHeight;
            placeCapStage(level, saplingPos, resolvedHeight, capStage, random);
        }
    }

    private void placeStemStage(ServerLevel level, BlockPos saplingPos, int stage) {
        BlockPos stemPos = saplingPos.above(stage + 1);
        BlockState existing = level.getBlockState(stemPos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            level.setBlock(stemPos, Blocks.MUSHROOM_STEM.defaultBlockState(), 3);
        }
    }

    private void placeCapStage(ServerLevel level, BlockPos saplingPos, int stemHeight,
                               int capStage, RandomSource random) {
        BlockPos capCenter = saplingPos.above(stemHeight);

        switch (capStage) {
            case 0 -> {
                // center + ring radius 1
                placeCapBlock(level, capCenter);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        placeCapBlock(level, capCenter.offset(dx, 0, dz));
                    }
                }
                // top center
                placeCapBlock(level, capCenter.above());
            }
            case 1 -> {
                // extend to radius 2
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
                        if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && random.nextFloat() > 0.6) continue;
                        placeCapBlock(level, capCenter.offset(dx, 0, dz));
                    }
                }
                // ring radius 1 on top layer
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        placeCapBlock(level, capCenter.offset(dx, 1, dz));
                    }
                }
            }
            case 2 -> {
                // extend to radius 3, outer ring
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) continue;
                        if (Math.abs(dx) == 3 && Math.abs(dz) == 3 && random.nextFloat() > 0.5) continue;
                        placeCapBlock(level, capCenter.offset(dx, -1, dz));
                    }
                }
                // a few hanging blocks
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
                        if (random.nextFloat() < 0.3) {
                            placeCapBlock(level, capCenter.offset(dx, -1, dz));
                        }
                    }
                }
            }
        }
    }

    private void placeCapBlock(ServerLevel level, BlockPos pos) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            level.setBlock(pos, Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState(), 3);
        }
    }
}
